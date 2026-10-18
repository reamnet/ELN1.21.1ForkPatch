package mods.eln.lighting

import mods.eln.Eln
import mods.eln.item.lampitem.LampDescriptor
import mods.eln.misc.*
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.transparentnode.FuelGeneratorSlowProcess
import mods.eln.transparentnode.worklight.*
import mods.eln.sixnode.lampsocket.*
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import kotlin.test.*

class NewFixtureControlsTest {
    private fun generator(): WorkLightElement {
        val descriptor=Eln.transparentNodeItem.subItemList[124 shl 6] as WorkLightDescriptor
        return WorkLightElement(TransparentNode().apply { coordinate=Coordinate(0,64,0,0) },descriptor)
    }
    private fun packet(id: Int)=DataInputStream(ByteArrayInputStream(byteArrayOf(id.toByte())))

    @Test fun motionHoldTimesOutAndDaylightCancelsItImmediately() {
        val hold=MotionHold()
        assertFalse(hold.step(.25,false,true))
        assertTrue(hold.step(.25,true,true))
        assertTrue(hold.step(29.75,false,true))
        assertFalse(hold.step(.25,false,true))
        assertTrue(hold.step(.25,true,true))
        assertTrue(hold.step(29.75,false,true))
        assertTrue(hold.step(.25,true,true))
        assertEquals(30.0,hold.remaining)
        assertFalse(hold.step(.25,false,false))
        assertEquals(0.0,hold.remaining)
    }

    @Test fun motionOffDisconnectsBulbLoadWithoutChangingItsInventoryOrSettings() {
        val descriptor=Eln.sixNodeItem.subItemList.values.filterIsInstance<LampSocketDescriptor>().single { it.fixture==DecorativeLampStyle.MOTION }
        val e=LampSocketElement(mods.eln.node.six.SixNode().apply { coordinate=Coordinate(0,64,0,0) },Direction.XN,descriptor)
        val bulb=Eln.findItemStack("120V LED Light Bulb",2)
        e.inventory.setItem(0,bulb)
        e.motionMode=2;assertTrue(e.updateMotion(.25))
        val onResistance=e.lampResistor.resistance
        e.motionMode=3;assertFalse(e.updateMotion(.25))
        assertTrue(e.lampResistor.resistance>onResistance)
        assertTrue(120.0*120/e.lampResistor.resistance<.001)
        val nbt=CompoundTag().also(e::writeToNBT)
        e.motionMode=2;e.readFromNBT(nbt)
        assertEquals(3,e.motionMode);assertEquals(2,e.installedBulbs)
    }

    @Test fun gasGeneratorReallyPowersEveryBulbVoltageAndConsumesFuelUnderLoad() {
        val bulbs=Eln.sharedItem.subItemList.values.filterIsInstance<LampDescriptor>()
        for(bulb in bulbs) {
            val e=generator()
            e.inventory.setItem(0,bulb.newItemStack(2));e.configureBulbs()
            val root=RootSystem(.05,1)
            e.electricalLoadList.forEach(root::addState);e.electricalComponentList.forEach(root::addComponent)
            e.tankLevel=.5;e.on=true;e.voltageGracePeriod=1.0
            val fuel=FuelGeneratorSlowProcess(e)
            repeat(60) { fuel.process(.05);root.step() }
            assertTrue(e.on,bulb.name)
            assertEquals(bulb.lampData.nominalU,e.positiveLoad.voltage,.01,bulb.name)
            assertEquals(bulb.lampData.nominalP*2,e.powerSource.effectivePower,.01,bulb.name)
            assertTrue(e.tankLevel<.5,bulb.name)
        }
    }

    @Test fun stoppedCartAcceptsExternalCablePowerWithoutAnActiveEngine() {
        val e=generator();e.inventory.setItem(0,Eln.findItemStack("120V LED Light Bulb",2));e.configureBulbs()
        val root=RootSystem(.05,1)
        e.electricalLoadList.forEach(root::addState);e.electricalComponentList.forEach(root::addComponent)
        e.powerSource.power=2000.0;repeat(5) { root.step() }
        e.powerSource.power=0.0;repeat(5) { root.step() }
        val input=mods.eln.sim.ElectricalLoad();root.addState(input)
        root.addComponent(mods.eln.sim.mna.component.VoltageSource("grid",input,null).apply { voltage=120.0 })
        root.addComponent(mods.eln.sim.mna.component.Resistor(input,e.positiveLoad).apply { resistance=.01 })
        repeat(10) { root.step() }
        assertEquals(120.0,e.positiveLoad.voltage,.02)
        assertEquals(0.0,e.powerSource.effectivePower,.001)
    }

    @Test fun emptyTankStopsAndWirelessModeCannotRunTheEngine() {
        val e=generator()
        e.networkUnserialize(packet(80));assertFalse(e.on)
        e.tankLevel=.5;e.networkUnserialize(packet(80));assertTrue(e.on)
        e.networkUnserialize(packet(81));assertTrue(e.wireless);assertFalse(e.on)
        e.networkUnserialize(packet(80));assertFalse(e.on)
        e.networkUnserialize(packet(81));e.networkUnserialize(packet(80));assertTrue(e.on)
        e.tankLevel=0.0;FuelGeneratorSlowProcess(e).process(.05)
        assertFalse(e.on);assertEquals(0.0,e.powerSource.power)
    }

    @Test fun pickedUpGeneratorPreservesFuelAndChannelButNeverStartsInAnInventory() {
        val e=generator();e.tankLevel=.37;e.channel="Workshop";e.wireless=true
        val saved=e.getItemStackNBT()
        val restored=generator();restored.readItemStackNBT(saved)
        assertEquals(.37,restored.tankLevel);assertEquals("Workshop",restored.channel)
        assertTrue(restored.wireless);assertFalse(restored.on)
        restored.readItemStackNBT(saved.copy().apply { putDouble("tankLevel",Double.POSITIVE_INFINITY) })
        assertEquals(1.0,restored.tankLevel)
    }

    @Test fun factoryFixtureHasAWholeBlockOfSuspensionAndAllNewLightsHaveClearance() {
        val model=Obj3D().apply { assertTrue(loadFile("hangingfactorylight/hangingfactorylight.obj")) }
        assertTrue(model.getPart("Metal").boundingBox().min.x<.05)
        assertTrue(model.getPart("Light0").boundingBox().min.x>1.15)
        val fixture=Eln.sixNodeItem.subItemList.values.filterIsInstance<LampSocketDescriptor>().single { it.fixture==DecorativeLampStyle.FACTORY }
        assertTrue(fixture.getGhostGroup(Direction.YP,LRDU.Up)!!.elementList.any { it.y == -1 })
    }
}
