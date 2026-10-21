package mods.eln.lighting

import mods.eln.Eln
import mods.eln.item.HeatingCorpElement
import mods.eln.misc.*
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.*
import mods.eln.sixnode.batterycharger.*
import mods.eln.transparentnode.cord.*
import mods.eln.transparentnode.electricalfurnace.ElectricalFurnaceElement
import mods.eln.transparentnode.electricalmachine.*
import mods.eln.mechanical.*
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import java.util.UUID
import kotlin.test.*

class MachineCordTest {
    private fun element(d: TransparentNodeDescriptor): TransparentNodeElement = d.ElementClass.getConstructor(TransparentNode::class.java,TransparentNodeDescriptor::class.java).newInstance(TransparentNode(),d) as TransparentNodeElement
    private fun plug(e: TransparentNodeElement,rating: CordRating) {
        e.machineCord!!.cord.read(CompoundTag().apply { put("applianceCord",CordAnchor(0,BlockPos.ZERO,UUID.randomUUID(),Direction.ZP,rating).write()) })
    }
    @Test fun workshopMachinesFeedTheirExistingInputAndIsolatePlacedCablesUntilUnplugged() {
        val descriptors=Eln.transparentNodeItem.subItemList.values.filterIsInstance<ElectricalMachineDescriptor>()
        assertTrue(descriptors.size>=9)
        for(d in descriptors) {
            val e=element(d);val device=assertNotNull(CordDevices.get(e));assertSame(device,CordDevices.get(e))
            val r=CordRating.entries.single { it.accepts(d.nominalU) }
            assertTrue(device.acceptsCord(r));CordRating.entries.filter { it!=r }.forEach { assertFalse(device.acceptsCord(it)) }
            val inlet=e.getElectricalLoad(e.front,LRDU.Down)
            assertSame(inlet,e.machineCord!!.input)
            val side=Direction.values().first { e.getConnectionMask(it,LRDU.Down)!=0 }
            e.node!!.element=e
            assertNotNull(e.node!!.getElectricalLoad(side,LRDU.Down,0))
            plug(e,r);assertNull(e.node!!.getElectricalLoad(side,LRDU.Down,0));assertEquals(0,e.node!!.getSideConnectionMask(side,LRDU.Down))
            val saved=CompoundTag().also(e::writeToNBT);val copy=element(d).also { it.readFromNBT(saved) }
            assertEquals(r,copy.machineCord!!.cord.anchor!!.rating)
            device.cord.read(CompoundTag());assertNotNull(e.node!!.getElectricalLoad(side,LRDU.Down,0))
        }
    }
    @Test fun chargersAndBothMotorTiersAcceptOnlyTheirOwnRatedConnector() {
        for(d in Eln.sixNodeItem.subItemList.values.filterIsInstance<BatteryChargerDescriptor>()) {
            val e=BatteryChargerElement(SixNode(),Direction.YN,d);val c=assertNotNull(CordDevices.get(e))
            assertSame(e.powerLoad,c.cord.input);assertTrue(c.acceptsCord(CordRating.entries.single { it.accepts(d.nominalVoltage) }))
        }
        for(d in Eln.transparentNodeItem.subItemList.values.filterIsInstance<MotorDescriptor>()) {
            val e=element(d);val c=assertNotNull(CordDevices.get(e));assertTrue(c.acceptsCord(CordRating.entries.single { it.accepts(d.nominalU.toDouble()) }))
            assertSame(e.getElectricalLoad(e.front,LRDU.Down),c.cord.input)
        }
    }
    @Test fun furnaceConnectorFollowsInstalledHeatingElementWithoutConvertingVoltage() {
        val d=Eln.transparentNodeItem.subItemList.values.filterIsInstance<TransparentNodeDescriptor>().single { it.ElementClass==ElectricalFurnaceElement::class.java }
        val e=element(d);val c=assertNotNull(CordDevices.get(e))
        assertTrue(CordRating.entries.none(c::acceptsCord))
        for(heater in Eln.sharedItem.subItemList.values.filterIsInstance<HeatingCorpElement>()) {
            e.inventory!!.setItem(ElectricalFurnaceElement.heatingCorpSlotId,heater.newItemStack(1))
            val matching=CordRating.entries.single { it.accepts(heater.electricalNominalU) }
            assertTrue(c.acceptsCord(matching));assertEquals(heater.electricalNominalU,c.cordVoltage)
        }
    }
    @Test fun thickCopperCableStopsOutsideTheBreakerCaseInEveryOrientation() {
        val obj=Obj3D().apply { assertTrue(loadFile("electricalbreaker/electricalbreaker.obj")) }
        val body=assertNotNull(obj.getPart("case"))
        val wire=mods.eln.sixnode.electricalcable.UtilityCableDescriptor.allDescriptors().single { !it.melted && it.material==mods.eln.sixnode.electricalcable.UtilityCableMaterial.COPPER && it.sizeLabel=="1/0 AWG" && it.insulated && it.insulationVoltageRating==1000.0 }
        assertTrue(wire.render.height>0)
        for(front in LRDU.values()) {
            val ends=mods.eln.sixnode.electricalbreaker.BreakerCableEnds.starts(body,front)
            val points=listOf(body.yMin to body.zMin,body.yMin to body.zMax,body.yMax to body.zMin,body.yMax to body.zMax).map { (y,z)->front.rotateOnXnLeft(net.minecraft.world.phys.Vec3(0.0,y.toDouble(),z.toDouble())) }
            assertTrue(-ends[0]<points.minOf { it.z });assertTrue(ends[1]>points.maxOf { it.z })
            assertTrue(-ends[2]<points.minOf { it.y });assertTrue(ends[3]>points.maxOf { it.y })
            assertTrue(ends.all { it<.5f })
        }
    }
    @Test fun industrialRegistrationPreservesHouseholdIdentitiesAndHasRealCouplerMeshes() {
        assertEquals(listOf(CordRating.V120,CordRating.V240),CordRating.entries.take(2))
        CordRating.entries.filter { it.industrial }.forEachIndexed { i,r ->
            assertFalse(Eln.findItemStack(r.cordName,1).isEmpty)
            val d=Eln.transparentNodeItem.subItemList[(125 shl 6)+i] as OutletDescriptor
            assertEquals(r,d.rating);assertEquals(2,d.sockets);assertFalse(r.plug.startsWith("NEMA"))
        }
        for(name in listOf("industrialmachineplug","industrialmachineoutlet")) {
            val m=Obj3D().apply { assertTrue(loadFile("$name/$name.obj")) }
            for(part in if(name.endsWith("plug"))listOf("Case","Grip","Blades","Badge") else listOf("Case","Trim","Black"))assertNotNull(m.getPart(part))
        }
        // The switch has no front cover, and its lever stays outside the sidewall in ON and OFF.
        val vertices=javaClass.classLoader.getResourceAsStream("assets/eln/model/redhandleservicedisconnect/redhandleservicedisconnect.obj")!!.bufferedReader().useLines { lines ->
            var handle=false;lines.mapNotNull { line -> if(line.startsWith("o "))handle=line=="o Handle";if(handle && line.startsWith("v "))line.drop(2).split(" ").map(String::toDouble) else null }.toList()
        }
        assertTrue(vertices.isNotEmpty());assertTrue(vertices.all { it[0]>.31 })
        assertTrue(vertices.maxOf { it[1] }>.25)
    }
}
