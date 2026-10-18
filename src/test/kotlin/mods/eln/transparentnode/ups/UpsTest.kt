package mods.eln.transparentnode.ups

import mods.eln.Eln
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.state.VoltageState
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import org.junit.Test
import java.io.*
import kotlin.test.*

class UpsTest {
    private fun element()=UpsElement(TransparentNode(),Eln.transparentNodeItem.subItemList.values.filterIsInstance<UpsDescriptor>().single())
    private fun fill(e: UpsElement,count: Int=24,fraction: Double=.5,name: String="Portable Battery Pack") {
        repeat(count) { i ->
            val stack=Eln.findItemStack(name,1);val type=upsBattery(stack)!!
            type.setEnergy(stack,type.getEnergyMax(stack)*fraction);e.inventory.setItem(i,stack)
        }
    }
    private fun packet(write: DataOutputStream.()->Unit)=DataInputStream(ByteArrayInputStream(ByteArrayOutputStream().also { b -> DataOutputStream(b).use(write) }.toByteArray()))
    private class Circuit(target: Double=120.0, watts: Double=500.0, cells: Int=24, fraction: Double=.5, grid: Double=3200.0) {
        val e=UpsTest().element();val u=e.unit;val root=RootSystem(.05,1)
        val feed=VoltageState()
        val supply=SwitchableVoltageSource("grid").apply { connectTo(feed,null);voltage=grid;enabled=true }
        val wire=Resistor(feed,u.primary).apply { resistance=.05 }
        val load=Resistor(u.secondary,null).apply { resistance=if(watts>0)target*target/watts else 1e19 }
        init {
            UpsTest().fill(e,cells,fraction)
            u.settings(CompoundTag().apply { putDouble("upsOutputVoltage",target) })
            root.addState(feed);e.electricalLoadList.forEach(root::addState)
            e.electricalComponentList.forEach(root::addComponent)
            listOf(supply,wire,load).forEach(root::addComponent);u.controllers.forEach(root::addProcess)
        }
        fun tick() {
            val before=e.bank.energy
            root.step();u.account(.05)
            val available=supply.power*.05+before-e.bank.energy
            assertTrue(available+1e-5>=load.power*.05,"Created energy: available=$available, load=${load.power*.05}, status=${u.status}")
        }
        fun settle() { repeat(15) { tick() } }
    }
    @Test fun rackCapacityUsesActualBatteryTypesAndRequiresCompleteSets() {
        val e=element();fill(e,23)
        assertEquals(23,e.bank.installed);assertEquals(0,e.bank.activeCount);assertEquals(0.0,e.bank.capacity)
        val last=Eln.findItemStack("Portable Battery Pack",1);e.inventory.setItem(23,last)
        assertEquals(24,e.bank.activeCount);assertEquals(24*160000.0,e.bank.capacity)
        fill(e,48,fraction=.5,name="Portable Battery")
        assertEquals(48,e.bank.activeCount);assertEquals(48*40000.0,e.bank.capacity)
        assertEquals(48*20000.0,e.bank.energy)
        assertNull(upsBattery(Eln.findItemStack("Portable Condensator",1)))
        assertNull(upsBattery(Eln.findItemStack("Iron Plate",1)))
    }
    @Test fun eachRemovedBatteryCarriesItsExactShareAndPartialRacksRetainEnergy() {
        val e=element();fill(e)
        val start=e.bank.energy
        assertEquals(2400.0,e.bank.transfer(2400.0,1.0))
        val removed=e.inventory.removeItem(0,1)
        assertEquals(80100.0,upsBattery(removed)!!.getEnergy(removed),1e-6)
        assertEquals(0,e.bank.activeCount)
        e.inventory.setItem(0,removed)
        assertEquals(start+2400,e.bank.energy,1e-6)
        assertEquals(start+2400,e.bank.energy,1e-6) // A refresh never adds charge a second time.
    }
    @Test fun mixedChargeLevelsRespectIndividualChargeAndDischargePower() {
        val e=element();fill(e,24,0.0)
        val stack=e.inventory.getItem(0);upsBattery(stack)!!.setEnergy(stack,10000.0);e.bank.invalidate()
        assertEquals(1000.0,e.bank.availableDischargeWatts(.05),1e-6)
        assertEquals(-50.0,e.bank.transfer(-10000.0,.05),1e-6)
        assertEquals(9950.0,e.bank.energy,1e-6)
    }
    @Test fun everyRequestedVoltageRegulatesFromOneTo3200Volts() {
        for(target in listOf(1.0,12.0,120.0,240.0,850.0,3200.0)) {
            val c=Circuit(target,1.0);c.settle()
            assertTrue(c.u.inverterOutput.enabled,"$target V: ${c.u.inverter.status}")
            assertEquals(target,c.u.secondary.voltage,maxOf(.005,target*.002))
        }
    }
    @Test fun inputLossAndRestorationDoNotInterruptTheOutput() {
        val c=Circuit(watts=1000.0);c.settle()
        c.supply.enabled=false
        val start=c.e.bank.energy
        repeat(30) { c.tick();assertEquals(120.0,c.u.secondary.voltage,.05) }
        assertEquals("BACKUP",c.u.status);assertTrue(c.e.bank.energy<start-1500)
        c.supply.enabled=true
        repeat(30) { c.tick();assertEquals(120.0,c.u.secondary.voltage,.05) }
        assertTrue(c.u.inputWatts>c.u.outputWatts)
    }
    @Test fun anEmptyBankChargesAndStartsAutomatically() {
        val c=Circuit(watts=500.0,fraction=0.0)
        c.settle()
        assertTrue(c.e.bank.energy>0);assertTrue(c.load.power>499.0)
        assertTrue(c.u.inputWatts>c.u.outputWatts)
    }
    @Test fun overloadAndEmptyBatteryNeverCreatePowerOrBackfeedInput() {
        val c=Circuit(watts=50000.0);c.settle()
        assertTrue(c.u.outputWatts<=UpsPowerUnit.MAX_WATTS*1.000001)
        c.supply.enabled=false;repeat(20) { c.tick();assertTrue(c.u.chargerInput.power==0.0) }
        val empty=Circuit(fraction=1e-8);empty.supply.enabled=false
        repeat(30) { empty.tick() }
        assertTrue(empty.e.bank.energy<.01)
        assertTrue(empty.load.power<.01)
    }
    @Test fun fullBatteryAndIdleOutputDoNotOverchargeOrClampTheGrid() {
        val c=Circuit(watts=0.0,fraction=1.0);c.settle()
        assertEquals(c.e.bank.capacity,c.e.bank.energy,1e-6)
        assertEquals(3200.0,c.u.primary.voltage,1e-6)
        assertFalse(c.u.chargerInput.enabled)
        val before=c.root.systems.sumOf { it.fullFactorizations+it.updatedFactorizations }
        repeat(20) { c.tick() }
        assertEquals(before,c.root.systems.sumOf { it.fullFactorizations+it.updatedFactorizations })
    }
    @Test fun invalidAndTruncatedVoltagePacketsLeaveSettingsIntact() {
        val e=element()
        assertTrue(e.unit.packet(80,packet { writeDouble(850.0) }))
        for(v in listOf(Double.NaN,Double.POSITIVE_INFINITY,-1.0,0.0,3201.0)) e.unit.packet(80,packet { writeDouble(v) })
        e.unit.packet(80,packet { writeInt(42) })
        assertEquals(850.0,e.unit.outputVoltage)
        e.unit.packet(81,packet { writeBoolean(false) });assertFalse(e.unit.enabled)
    }
    @Test fun aSecondRackUpgradesUsablePowerAsWellAsStoredEnergy() {
        val powers=listOf(24,48).map { count ->
            val c=Circuit(240.0,15000.0,cells=count)
            fill(c.e,count,.5,"Portable Battery");c.settle()
            c.supply.enabled=false;c.settle()
            assertTrue(c.u.outputWatts<=minOf(10000.0,count*250.0*.95)+1e-5)
            c.u.outputWatts
        }
        assertTrue(powers[0]>5500.0);assertTrue(powers[1]>9800.0)
    }
    @Test fun aWeakInputUsesBatteryAssistWithoutInterruptingTheLoad() {
        val c=Circuit(watts=5000.0,grid=24.0)
        val before=c.e.bank.energy;c.settle()
        assertTrue(c.load.power>4900)
        assertTrue(c.u.inputWatts in 1.0..2400.01)
        assertTrue(c.e.bank.energy<before)
        assertEquals("ASSIST",c.u.status)
    }
    @Test fun turningTheOutputOffStillAllowsChargingAndInputOvervoltageUsesBackup() {
        val c=Circuit();c.settle()
        c.u.packet(81,packet { writeBoolean(false) });val before=c.e.bank.energy;c.settle()
        assertTrue(c.load.power<1e-7);assertTrue(c.e.bank.energy>before)
        c.u.packet(81,packet { writeBoolean(true) });c.supply.voltage=60000.0;c.settle()
        assertTrue(c.load.power>499);assertTrue(c.u.inputWatts<1e-7)
        assertEquals("BACKUP",c.u.status)
    }
    @Test fun saveAndItemRoundTripsPreserveVoltageAndBatteryChargeWithoutDuplicatingCells() {
        val e=element();fill(e,48)
        e.bank.transfer(-400.0,.1)
        e.unit.settings(CompoundTag().apply { putDouble("upsOutputVoltage",850.0) })
        val saved=CompoundTag().also(e::writeToNBT)
        val restored=element();restored.readFromNBT(saved)
        assertEquals(48,restored.bank.activeCount)
        assertEquals(e.bank.energy,restored.bank.energy,1e-6)
        assertEquals(850.0,restored.unit.outputVoltage)
        val item=e.getItemStackNBT();assertFalse(item.contains("inv"))
        val placed=element();placed.readItemStackNBT(item)
        assertEquals(0,placed.bank.installed);assertEquals(850.0,placed.unit.outputVoltage)
    }
}
