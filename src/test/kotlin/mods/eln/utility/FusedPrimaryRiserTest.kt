package mods.eln.utility

import mods.eln.Eln
import mods.eln.misc.*
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.abs
import kotlin.test.*

class FusedPrimaryRiserTest {
    private fun pole(kind: DistributionKind)=DistributionPoleElement(TransparentNode().apply { coordinate=Coordinate(0,80,0,0) },
        Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>().single { it.kind==kind })
    private fun physicalFuse(p: DistributionPoleElement,name: String) {
        p.fuses.single().removeCartridge()
        assertTrue(p.fuses.single().insertCartridge(Eln.findItemStack(name,1)))
        p.readItemStackNBT(p.getItemStackNBT()) // Same settings/contact restoration used on placement.
    }
    @Test fun primaryCutoutDisconnectsBothBuriedPortsWithoutInterruptingTheSeparateSecondary() {
        for(kind in listOf(DistributionKind.FEEDER_SINGLE,DistributionKind.FEEDER_LIGHT_SINGLE)) {
            val p=pole(kind);assertEquals(1,p.fuses.size);assertNotSame(p.lines[0],p.outs[0])
            assertSame(p.lines[0],p.gridLoads(32).single());assertSame(p.outs[0],p.gridLoads(36).single())
            assertSame(p.outs[0],p.getElectricalLoad(Direction.YN,LRDU.Up));assertSame(p.outs[0],p.getElectricalLoad(p.front.left(),LRDU.Down))
            if(kind.light)p.readItemStackNBT(CompoundTag().apply { putInt("utilityLampVoltage",240) })
            else assertTrue(p.poleStreetlight!!.install(Eln.findItemStack(PoleStreetlightAttachment.NAME,1),true))
            fun lampWatts()=if(kind.light)p.lampPower else p.poleStreetlight!!.power
            physicalFuse(p,"10 A Pole Fuse")
            val root=RootSystem(.01,1);p.electricalLoadList.forEach(root::addState);p.electricalComponentList.forEach(root::addComponent)
            root.addComponent(VoltageSource("overhead primary",p.lines[0],null).apply { voltage=12470.0 })
            root.addComponent(VoltageSource("separate 240 V service",p.services[2],null).apply { voltage=240.0 })
            root.addComponent(Resistor(p.outs[0],null).apply { resistance=12470.0/5 })
            fun step() { repeat(10) { p.electricalProcessList.forEach { it.process(.01) };root.step() } }
            step();assertEquals(12470.0,p.outs[0].voltage,.1);assertFalse(p.fuses[0].blown);assertEquals(70.0,lampWatts(),.01)
            p.operateFuse(0);step();assertFalse(p.fuses[0].closed);assertEquals(12470.0,p.lines[0].voltage,.001)
            assertTrue(abs(p.outs[0].voltage)<.001);assertEquals(240.0,p.services[2].voltage,.001);assertEquals(70.0,lampWatts(),.01)
            p.operateFuse(0);step();assertEquals(12470.0,p.outs[0].voltage,.1)
            physicalFuse(p,"1 A Pole Fuse");step()
            assertTrue(p.fuses[0].blown);assertFalse(p.fuses[0].closed);assertTrue(abs(p.outs[0].voltage)<.001)
            assertEquals(12470.0,p.lines[0].voltage,.001);assertEquals(240.0,p.services[2].voltage,.001)
            assertEquals(70.0,lampWatts(),.01)
        }
    }
    @Test fun cutoutAlsoIsolatesBackfeedAndRetainsItsPhysicalCartridgeAcrossReloadAndPickup() {
        val p=pole(DistributionKind.FEEDER_SINGLE);physicalFuse(p,"5 A Pole Fuse")
        val root=RootSystem(.01,1);p.electricalLoadList.forEach(root::addState);p.electricalComponentList.forEach(root::addComponent)
        root.addComponent(VoltageSource("buried source",p.outs[0],null).apply { voltage=12470.0 })
        root.addComponent(Resistor(p.lines[0],null).apply { resistance=12470.0 })
        repeat(4) { p.electricalProcessList.forEach { it.process(.01) };root.step() }
        assertEquals(12470.0,p.lines[0].voltage,.1)
        p.operateFuse(0);root.step();assertTrue(abs(p.lines[0].voltage)<.001);assertEquals(12470.0,p.outs[0].voltage,.001)
        val saved=CompoundTag().also(p::writeToNBT)
        for(tag in listOf(saved,p.getItemStackNBT())) {
            val copy=pole(DistributionKind.FEEDER_SINGLE).apply { readFromNBT(tag) }
            assertFalse(copy.fuses[0].closed);assertEquals(5.0,copy.fuses[0].rating(100.0))
            assertEquals("5 A Pole Fuse",Eln.sharedItem.getDescriptor(copy.fuses[0].removeCartridge()).name)
            assertTrue(copy.fuses[0].removeCartridge().isEmpty)
        }
    }
    @Test fun existingUnfusedSavesMigrateClosedAndTheGenericFuseCurrentControlActuallyApplies() {
        val p=pole(DistributionKind.FEEDER_SINGLE)
        val old=CompoundTag().also(p::writeToNBT)
        old.allKeys.filter { it.startsWith("utilityFuse0") || it.startsWith("phaseOut0") }.toList().forEach(old::remove)
        val copy=pole(DistributionKind.FEEDER_SINGLE).apply { readFromNBT(old) }
        assertTrue(copy.fuses.single().closed);assertFalse(copy.fuses.single().blown);assertTrue(copy.fuses.single().hasCartridge)
        val bytes=ByteArrayOutputStream();DataOutputStream(bytes).apply { writeByte(91);writeDouble(12.0) }
        copy.networkUnserialize(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))
        assertEquals(12.0,copy.fuseAmps);assertEquals(12.0,copy.fuses[0].rating(copy.fuseAmps))
        val model=Obj3D().apply { assertTrue(loadFile("distributionpole/distributionpole.obj")) }
        assertNotNull(model.getPart("RiserFuseJumper"));assertNotNull(model.getPart("RiserJumper1"))
    }
}
