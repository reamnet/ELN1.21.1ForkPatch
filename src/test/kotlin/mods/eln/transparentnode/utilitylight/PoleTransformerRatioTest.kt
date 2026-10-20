package mods.eln.transparentnode.utilitylight

import mods.eln.Eln
import mods.eln.gridnode.electricalpole.ElectricalPoleDescriptor
import mods.eln.gridnode.electricalpole.ElectricalPoleElement
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.utility.DistributionKind
import mods.eln.utility.DistributionPoleDescriptor
import mods.eln.utility.DistributionPoleElement
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import java.io.*
import kotlin.test.*

class PoleTransformerRatioTest {
    private fun voltagePacket(volts: Int)=DataInputStream(ByteArrayInputStream(ByteArrayOutputStream().also {
        DataOutputStream(it).writeInt(volts)
    }.toByteArray()))

    @Test fun everyTransformerPoleTracksPrimaryVoltageAtBothNominalOutputsUnderLoadAndAfterReload() {
        val descriptors=Eln.transparentNodeItem.subItemList.values
        for (descriptor in descriptors.filterIsInstance<DistributionPoleDescriptor>().filter { it.kind.transformer }) {
            for (target in listOf(120,240)) {
                val pole=DistributionPoleElement(TransparentNode(),descriptor)
                pole.inventory.setItem(0,Eln.findItemStack("Mains Step-down Unit",1));pole.inventoryChange(pole.inventory)
                pole.unit!!.packet(80,voltagePacket(target))
                val settings=pole.getItemStackNBT()
                pole.unit.read(CompoundTag());pole.readItemStackNBT(settings)
                assertEquals(target,pole.unit.outputVoltage)
                val root=RootSystem(.01,50)
                pole.electricalLoadList.forEach(root::addState);pole.electricalComponentList.forEach(root::addComponent)
                val source=VoltageSource("nominal grid",pole.lines[0],null)
                root.addComponent(source);root.addProcess(pole.unit.controller)
                val loads=pole.services.mapIndexed { i,b -> Resistor(b,null).apply { resistance=if(i==2)480.0 else 240.0 }.also(root::addComponent) }
                source.voltage=MainsStepDown.NOMINAL_POLE_VOLTS
                repeat(12) { root.step() }
                val baseline=pole.services.map { it.voltage }
                baseline.forEachIndexed { i,v -> assertEquals(if(i==2)240.0 else 120.0,v,.1,descriptor.name) }
                for (fraction in listOf(.95,1.05,.75,1.0,0.0,1.0)) {
                    source.voltage=MainsStepDown.NOMINAL_POLE_VOLTS*fraction
                    repeat(12) { root.step() }
                    pole.services.forEachIndexed { i,b -> assertEquals(baseline[i]*fraction,b.voltage,1e-5,"${descriptor.name}, target=$target, input=$fraction") }
                    assertNotEquals("NON_CONVERGENT",pole.unit.controller.status)
                    assertTrue(source.power+1e-6>=loads.sumOf { it.power })
                }
                // Additional demand produces physical winding sag instead of a higher regulated drive.
                val before=pole.unit.secondary.voltage
                root.addComponent(Resistor(pole.unit.secondary,null).apply { resistance=target.toDouble()*target/2000 })
                repeat(12) { root.step() }
                assertTrue(pole.unit.secondary.voltage<before)
                assertTrue(pole.lines[0].voltage>15_199)
                assertFalse(pole.unit.outputOverloaded,"Ordinary voltage sag is not a converter current-limit fault")
            }
        }
    }

    @Test fun allExistingStreetlightStylesUseTheSameFixedPrimaryNominal() {
        for (descriptor in Eln.transparentNodeItem.subItemList.values.filterIsInstance<UtilityStreetLightDescriptor>()) {
            val pole=UtilityStreetLightElement(TransparentNode(),descriptor)
            pole.inventory.setItem(0,Eln.findItemStack("Mains Step-down Unit",1));pole.inventoryChange(pole.inventory);pole.fuseContact.closed=pole.fuse.closed
            val root=RootSystem(.01,50)
            pole.electricalLoadList.forEach(root::addState);pole.electricalComponentList.forEach(root::addComponent)
            val source=VoltageSource("street feed",pole.highBus,null);root.addComponent(source);root.addProcess(pole.unit.controller)
            root.addComponent(Resistor(pole.unit.secondary,null).apply { resistance=240.0 })
            for (fraction in listOf(1.0,.95,1.05,0.0,1.0)) {
                source.voltage=15_200*fraction;repeat(12) { root.step() }
                assertEquals(120*fraction,pole.unit.secondary.voltage,.1,descriptor.name)
                assertNotEquals("NON_CONVERGENT",pole.unit.controller.status)
            }
        }
    }

