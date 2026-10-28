package mods.eln.utility

import mods.eln.Eln
import mods.eln.gridnode.GridElement
import mods.eln.misc.*
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.VoltageSource
import org.junit.Test
import kotlin.test.*

class PoleStreetlightAttachmentTest {
    private fun pole()=DistributionPoleElement(TransparentNode().apply { coordinate=Coordinate(0,80,0,0);elementId=3+(127 shl 6) },
        Eln.transparentNodeItem.subItemList[3+(127 shl 6)] as DistributionPoleDescriptor)
    @Test fun everyPlainDistributionAndTransmissionPoleSupportsTheSameRemovableSecondaryAttachment() {
        val descriptors=Eln.transparentNodeItem.subItemList.values
        for(d in descriptors.filterIsInstance<DistributionPoleDescriptor>().filter { !it.kind.underground && !it.kind.light }) {
            val p=DistributionPoleElement(TransparentNode(),d);assertNotNull(p.poleStreetlight,d.kind.name)
        }
        for(d in descriptors.filterIsInstance<TransmissionDescriptor>()) {
            val p=TransmissionElement(TransparentNode(),d)
            if(d.kind.station)assertNull(p.poleStreetlight) else { assertNotNull(p.poleStreetlight);assertEquals(3,p.gridLoads(50).size) }
        }
        for(d in descriptors.filterIsInstance<mods.eln.gridnode.electricalpole.ElectricalPoleDescriptor>()) {
            val p=mods.eln.gridnode.electricalpole.ElectricalPoleElement(TransparentNode(),d);assertNotNull(p.poleStreetlight,d.name)
        }
        for(d in descriptors.filterIsInstance<DistributionPoleDescriptor>().filter { it.kind.light })assertTrue(d.isHidden(),d.kind.name)
    }
    @Test fun installedLampNeverUsesPrimaryPowerAndItsSecondaryLoadFollowsVoltageSquared() {
        val p=pole();val lamp=p.poleStreetlight!!;val item=Eln.findItemStack(PoleStreetlightAttachment.NAME,2)
        assertTrue(lamp.install(item,false));assertEquals(1,item.count);assertFalse(lamp.install(item,false));assertEquals(1,item.count)
        val root=RootSystem(.01,1);p.electricalLoadList.forEach(root::addState);p.electricalComponentList.forEach(root::addComponent)
        val hv=VoltageSource("primary only",p.lines[0],null).apply { voltage=12470.0 };root.addComponent(hv)
        val lv=VoltageSource("external secondary",p.services[2],null).apply { voltage=0.0 };root.addComponent(lv)
        fun step() { repeat(15) { p.electricalProcessList.forEach { it.process(.01) };root.step() } }
        step();assertTrue(lamp.power<1e-6);assertEquals(0,lamp.light)
        lv.voltage=240.0;step();assertEquals(70.0,lamp.power,.01);assertTrue(lamp.light>=14)
        lv.voltage=228.0;step();assertEquals(70*.95*.95,lamp.power,.01)
        hv.voltage=0.0;step();assertEquals(70*.95*.95,lamp.power,.01)
        val removed=lamp.remove();assertEquals(3,UtilityItems.part(removed));step();assertEquals(0,lamp.light);assertTrue(lamp.power<1e-6);assertTrue(lamp.remove().isEmpty)
    }
    @Test fun aimingAndPhysicalAttachmentPersistWithWorldAndCarriedPoleWithoutDuplicatingTheItem() {
        val p=pole();val lamp=p.poleStreetlight!!;assertTrue(lamp.install(Eln.findItemStack(PoleStreetlightAttachment.NAME,1),false))
        assertTrue(lamp.settings(true,true,120,345,-2,-30));assertFalse(lamp.settings(true,true,480,345,-2,-30))
        val tag=net.minecraft.nbt.CompoundTag().also(p::writeToNBT)
        val loaded=pole().apply { readFromNBT(tag) }.poleStreetlight!!;assertTrue(loaded.installed);assertEquals(345,loaded.yaw);assertEquals(-2,loaded.height);assertTrue(loaded.automatic)
        val carried=p.dropItemStack;val moved=pole().poleStreetlight!!;moved.read(carried.tagCompound!!)
        assertTrue(moved.installed);assertEquals(-30,moved.pitch);assertFalse(carried.tagCompound!!.getCompound("poleStreetlightAttachment").contains("gridLinks"))
        assertEquals(1,moved.remove().count);assertTrue(moved.remove().isEmpty)
    }
}
