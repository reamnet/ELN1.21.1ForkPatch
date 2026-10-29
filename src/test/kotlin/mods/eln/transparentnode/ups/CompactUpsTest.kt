package mods.eln.transparentnode.ups

import mods.eln.Eln
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.state.VoltageState
import mods.eln.transparentnode.cord.*
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import java.io.*
import kotlin.test.*

class CompactUpsTest {
    private fun element()=CompactUpsElement(TransparentNode(),Eln.transparentNodeItem.subItemList.values.filterIsInstance<UpsDescriptor>().single { it.compact })
    private fun fill(e: CompactUpsElement,count: Int=4,fraction: Double=.5) {
        repeat(count) { i -> val stack=Eln.findItemStack("UPS Industrial Battery Module",1)
            upsBattery(stack)!!.let { it.setEnergy(stack,it.getEnergyMax(stack)*fraction) };e.inventory.setItem(i,stack) }
    }
    private fun packet(f: DataOutputStream.()->Unit)=DataInputStream(ByteArrayInputStream(ByteArrayOutputStream().also { DataOutputStream(it).use(f) }.toByteArray()))
    private class Circuit(volts: Double,loadWatts: Double=500.0,grid: Double=240.0,fraction: Double=.5) {
        val e=CompactUpsTest().element();val u=e.unit;val root=RootSystem(.05,1);val feed=VoltageState()
        val source=SwitchableVoltageSource("compactGrid").apply { connectTo(feed,null);voltage=grid;enabled=true }
        val wire=Resistor(feed,u.primary).apply { resistance=.03 }
        val load=Resistor(u.secondary,null).apply { resistance=if(loadWatts>0)volts*volts/loadWatts else 1e19 }
        init {
            CompactUpsTest().fill(e,fraction=fraction);u.settings(CompoundTag().apply { putDouble("upsOutputVoltage",volts) })
            root.addState(feed);e.electricalLoadList.forEach(root::addState);e.electricalComponentList.forEach(root::addComponent)
            listOf(source,wire,load).forEach(root::addComponent);u.controllers.forEach(root::addProcess)
        }
        fun tick() { root.step();u.account(.05) }
        fun settle() { repeat(60) { tick() } }
    }
    @Test fun fourIndependentCellsUpgradeCapacityAndRetainChargeOnRemoval() {
        val e=element();assertEquals(4,e.inventory.containerSize)
        fill(e,1);assertEquals(1,e.bank.activeCount);val one=e.bank.capacity
        fill(e);assertEquals(4,e.bank.activeCount);assertEquals(one*4,e.bank.capacity)
        val before=e.bank.energy;e.bank.transfer(-100.0,1.0)
        val removed=e.inventory.removeItem(0,1)
        assertEquals(before-100,e.bank.energy+upsBattery(removed)!!.getEnergy(removed),1e-5)
    }
    @Test fun chargesWithin3000WAnd20AAtBothInputVoltages() {
        for(grid in listOf(120.0,240.0)) {
            val c=Circuit(120.0,grid=grid,loadWatts=0.0);c.settle()
            assertTrue(c.u.inputWatts>100,"No charge at $grid V: ${c.u.status}")
            assertTrue(c.u.inputWatts<=3000.001,"Charging exceeds 3000 W: ${c.u.inputWatts}")
            assertTrue(-c.u.chargerInput.current<=20.00001)
            assertTrue(c.e.bank.energy>c.e.bank.capacity*.5)
        }
    }
    @Test fun supplies120And240VThroughOutageAndLimitsAggregateOutput() {
        for(v in listOf(120.0,240.0)) {
            val c=Circuit(v);c.settle();assertEquals(v,c.u.secondary.voltage,.2)
            c.source.enabled=false;val before=c.e.bank.energy
            repeat(30) { c.tick();assertEquals(v,c.u.secondary.voltage,.2) }
            assertTrue(c.e.bank.energy<before-700);assertEquals("BACKUP",c.u.status)
            c.load.resistance=v*v/12000;c.settle();assertTrue(c.u.outputWatts<=3000.01)
        }
    }
    @Test fun fourSocketsReserveIndependentlyAndRejectFifthOrWrongVoltage() {
        val e=element();val loads=List(5) { element().cord }
        repeat(4) { i -> assertEquals(i,e.freeSocket());assertTrue(e.reserve(loads[i],i)) }
        assertNull(e.freeSocket());assertFalse(e.reserve(loads[4],0));assertFalse(e.reserve(loads[4],4))
        assertFalse(e.acceptsCord(CordRating.V480));assertTrue(e.acceptsCord(CordRating.V120));assertTrue(e.acceptsCord(CordRating.V240))
        e.unit.packet(80,packet { writeDouble(240.0) });assertEquals(120.0,e.unit.outputVoltage)
        e.release(loads[0],true);assertEquals(0,e.freeSocket())
        e.unit.packet(80,packet { writeDouble(850.0) });assertEquals(120.0,e.unit.outputVoltage)
    }
    @Test fun sourceCannotFeedItsOwnInputAndSocketReservationsSurviveSave() {
        val e=element();assertTrue(cordWouldLoop(e,e))
        val other=element().cord;assertTrue(e.reserve(other,2))
        val n=CompoundTag();e.writeToNBT(n);val restored=element();restored.readFromNBT(n)
        assertEquals(e.id,restored.id);assertTrue(restored.isOccupied(2));assertTrue(restored.canClaim(other,2))
        assertFalse(restored.canClaim(element().cord,2))
    }
    @Test fun mismatchedBypassDoesNotPut240VOn120VOutputs() {
        val c=Circuit(120.0,grid=240.0);c.settle();c.u.packet(82,packet { writeBoolean(true) });c.settle()
        assertFalse(c.u.bypass);assertEquals(120.0,c.u.secondary.voltage,.2)
    }
    @Test fun portableSnapshotRestoresCellsOutputSettingAndInputCord() {
        val e=element();fill(e)
        e.unit.settings(CompoundTag().apply { putDouble("upsOutputVoltage",240.0) })
        val saved=CompoundTag().also(e::writeToNBT)
        val a=CordAnchor(0,net.minecraft.core.BlockPos(1,64,1),java.util.UUID.randomUUID(),mods.eln.misc.Direction.ZP,CordRating.V240)
        saved.put("applianceCord",a.write())
        val copy=element();copy.readItemStackNBT(CompoundTag().apply { put("carriedAppliance",saved) })
        assertEquals(4,copy.bank.activeCount);assertEquals(e.bank.energy,copy.bank.energy,.001)
        assertEquals(240.0,copy.unit.outputVoltage);assertTrue(copy.cord.plugged);assertEquals(a,copy.cord.anchor)
        assertEquals(0,copy.freeSocket())
    }

}