    @Test fun legacyConverterPoleUsesSelectableNominalOutputAndKeepsItWhenMoved() {
        val descriptor=Eln.transparentNodeItem.subItemList.values.filterIsInstance<ElectricalPoleDescriptor>().single { it.name=="Utility Pole w/DC-DC Converter" }
        for (target in listOf(120,240,3200)) {
            val pole=ElectricalPoleElement(TransparentNode(),descriptor)
            pole.readItemStackNBT(CompoundTag().apply { putInt("legacyOutputVoltage",target) })
            val copy=ElectricalPoleElement(TransparentNode(),descriptor);copy.readItemStackNBT(pole.getItemStackNBT())
            assertEquals(target,copy.outputVoltage)
            val root=RootSystem(.01,50)
            copy.electricalLoadList.forEach(root::addState);copy.electricalComponentList.forEach(root::addComponent)
            val source=VoltageSource("legacy grid",copy.electricalLoad,null);root.addComponent(source)
            root.addProcess(copy.trafo!!.interSystemProcess)
            root.addComponent(Resistor(copy.trafo.secondaryLoad,null).apply { resistance=target.toDouble()*target/100 })
            for (fraction in listOf(1.0,.95,1.05,0.0,1.0)) {
                source.voltage=15_200*fraction;repeat(12) { root.step() }
                assertEquals(target*fraction,copy.trafo.secondaryLoad.voltage,.1)
            }
        }
    }
    private fun transformer(): DistributionPoleElement {
        val d=Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>().single { it.kind==DistributionKind.TRANSFORMER_SINGLE }
        return DistributionPoleElement(TransparentNode(),d).also {
            it.inventory.setItem(0,Eln.findItemStack("Mains Step-down Unit",1));it.inventoryChange(it.inventory)
            it.readItemStackNBT(CompoundTag().apply { putInt("mainsOutputVoltage",240) })
        }
    }
    @Test fun aTransformerCanReflectSecondaryPowerBackToAPrimaryLoadWithoutCreatingEnergy() {
        val pole=transformer();val root=RootSystem(.01,50)
        pole.electricalLoadList.forEach(root::addState);pole.electricalComponentList.forEach(root::addComponent)
        root.addProcess(pole.unit!!.controller)
        val source=VoltageSource("secondary supply",pole.unit.secondary,null).apply { voltage=240.0 };root.addComponent(source)
        val load=Resistor(pole.lines[0],null).apply { resistance=15_200.0*15_200/1000 };root.addComponent(load)
        repeat(20) { root.step() }
        assertEquals(15_200.0,pole.lines[0].voltage,5.0)
        assertTrue(load.power>999);assertTrue(source.power>=load.power)
        assertTrue(pole.unit.output.power<0,"Reverse power must flow through the winding")
        assertNotEquals("NON_CONVERGENT",pole.unit.controller.status)
    }
    @Test fun twoPoleSecondariesShareALoadThroughTheirRealFeedersAndOneCanBeOpened() {
        val root=RootSystem(.01,50);val poles=List(2) { transformer() }
        val grid=mods.eln.sim.nbt.NbtElectricalLoad("grid");root.addState(grid)
        val source=VoltageSource("shared grid",grid,null).apply { voltage=15_200.0 };root.addComponent(source)
        poles.forEachIndexed { i,pole ->
            pole.electricalLoadList.forEach(root::addState);pole.electricalComponentList.forEach(root::addComponent)
            root.addProcess(pole.unit!!.controller)
            root.addComponent(Resistor(grid,pole.lines[0]).apply { resistance=10.0*(i+1) })
        }
        root.addComponent(Resistor(poles[0].unit!!.secondary,poles[1].unit!!.secondary).apply { resistance=.02 })
        val load=Resistor(poles[0].unit!!.secondary,null).apply { resistance=240.0*240/5000 };root.addComponent(load)
        for(fraction in listOf(1.0,.95,1.05,1.0)) {
            source.voltage=15_200*fraction
            repeat(20) { root.step() }
            poles.forEach { assertNotEquals("NON_CONVERGENT",it.unit!!.controller.status);assertEquals(240*fraction,it.unit.secondary.voltage,1.0) }
            assertTrue(source.power>=load.power)
        }
        poles[0].unit!!.packet(81,DataInputStream(ByteArrayInputStream(byteArrayOf(0))))
        repeat(20) { root.step() }
        assertFalse(poles[0].unit!!.output.enabled)
        assertEquals(240.0,poles[1].unit!!.secondary.voltage,1.0)
        assertTrue(load.power>4900);assertTrue(source.power>=load.power)
    }

    @Test fun gridVoltageChangesReuseTheFixedTransformerMatrices() {
        val pole=transformer();val root=RootSystem(.01,50)
        pole.electricalLoadList.forEach(root::addState);pole.electricalComponentList.forEach(root::addComponent)
        root.addProcess(pole.unit!!.controller)
        val source=VoltageSource("varying primary",pole.lines[0],null).apply { voltage=15_200.0 };root.addComponent(source)
        root.addComponent(Resistor(pole.unit.secondary,null).apply { resistance=240.0*240/1000 })
        repeat(10) { root.step() }
        val before=root.systems.sumOf { it.fullFactorizations }
        repeat(50) { i ->
            source.voltage=15_200*(1+.05*kotlin.math.sin(i.toDouble()))
            root.step()
            assertEquals(source.voltage*240/15_200,pole.unit.secondary.voltage,.1)
            assertNotEquals("NON_CONVERGENT",pole.unit.controller.status)
        }
        assertEquals(before,root.systems.sumOf { it.fullFactorizations },"A voltage fluctuation must not rebuild the transformer matrices")
    }

}
