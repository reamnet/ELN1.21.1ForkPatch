package mods.eln.utility.insulator

import mods.eln.Eln
import mods.eln.misc.*
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.gridnode.WireSpanConnection
import org.junit.Test
import kotlin.test.*

class LineInsulatorTest {
    private fun element(voltage: InsulatorVoltage,feed: Boolean)=LineInsulatorElement(TransparentNode().apply { coordinate=Coordinate(0,80,0,0) },
        Eln.transparentNodeItem.subItemList.values.filterIsInstance<LineInsulatorDescriptor>().single { it.voltage==voltage && it.feed==feed })
    @Test fun everyVoltageHasTwoStableItemsAndMatchingModelDimensions() {
        for((i,v)in InsulatorVoltage.entries.withIndex())for(feed in listOf(false,true)) {
            val e=element(v,feed);assertSame(e.insulator,Eln.transparentNodeItem.subItemList[43+i*2+(if(feed)1 else 0)+(125 shl 6)])
            val m=Obj3D().apply { assertTrue(loadFile("${v.model}/${v.model}.obj")) };val porcelain=m.getPart("Porcelain").boundingBox()
            assertEquals(v.length,porcelain.max.x-porcelain.min.x,1e-6)
            assertEquals(v.radius*2,porcelain.max.y-porcelain.min.y,1e-6)
            assertEquals(1,e.renderWireCount(32));assertEquals(1,e.electricalLoadList.size)
            assertTrue(e.insulator.geometryBounds().maxY<3)
        }
        assertTrue(InsulatorVoltage.LOW.length<InsulatorVoltage.DISTRIBUTION.length)
        assertEquals(2.5,InsulatorVoltage.TRANSMISSION.length/InsulatorVoltage.SUBTRANSMISSION.length,0.0)
    }
    @Test fun attachmentPointsFollowAllSixMountDirectionsAndFeedIsTheSamePhysicalBus() {
        for(v in InsulatorVoltage.entries)for(feed in listOf(false,true))for(front in Direction.entries) {
            val e=element(v,feed);e.front=front
            val expected=front.rotateFromXN(net.minecraft.world.phys.Vec3(-.5+v.length,0.0,0.0))
            assertTrue(expected.distanceTo(e.renderedGridCablePoint(32,0))<1e-9)
            for(side in Direction.entries)for(port in LRDU.entries) {
                val load=e.getElectricalLoad(side,port)
                if(feed && side!=front.inverse)assertSame(e.bus,load) else assertNull(load)
            }
        }
    }
    @Test fun spansRetainRealVoltageDropAndTheSupportNeverConvertsPower() {
        for(v in InsulatorVoltage.entries) {
            val a=element(v,true);val b=element(v,false);val root=RootSystem(.01,1)
            root.addState(a.bus);root.addState(b.bus)
            val source=VoltageSource("input",a.bus,null).apply { voltage=v.volts.toDouble() }
            val span=WireSpanConnection(a.bus,b.bus,.25);val load=Resistor(b.bus,null).apply { resistance=100.0 }
            listOf(source,span,load).forEach(root::addComponent);repeat(3) { root.step() }
            assertEquals(v.volts*100/(100+.25+.00002),b.bus.voltage,1e-6)
            assertEquals(source.power,load.power+span.current*span.current*span.resistance,1e-5)
            source.voltage*=.95;root.step();assertTrue(b.bus.voltage<v.volts*.95)
        }
    }
    @Test fun lowVoltageServiceChoicePersistsWhileHighVoltageModelsStaySingleCircuit() {
        val e=element(InsulatorVoltage.LOW,true);assertTrue(e.selectCircuit(42));assertEquals("SERVICE_240",e.gridCircuitKey(42))
        val copy=element(InsulatorVoltage.LOW,true);copy.readItemStackNBT(e.getItemStackNBT());assertEquals(42,copy.terminal)
        assertFalse(element(InsulatorVoltage.DISTRIBUTION,true).selectCircuit(42))
    }
}
