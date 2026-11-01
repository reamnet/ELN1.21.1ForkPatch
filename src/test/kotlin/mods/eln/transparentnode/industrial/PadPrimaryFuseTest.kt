package mods.eln.transparentnode.industrial

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.utility.PoleHardware
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import kotlin.test.*

class PadPrimaryFuseTest {
    private class Circuit(core: Int=2,fuse: Int=10) {
        val d=IndustrialElement(TransparentNode().apply { coordinate=Coordinate(0,80,0,0) },Eln.transparentNodeItem.subItemList[IndustrialKind.PAD.ordinal+(126 shl 6)]!!)
        val root=RootSystem(.05,1)
        val source=VoltageSource("12.47 kV primary",d.input,null).apply { voltage=12470.0 }
        val load=Resistor(d.outputs[0],null).apply { resistance=240.0/145.0 }
        init {
            d.inventory.setItem(0,Eln.findItemStack(PoleHardware.transformerNames[core],1))
            d.inventory.setItem(1,Eln.findItemStack(PoleHardware.fuseNames[fuse],1));d.inventoryChange(d.inventory)
            d.electricalLoadList.forEach(root::addState);d.electricalComponentList.forEach(root::addComponent)
            root.addComponent(source);root.addComponent(load)
        }
        fun run(n:Int)=repeat(n) { d.prepare(true);root.step();d.account(.05);d.tick(.05) }
    }
    @Test fun a145Amp240VoltLoadDoesNotBlowA200AmpPrimaryLink() {
        val c=Circuit();c.run(400)
        assertTrue(c.load.current in 140.0..146.0)
        assertTrue(c.d.tap.current in 2.7..2.9,"Primary ${c.d.tap.current} A")
        assertFalse(c.d.protection.tripped);assertFalse(PoleHardware.fuse(c.d.inventory.getItem(1))!!.spent)
        assertEquals(c.source.power,c.load.power+c.d.winding.power,.001)
    }
    @Test fun anUndersizedCoreTripsItsOwnGuardWithoutConsumingTheHealthyBayonetFuse() {
        val c=Circuit(core=1);c.run(45)
        assertTrue(c.d.protection.tripped);assertEquals(7,c.d.protection.reason)
        assertFalse(PoleHardware.fuse(c.d.inventory.getItem(1))!!.spent);assertFalse(c.d.tap.enabled)
        val saved=CompoundTag().also(c.d::writeToNBT);val restored=Circuit().d;restored.readFromNBT(saved)
        assertEquals(7,restored.protection.reason);assertFalse(PoleHardware.fuse(restored.inventory.getItem(1))!!.spent)
        c.load.resistance=24.0;c.d.resetTrip();c.run(60);assertFalse(c.d.protection.tripped);assertTrue(c.d.tap.enabled)
    }
    @Test fun actualPrimaryOvercurrentStillBlowsTheLinkAndRequiresReplacement() {
        val c=Circuit(fuse=0);c.run(38);assertFalse(c.d.protection.tripped)
        c.run(5);assertEquals(2,c.d.protection.reason);assertTrue(PoleHardware.fuse(c.d.inventory.getItem(1))!!.spent)
        c.d.resetTrip();c.run(5);assertFalse(c.d.tap.enabled)
    }
    @Test fun transformerOverloadTimeDoesNotCountAsPrimaryFuseOvercurrentTime() {
        val p=Protection()
        repeat(35) { p.stepPad(.05,12470.0,3.0,12470.0,200.0,25000.0) }
        assertFalse(p.tripped)
        p.stepPad(.05,12470.0,201.0,12470.0,200.0,25000.0);assertFalse(p.tripped)
        assertEquals(.05,p.elapsed,1e-10)
        repeat(39) { p.stepPad(.05,12470.0,201.0,12470.0,200.0,25000.0) }
        assertTrue(p.tripped);assertEquals(2,p.reason)
    }
    @Test fun aRealPrimaryShortStillBlowsA200AmpLinkAndRetainsItsDelayAcrossSave() {
        val c=Circuit();c.load.resistance=.0001;c.run(38)
        assertTrue(c.d.tap.current>200.0);assertFalse(c.d.protection.tripped)
        val saved=CompoundTag().also(c.d::writeToNBT);c.d.readFromNBT(saved)
        assertEquals(2,c.d.protection.pendingReason);assertTrue(c.d.protection.elapsed>1.8)
        c.run(5);assertTrue(PoleHardware.fuse(c.d.inventory.getItem(1))!!.spent);assertEquals(2,c.d.protection.reason)
    }
}
