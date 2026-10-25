package mods.eln.lighting

import mods.eln.Eln
import mods.eln.misc.*
import mods.eln.node.six.*
import mods.eln.node.transparent.TransparentNode
import mods.eln.registration.DecorativeLightRegistration
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sixnode.lampsocket.*
import mods.eln.transparentnode.cord.*
import mods.eln.utility.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.Vec3
import org.junit.Test
import java.io.*
import kotlin.test.*

class TransmissionAndLightingTest {
    private fun transmission(kind: TransmissionKind)=TransmissionElement(TransparentNode().apply { coordinate=Coordinate(0,64,0,0) },
        Eln.transparentNodeItem.subItemList[kind.subId+(125 shl 6)] as TransmissionDescriptor).apply { front=Direction.XN }
    private fun fixture(style: DecorativeLampStyle)=LampSocketElement(SixNode().apply { coordinate=Coordinate(0,74,0,0) },Direction.YP,
        Eln.sixNodeItem.subItemList[DecorativeLightRegistration.idFor(style)] as LampSocketDescriptor)

    @Test fun everyBulbFixtureAccepts240BeforeOrAfterBulbsAreInserted() {
        val bulb=Eln.findItemStack("240V LED Light Bulb",1)
        assertFalse(bulb.isEmpty)
        for(style in DecorativeLampStyle.entries) {
            val e=fixture(style)
            assertTrue(e.acceptsCord(CordRating.V240),style.name)
            assertTrue(e.acceptsCord(CordRating.V120),style.name)
            assertFalse(e.acceptsCord(CordRating.V240_30),style.name)
            e.inventory.setItem(0,bulb.copy())
            assertTrue(e.acceptsCord(CordRating.V240),style.name)
            assertEquals(240.0,e.cordVoltage,0.0)
        }
    }
    @Test fun warehouseDropPersistsRejectsInvalidValuesAndMovesTheRealLightSourceDown() {
        val e=fixture(DecorativeLampStyle.WAREHOUSE_HIGH_BAY)
        val bytes=ByteArrayOutputStream().also { DataOutputStream(it).apply { writeByte(10);writeDouble(6.75) } }.toByteArray()
        e.networkUnserialize(DataInputStream(ByteArrayInputStream(bytes)))
        assertEquals(6.75,e.suspension.drop)
        val saved=CompoundTag().also(e::writeToNBT)
        val restored=fixture(DecorativeLampStyle.WAREHOUSE_HIGH_BAY).also { it.readFromNBT(saved) }
        assertEquals(6.75,restored.suspension.drop)
        assertFalse(restored.suspension.set(Double.NaN));assertFalse(restored.suspension.set(Double.POSITIVE_INFINITY))
        assertEquals(6.75,restored.suspension.drop)
        restored.suspension.set(40.0);assertEquals(7.0,restored.suspension.drop)
        restored.suspension.set(-1.0);assertEquals(0.0,restored.suspension.drop)
        val style=DecorativeLampStyle.WAREHOUSE_HIGH_BAY
        val before=style.worldPoint(Vec3.ZERO,Direction.YP,LRDU.Up,style.heads[0])
        val after=style.worldPoint(Vec3.ZERO,Direction.YP,LRDU.Up,style.heads[0].add(e.suspension.drop,0.0,0.0))
        assertEquals(before.y-6.75,after.y,1e-6);assertEquals(before.x,after.x,1e-6);assertEquals(before.z,after.z,1e-6)
    }
    @Test fun bothSubstationRatiosFollowEachPhaseAndConservePowerIncludingCopperLoss() {
        for(kind in listOf(TransmissionKind.STEP_69,TransmissionKind.STEP_230)) {
            val e=transmission(kind);val root=RootSystem(.01,1)
            e.electricalLoadList.forEach(root::addState);e.electricalComponentList.forEach(root::addComponent)
            val sources=e.inputs.mapIndexed { i,p->VoltageSource("phase$i",p,null).apply { voltage=kind.inputVolts*(.9+i*.1) }.also(root::addComponent) }
            val resistance=kind.outputVolts*kind.outputVolts/(kind.wattsPerPhase*.5)
            val loads=e.outputs.map { Resistor(it,null).apply { this.resistance=resistance }.also(root::addComponent) }
            repeat(4) { root.step() }
            val winding=kind.outputVolts*kind.outputVolts/kind.wattsPerPhase*.005
            for(i in 0..2) {
                val ideal=sources[i].voltage*kind.ratio
                assertEquals(ideal/(1+(winding+.002+.002*kind.ratio*kind.ratio)/resistance),e.outputs[i].voltage,1e-5)
                val supplied=kotlin.math.abs(sources[i].voltage*sources[i].current)
                val expected=loads[i].power+loads[i].current*loads[i].current*(winding+.002+.002*kind.ratio*kind.ratio)
                assertEquals(expected,supplied,1e-4)
            }
            sources.forEach { it.voltage*=.95 };root.step()
            for(i in 0..2)assertEquals(sources[i].voltage*kind.ratio/(1+(winding+.002+.002*kind.ratio*kind.ratio)/resistance),e.outputs[i].voltage,1e-5)
        }
    }
    @Test fun disconnectOpensAllPhasesAndPersistsWithoutChangingItsTerminalIdentity() {
        for(kind in listOf(TransmissionKind.SWITCH_69,TransmissionKind.SWITCH_230)) {
            val e=transmission(kind);val root=RootSystem(.01,1)
            e.electricalLoadList.forEach(root::addState);e.electricalComponentList.forEach(root::addComponent)
            e.inputs.forEachIndexed { i,p->root.addComponent(VoltageSource("phase$i",p,null).apply { voltage=kind.inputVolts }) }
            e.outputs.forEach { root.addComponent(Resistor(it,null).apply { resistance=100_000.0 }) }
            root.step();assertTrue(e.outputs.all { it.voltage>kind.inputVolts*.99 })
            e.operate(true);root.step();assertTrue(e.outputs.all { kotlin.math.abs(it.voltage)<1e-8 })
            val copy=transmission(kind).also { it.readItemStackNBT(e.getItemStackNBT()) }
            assertFalse(copy.primaryClosed);assertEquals(3,copy.gridLoads(52).size);assertEquals(3,copy.gridLoads(53).size)
            assertTrue(e.inputs.all { it.voltage==kind.inputVolts })
        }
    }
    @Test fun risersHaveThreeSeparateBaseFeedsAndLargerTowersKeepTheirRatedGeometry() {
        for(kind in TransmissionKind.entries) {
            val e=transmission(kind)
            assertEquals(3,e.gridLoads(52).size)
            assertEquals(if(kind.station)3 else 0,e.gridLoads(53).size)
            if(kind.riser) {
                assertSame(e.inputs[0],e.getElectricalLoad(e.front.left(),LRDU.Down))
                assertSame(e.inputs[1],e.getElectricalLoad(e.front.right(),LRDU.Down))
                assertSame(e.inputs[2],e.getElectricalLoad(e.front.inverse,LRDU.Down))
            } else assertNull(e.getElectricalLoad(e.front.left(),LRDU.Down))
            val model=Obj3D().apply { assertTrue(loadFile("${kind.model}/${kind.model}.obj")) }
            assertNotNull(model.getPart("main"));assertTrue(model.yDim>=kind.height-.8,kind.name)
        }
        assertTrue(TransmissionKind.POLE_230.height>TransmissionKind.POLE_69.height)
        assertTrue(TransmissionKind.POLE_230.spread>TransmissionKind.POLE_69.spread)
    }
    @Test fun singlePhasePoleWireEndsOnTheNewTopInsulator() {
        val model=Obj3D().apply { assertTrue(loadFile("distributionpole/distributionpole.obj")) }
        val top=model.getPart("TopPin").boundingBox()
        for(kind in DistributionKind.entries.filter { it.phases==1 && !it.underground }) {
            val descriptor=Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>().single { it.kind==kind }
            val e=DistributionPoleElement(TransparentNode().apply { coordinate=Coordinate(0,64,0,0) },descriptor)
            assertEquals(top.max.y,e.gridCablePoint(32,0).y,1e-5)
        }
    }
    @Test fun dryerConnectorUsesTheThreeProngTenThirtyPattern() {
        assertEquals("NEMA 10-30P",CordRating.V240_30.plug);assertEquals("NEMA 10-30R",CordRating.V240_30.socket)
        val model=Obj3D().apply { assertTrue(loadFile("nema1030plug/nema1030plug.obj")) }
        assertNotNull(model.getPart("Blades"));assertNotNull(model.getPart("Case"));assertNotNull(model.getPart("Grip"))
    }
}
