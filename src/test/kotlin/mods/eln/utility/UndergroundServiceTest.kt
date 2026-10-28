package mods.eln.utility

import mods.eln.Eln
import mods.eln.misc.*
import mods.eln.node.NodeConnectionEndpoint
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.ElectricalConnection
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.transparentnode.industrial.*
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import kotlin.test.*

class UndergroundServiceTest {
    private fun meter(wide: Boolean=false)=HouseServiceElement(TransparentNode().apply { coordinate=Coordinate(10,80,10,0) },
        Eln.transparentNodeItem.subItemList.values.filterIsInstance<HouseServiceDescriptor>().single { it.meter && it.wide==wide })
    @Test fun meterRegistersAndSerialMoveWithTheMeterAndNewMetersStartAtZero() {
        val a=meter();val b=meter(true)
        a.inventory.setItem(0,Eln.findItemStack("Smart Digital Service Meter",1));a.inventoryChange(a.inventory)
        a.energy.step(3600.0,2400.0);a.energy.step(1800.0,-1000.0)
        assertTrue(a.setRemoteDisconnect(true));val serial=a.meterSerial;assertEquals(10,serial.length)
        val item=a.inventory.removeItemNoUpdate(0);a.inventoryChange(a.inventory)
        b.inventory.setItem(0,item);b.inventoryChange(b.inventory)
        assertEquals(2.4,b.energy.importedKwh,1e-12);assertEquals(.5,b.energy.exportedKwh,1e-12)
        assertEquals(serial,b.meterSerial);assertTrue(b.disconnected)
        b.inventory.setItem(0,Eln.findItemStack("Smart Digital Service Meter",1));b.inventoryChange(b.inventory)
        assertEquals(0.0,b.energy.importedKwh);assertEquals(0.0,b.energy.exportedKwh);assertFalse(b.disconnected);assertNotEquals(serial,b.meterSerial)
    }
    @Test fun analogDiskUsesThePrintedSevenPointTwoWattHourConstantAndReversesForExport() {
        assertEquals(360.0,ServiceMeters.diskDegrees(7.2*3600),1e-10)
        assertEquals(-360.0,ServiceMeters.diskDegrees(-7.2*3600),1e-10)
        assertEquals(180.0,ServiceMeters.diskPosition(.0036,0.0),1e-10)
    }
    @Test fun oldSocketRegistersMigrateOntoTheInstalledMeterWithoutResettingOrDuplicatingIt() {
        val original=meter();original.inventory.setItem(0,Eln.findItemStack("Analog Glass Service Meter",1));original.inventoryChange(original.inventory)
        original.energy.step(3600.0,1000.0)
        val saved=CompoundTag().also(original::writeToNBT)
        // Old saves stored their register on the cabinet, not on the removable item.
        var stripped=0
        fun legacy(tag: net.minecraft.nbt.Tag) {
            when(tag) {
                is CompoundTag->{ if(tag.contains("serviceMeter")) { tag.remove("serviceMeter");stripped++ };tag.allKeys.toList().forEach { legacy(tag.get(it)!!) } }
                is net.minecraft.nbt.ListTag->tag.forEach(::legacy)
            }
        }
        legacy(saved);assertEquals(1,stripped)
        val restored=meter();restored.readFromNBT(saved)
        assertEquals(1.0,restored.energy.importedKwh,1e-12)
        val removed=restored.inventory.removeItem(0,1)
        val other=meter(true);other.inventory.setItem(0,removed);other.inventoryChange(other.inventory)
        assertEquals(1.0,other.energy.importedKwh,1e-12);assertEquals(1,other.inventory.getItem(0).count)
        assertFalse(other.setRemoteDisconnect(true),"An analog meter must not acquire a smart disconnect")
    }
    @Test fun wideSocketHasTwoReservedWallCellsAndTwoIndependentDownwardEntriesInEveryRotation() {
        val m=meter(true);val d=m.transparentNodeDescriptor as HouseServiceDescriptor
        for(front in listOf(Direction.XN,Direction.XP,Direction.ZN,Direction.ZP)) {
            m.front=front
            val ghost=d.getGhostGroupFront(front)!!.elementList.single()
            val right=front.right().toFacing();assertEquals(right.stepX,ghost.x);assertEquals(right.stepZ,ghost.z);assertEquals(0,ghost.y)
            for(output in listOf(false,true)) {
                val pos=WideMeterWiring.cableCell(m,output)
                val remote=SixNode().apply { coordinate=Coordinate(pos.x,pos.y,pos.z,0) }
                val endpoint=NodeConnectionEndpoint(remote,Direction.YP,LRDU.Up,null,front.inverse,null)
                val load=m.getElectricalLoad(Direction.YN,WideMeterWiring.port(m,output),endpoint)
                assertSame(if(output)m.load[2] else m.line[2],load)
            }
            assertNull(m.getElectricalLoad(Direction.YP,LRDU.Down));assertNull(m.getElectricalLoad(front,LRDU.Down))
            val wrong=SixNode().apply { coordinate=Coordinate(10,81,10,0) }
            assertNull(m.getElectricalLoad(Direction.YN,WideMeterWiring.port(m,true),NodeConnectionEndpoint(wrong,Direction.YN,LRDU.Down,null,front.inverse,null)))
        }
        assertFalse(m.allowsNewGridLinks)
    }
    @Test fun aBottomFedWideSocketTracksRealLoadEnergyAndTheSmartDisconnectLeavesItsLinePowered() {
        val m=meter(true);m.inventory.setItem(0,Eln.findItemStack("Smart Digital Service Meter",1));m.inventoryChange(m.inventory)
        val root=RootSystem(.01,1);m.electricalLoadList.forEach(root::addState);m.electricalComponentList.forEach(root::addComponent)
        val feeder=NbtElectricalLoad("buried feeder").apply { serialResistance=.00001 };root.addState(feeder)
        val source=VoltageSource("utility",feeder,null).apply { voltage=240.0 };root.addComponent(source)
        root.addComponent(ElectricalConnection(feeder,m.line[2]))
        val load=Resistor(m.load[2],null).apply { resistance=24.0 };root.addComponent(load)
        fun step() { m.electricalProcessList.forEach { it.process(.01) };root.step() }
        repeat(20) { step() };assertEquals(240.0,m.load[2].voltage,.1);assertTrue(m.energy.importedKwh>0)
        source.voltage=228.0;repeat(10) { step() };assertEquals(228.0,m.load[2].voltage,.1)
        assertTrue(m.setRemoteDisconnect(true));repeat(5) { step() }
        assertEquals(228.0,m.line[2].voltage,.01);assertEquals(0.0,m.load[2].voltage,1e-7)
    }
    @Test fun padSuppliesBoth120VLegsAnd240VFromOnePassiveWindingAndFollowsPrimarySag() {
        val d=Eln.transparentNodeItem.subItemList[4+(126 shl 6)] as IndustrialDescriptor
        val pad=IndustrialElement(TransparentNode().apply { coordinate=Coordinate(0,80,0,0) },d)
        pad.inventory.setItem(0,Eln.findItemStack(PoleHardware.transformerNames[0],1));pad.inventory.setItem(1,Eln.findItemStack(PoleHardware.fuseNames[0],1));pad.inventoryChange(pad.inventory)
        val root=RootSystem(.01,1);pad.electricalLoadList.forEach(root::addState);pad.electricalComponentList.forEach(root::addComponent)
        val source=VoltageSource("primary",pad.input,null).apply { voltage=12470.0 };root.addComponent(source)
        val services=pad.padSecondary!!.loads
        val loads=services.mapIndexed { i,b->Resistor(b,null).apply { resistance=if(i==2)96.0 else 48.0 }.also(root::addComponent) }
        repeat(8) { root.step() };val nominal=services.map { it.voltage }
        nominal.forEachIndexed { i,v->assertEquals(if(i==2)240.0 else 120.0,v,.2) }
        assertTrue(source.power>=loads.sumOf { it.power })
        source.voltage=11846.5;repeat(8) { root.step() }
        services.forEachIndexed { i,b->assertEquals(nominal[i]*.95,b.voltage,1e-6) }
        assertTrue(pad.applySettings(doubleArrayOf(12470.0,208.0)));source.voltage=12470.0;repeat(8) { root.step() }
        assertEquals(208.0,pad.outputs[0].voltage,.2);assertEquals(104.0,services[0].voltage,.2);assertEquals(104.0,services[1].voltage,.2)
        assertSame(pad.input,pad.getElectricalLoad(Direction.YN,LRDU.Down));assertSame(pad.outputs[0],pad.getElectricalLoad(Direction.YN,LRDU.Up))
        pad.setEnabled(false);repeat(5) { root.step() };services.forEach { assertEquals(0.0,it.voltage,1e-7) }
    }
    @Test fun undergroundEquipmentHasNoOverheadSpanEndpointAndLegacyThreePhaseCabinetsKeepTheirIds() {
        val riser=Eln.transparentNodeItem.subItemList[1+(127 shl 6)] as HouseServiceDescriptor
        val e=HouseServiceElement(TransparentNode(),riser);assertTrue(riser.mast.underground);assertFalse(e.allowsNewGridLinks);assertFalse(e.serviceDropEndpoint)
        assertTrue(Eln.transparentNodeItem.subItemList[2+(127 shl 6)] is UndergroundHandholeDescriptor)
        for(kind in listOf(DistributionKind.UNDERGROUND_THREE,DistributionKind.UNDERGROUND_LIGHT_THREE)) {
            val old=Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>().single { it.kind==kind }
            assertTrue(old.isHidden());assertTrue(kind.retiredGroundCabinet)
        }
    }
    @Test fun newModelsHaveRealJawsGlassSealsAndASolidPadBase() {
        val model=Obj3D();assertTrue(model.loadFile("industrial_pad/industrial_pad.obj"))
        for(p in listOf("Case","Door","Pad","Elbows","Lugs","Warning","Ground"))assertNotNull(model.getPart(p))
        assertTrue(model.getPart("Pad")!!.boundingBox().min.y<=-.49)
        val meter=Obj3D();assertTrue(meter.loadFile("wideundergroundmetersocket/wideundergroundmetersocket.obj"))
        for(p in listOf("Glass","MeterRing","Jaws","JawInsulator","Seal","EntryLine","EntryLoad"))assertNotNull(meter.getPart(p))
        assertTrue(meter.xMax-meter.xMin>1.8f)
    }
}
