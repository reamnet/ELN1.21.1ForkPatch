package mods.eln.transparentnode.ups

import mods.eln.Eln
import mods.eln.devtest.StandbyTestFluids
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.transparentnode.standby.StandbyGeneratorElement
import mods.eln.transparentnode.standby.StandbyMode
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import org.junit.Test
import kotlin.test.*

class UpsStandbyTest {
    private class Circuit(fraction: Double,nominal: Double=240.0,val target: Double=120.0,cells: Int=24) {
        val ups=UpsElement(TransparentNode(),Eln.transparentNodeItem.subItemList.values.filterIsInstance<UpsDescriptor>().single { !it.wallMounted && !it.compact })
        val gen=StandbyGeneratorElement(TransparentNode(),Eln.transparentNodeItem.subItemList[7+(124 shl 6)]!!)
        val root=RootSystem(.05,1)
        val load=Resistor(ups.unit.secondary,null).apply { resistance=target*target/1000.0 }
        init {
            repeat(cells) { i ->
                val stack=Eln.findItemStack("Portable Battery Pack",1);val type=upsBattery(stack)!!
                type.setEnergy(stack,type.getEnergyMax(stack)*fraction);ups.inventory.setItem(i,stack)
            }
            ups.unit.settings(CompoundTag().apply { putDouble("upsOutputVoltage",target) })
            gen.unit.setVoltage(nominal)
            gen.unit.mode=StandbyMode.MANUAL
            gen.unit.fuel.tank.fill(FluidStack(StandbyTestFluids.fluid(0),20_000),FluidAction.EXECUTE)
            for(e in listOf(ups,gen)) { e.electricalLoadList.forEach(root::addState);e.electricalComponentList.forEach(root::addComponent) }
            root.addComponent(load);root.addComponent(Resistor(gen.unit.output,ups.unit.primary).apply { resistance=.05 })
            ups.unit.controllers.forEach(root::addProcess);gen.unit.attach(root)
        }
        fun tick() {
            val before=ups.bank.energy
            root.step()
            val generated=gen.unit.source.power
            ups.unit.account(.05);gen.unit.tick(.05)
            assertEquals("",gen.unit.fault,"generator ${gen.unit.status}, UPS ${ups.unit.status}, charger ${ups.unit.charger.status}, source ${gen.unit.source.power} W")
            assertTrue(generated>=-1e-6,"UPS feeds energy into the generator")
            assertTrue(generated<=10_000.011)
            assertTrue(generated*.05+before-ups.bank.energy+.001>=load.power*.05,"Created energy")
            if(gen.unit.running)assertNotEquals("NON_CONVERGENT",ups.unit.charger.status)
        }
    }

    @Test fun partiallyChargedUpsStartsAndChargesFromStandaloneGeneratorWithoutTripping() {
        for(nominal in listOf(120.0,240.0))for(target in listOf(120.0,240.0))for(cells in listOf(24,48)) {
            val c=Circuit(.5,nominal,target,cells)
            repeat(300) { c.tick() }
            assertTrue(c.gen.unit.running)
            assertTrue(c.load.power>990)
            assertTrue(c.ups.unit.inputWatts>c.ups.unit.outputWatts)
        }
    }

    @Test fun fullBankAndLoadChangesNeverSendReversePowerIntoTheGenerator() {
        for(nominal in listOf(120.0,240.0)) {
            val c=Circuit(1.0,nominal)
            repeat(200) { c.tick() }
            for(watts in listOf(0.0,50.0,5000.0,1000.0,0.0)) {
                c.load.resistance=if(watts==0.0)1e19 else c.target*c.target/watts
                repeat(80) { c.tick() }
                assertEquals(watts,c.load.power,maxOf(.1,watts*.005))
            }
        }
    }

    @Test fun anEmptyBankRecoversAndAHeavilyLoadedUpsRidesThroughGeneratorStopAndRestart() {
        val empty=Circuit(0.0,120.0,240.0)
        repeat(400) { empty.tick() }
        assertTrue(empty.ups.bank.energy>0);assertTrue(empty.load.power>990)
        for(nominal in listOf(120.0,240.0)) {
            val c=Circuit(.5,nominal,240.0)
            c.load.resistance=240.0*240/7000.0
            repeat(300) { c.tick() }
            c.gen.unit.mode=StandbyMode.OFF
            repeat(40) { c.tick();assertEquals(240.0,c.ups.unit.secondary.voltage,.1) }
            c.gen.unit.mode=StandbyMode.MANUAL
            repeat(200) { c.tick();assertEquals(240.0,c.ups.unit.secondary.voltage,.1) }
            assertTrue(c.ups.unit.inputWatts>c.ups.unit.outputWatts)
        }
    }
}
