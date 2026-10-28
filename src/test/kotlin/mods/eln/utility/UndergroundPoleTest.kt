package mods.eln.utility

import mods.eln.Eln
import mods.eln.misc.*
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.ElectricalConnection
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.nbt.NbtElectricalLoad
import org.junit.Test
import kotlin.test.*

class UndergroundPoleTest {
    private fun pole(kind: DistributionKind)=DistributionPoleElement(TransparentNode().apply { coordinate=Coordinate(0,80,0,0) },
        Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>().single { it.kind==kind })
    @Test fun newRiserPolesAppendUnusedIdsAndRetainRealOverheadEndpointsAndTallGeometry() {
        val kinds=listOf(DistributionKind.FEEDER_SINGLE,DistributionKind.FEEDER_LIGHT_SINGLE,DistributionKind.UNDERGROUND_SECONDARY_TRANSFORMER_SINGLE)
        kinds.forEachIndexed { i,kind->
            val d=Eln.transparentNodeItem.subItemList[3+i+(127 shl 6)] as DistributionPoleDescriptor
            assertEquals(kind,d.kind);val p=pole(kind);assertEquals(1,kind.phases);assertFalse(kind.underground)
            assertTrue(p.allowsNewGridLinks);assertEquals(listOf(p.lines[0]),p.gridLoads(32));assertEquals(7.15,p.gridCablePoint(32,0).y)
            assertTrue(d.ghostGroup!!.elementList.any { it.y==7 })
        }
        val model=Obj3D().apply { assertTrue(loadFile("distributionpole/distributionpole.obj")) }
        for(part in listOf("Riser1","RiserJumper1","SecondaryServiceRiser"))assertNotNull(model.getPart(part))
    }
    @Test fun primaryRiserAndExternalSecondaryStayElectricallyIndependentAndLightNeedsItsLvFeed() {
        val p=pole(DistributionKind.FEEDER_LIGHT_SINGLE)
        assertFalse(p.kind.transformer);assertEquals(0,p.inventory.containerSize)
        assertSame(p.outs[0],p.getElectricalLoad(Direction.YN,LRDU.Up));assertSame(p.outs[0],p.getElectricalLoad(p.front.left(),LRDU.Down))
        assertSame(p.services[2],p.getElectricalLoad(Direction.YN,LRDU.Down));assertSame(p.services[0],p.getElectricalLoad(Direction.YN,LRDU.Left));assertSame(p.services[1],p.getElectricalLoad(Direction.YN,LRDU.Right))
        val root=RootSystem(.01,1);p.electricalLoadList.forEach(root::addState);p.electricalComponentList.forEach(root::addComponent)
        val primary=VoltageSource("buried primary",p.lines[0],null).apply { voltage=12470.0 };root.addComponent(primary)
        val feeder=NbtElectricalLoad("separate LV feed").apply { serialResistance=.00001 };root.addState(feeder)
        val secondary=VoltageSource("separate secondary",feeder,null).apply { voltage=0.0 };root.addComponent(secondary)
        root.addComponent(ElectricalConnection(feeder,p.services[2]))
        fun step() { repeat(12) { p.electricalProcessList.forEach { it.process(.01) };root.step() } }
        step();assertEquals(12470.0,p.lines[0].voltage,.001);assertTrue(p.lampPower<1e-6);assertEquals(0,p.lightLevel)
        secondary.voltage=240.0;step();assertEquals(120.0,p.services[0].voltage,.01);assertEquals(240.0,p.services[2].voltage,.01)
        assertEquals(70.0,p.lampPower,.1);assertTrue(p.lightLevel>=14)
        primary.voltage=0.0;step();assertEquals(70.0,p.lampPower,.1)
        secondary.voltage=228.0;step();assertEquals(114.0,p.services[0].voltage,.01);assertEquals(70*.95*.95,p.lampPower,.1)
    }
    @Test fun undergroundSecondaryTransformerSharesItsRealFuseAndTracksPrimarySagAtBottomPorts() {
        val p=pole(DistributionKind.UNDERGROUND_SECONDARY_TRANSFORMER_SINGLE)
        p.inventory.setItem(0,Eln.findItemStack(PoleHardware.transformerNames[1],1));p.inventoryChange(p.inventory)
        p.fuses[0].removeCartridge();assertTrue(p.fuses[0].insertCartridge(Eln.findItemStack(PoleHardware.fuseNames[0],1)))
        val root=RootSystem(.01,1);p.electricalLoadList.forEach(root::addState);p.electricalComponentList.forEach(root::addComponent)
        val source=VoltageSource("12.47 kV primary",p.lines[0],null).apply { voltage=12470.0 };root.addComponent(source)
        root.addProcess(p.unit!!.controller)
        root.addComponent(Resistor(p.services[2],null).apply { resistance=96.0 });root.addComponent(Resistor(p.services[0],null).apply { resistance=48.0 })
        fun step() { repeat(30) { p.electricalProcessList.forEach { it.process(.01) };root.step() } }
        step();assertTrue(p.installed);assertTrue(p.fuses[0].closed);val volts=p.services.map { it.voltage }
        assertEquals(120.0,volts[0],1.0);assertEquals(240.0,volts[2],1.0)
        source.voltage=11846.5;step();p.services.forEachIndexed { i,s->assertEquals(volts[i]*.95,s.voltage,.2) }
        assertSame(p.services[2],p.getElectricalLoad(Direction.YN,LRDU.Down));assertSame(p.services[0],p.getElectricalLoad(Direction.YN,LRDU.Left))
        p.fuses[0].operate();step();assertTrue(p.services.all { kotlin.math.abs(it.voltage)<1e-5 })
    }
}
