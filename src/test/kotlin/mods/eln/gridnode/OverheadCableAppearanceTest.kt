package mods.eln.gridnode

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.UserError
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeDescriptor
import mods.eln.sixnode.electricalcable.*
import mods.eln.utility.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.Vec3
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.*
import java.util.Optional
import kotlin.math.sqrt
import kotlin.test.*

class OverheadCableAppearanceTest {
    private fun cable(size: String="2 AWG",material: UtilityCableMaterial=UtilityCableMaterial.COPPER,insulated: Boolean=false)=
        UtilityCableDescriptor.allDescriptors().first { it.sizeLabel==size && it.material==material && it.insulated==insulated && !it.melted }
    private fun pole(name: String,x: Int,z: Int): GridElement {
        val descriptor=Eln.transparentNodeItem.subItemList.values.filterIsInstance<GridDescriptor>().first { it.name==name }
        val node=TransparentNode().apply { coordinate=Coordinate(x,80,z,0) }
        return (descriptor.ElementClass.getConstructor(TransparentNode::class.java,TransparentNodeDescriptor::class.java)
            .newInstance(node,descriptor) as GridElement).also { node.element=it }
    }
    private fun link(a: GridElement,b: GridElement,cable: UtilityCableDescriptor,from: Int=50,to: Int=50): GridLink {
        val stack=cable.newItemStack().also { cable.setRemainingLengthMeters(it,48.0) }
        return GridLink(a.coordinate(),b.coordinate(),from,to,stack,cable.resistanceOhms(16.0)).also { l->
            for((field,element)in listOf("ae" to a,"be" to b))GridLink::class.java.getDeclaredField(field).apply { isAccessible=true }.set(l,Optional.of(element))
            a.gridLinkList+=l;b.gridLinkList+=l
        }
    }
    @Test fun diameterUsesActualConductorAreaAndMetalRatherThanFixedPoleThickness() {
        val bare=UtilityCableDescriptor.allDescriptors().filter { !it.insulated && !it.melted }.sortedBy { it.conductorAreaMm2 }
        assertTrue(bare.size>10)
        for((small,large)in bare.zipWithNext()) {
            val a=OverheadCableAppearance.of(small);val b=OverheadCableAppearance.of(large)
            assertEquals(sqrt(large.conductorAreaMm2/small.conductorAreaMm2),b.radius/a.radius,1e-10)
            assertTrue(b.radius>=a.radius)
        }
        val copper=OverheadCableAppearance.of(cable())
        val aluminum=OverheadCableAppearance.of(cable(material=UtilityCableMaterial.ALUMINUM))
        assertEquals(copper.radius,aluminum.radius,1e-10)
        assertEquals(OverheadCableAppearance.COPPER,copper.metal)
        assertEquals(OverheadCableAppearance.ALUMINUM,aluminum.metal)
        val insulated=OverheadCableAppearance.of(cable(insulated=true))
        assertTrue(insulated.outsideRadius>copper.outsideRadius)
    }
    private fun bundle(end: Vec3=Vec3(0.0,0.0,48.0)): OverheadSpan {
        val style=OverheadCableAppearance.of(cable(insulated=true))
        return OverheadSpan(List(3) { OverheadSpanMesh.terminal(Vec3.ZERO,it,style.outsideRadius) to OverheadSpanMesh.terminal(end,it,style.outsideRadius) },style,true)
    }
    @Test fun triplexHasTwoBlackWoundConductorsAndAMetallicMessengerWithExactEnds() {
        for(end in listOf(Vec3(0.0,0.0,48.0),Vec3(32.0,12.0,-18.0),Vec3(0.0,24.0,0.0))) {
            val span=bundle(end);val wires=OverheadSpanMesh.conductors(span)
            assertEquals(3,wires.size)
            assertEquals(listOf(OverheadCableAppearance.JACKET,OverheadCableAppearance.JACKET,span.appearance.metal),wires.map { it.color })
            for(i in 0..2) {
                assertEquals(span.ends[i].first,wires[i].points.first())
                assertEquals(span.ends[i].second,wires[i].points.last())
                assertTrue(wires[i].points.size<=513)
                assertTrue(OverheadCableMesh.sections(wires[i].points,wires[i].radius).flatten().all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
            }
            assertTrue(wires[0].points.zip(wires[1].points).drop(4).dropLast(4).all { (a,b)->a.distanceTo(b)>span.appearance.outsideRadius*4 })
        }
    }
    @Test fun spanPacketRetainsGaugeMaterialJacketAndSplicesAndRejectsInvalidRadius() {
        val span=bundle().copy(startSplice=OverheadSplice(Vec3(0.0,-.1,1.0),.05))
        val bytes=ByteArrayOutputStream().also { span.write(DataOutputStream(it)) }.toByteArray()
        val restored=OverheadSpan.read(DataInputStream(ByteArrayInputStream(bytes)))
        assertEquals(span.triplex,restored.triplex);assertEquals(span.appearance.metal,restored.appearance.metal)
        assertEquals(span.appearance.radius,restored.appearance.radius,1e-8)
        assertEquals(span.startSplice!!.radius,restored.startSplice!!.radius,1e-8)
        assertEquals(OverheadSpanMesh.geometry(span).size,OverheadSpanMesh.geometry(restored).size)
        val bad=bytes.copyOf();val nan=java.nio.ByteBuffer.allocate(4).putFloat(Float.NaN).array();nan.copyInto(bad,1)
        assertThrows(IllegalArgumentException::class.java) { OverheadSpan.read(DataInputStream(ByteArrayInputStream(bad))) }
    }
    @Test fun newSecondaryLinksRequireInsulationInEitherOrderWithoutConsumingCable() {
        val a=pole(DistributionKind.SECONDARY.title,0,0);val b=pole(DistributionKind.SECONDARY.title,0,16)
        val bare=cable();val supplied=bare.newItemStack().also { bare.setRemainingLengthMeters(it,48.0) }
        for(service in listOf(40,41,42,50))for(other in listOf(service,32,52)) {
            assertThrows(UserError::class.java) { GridLink.validateServiceCable(bare,service,other) }
            assertThrows(UserError::class.java) { GridLink.validateServiceCable(bare,other,service) }
            GridLink.validateServiceCable(cable(insulated=true),service,other)
        }
        for((x,y)in listOf(a to b,b to a))assertThrows(UserError::class.java) { GridLink.addLink(x,y,50,50,bare,48,supplied) }
        assertEquals(48.0,bare.getRemainingLengthMeters(supplied),0.0)
        assertTrue(a.gridLinkList.isEmpty() && b.gridLinkList.isEmpty())
        GridLink.validateServiceCable(bare,52,52);GridLink.validateServiceCable(bare,32,32)
        GridLink.validateServiceCable(Eln.instance.lowVoltageCableDescriptor,50,50)
    }
    @Test fun savedBareServiceLinksRetainTheirCableAndUninsulatedAppearance() {
        val a=pole(DistributionKind.SECONDARY.title,0,0);val b=pole(DistributionKind.SECONDARY.title,0,16)
        val old=link(a,b,cable());val tag=CompoundTag();old.writeToNBT(tag,"")
        val restored=GridLink(tag,"")
        assertFalse(OverheadCableAppearance.of(checkNotNull(ElectricalCableDescriptor.getDescriptor(restored.cable) as? ElectricalCableDescriptor)).insulated)
        assertFalse(OverheadSpanPublication.span(old).triplex)
        val ends=OverheadSpanPublication.span(old).ends
        assertTrue(ends[0].first.distanceTo(ends[2].first)<.25)
        assertTrue(ends[0].second.distanceTo(ends[2].second)<.25)
        assertTrue(OverheadSpanMesh.conductors(OverheadSpanPublication.span(old)).all { it.color==OverheadCableAppearance.COPPER })
        assertEquals(48.0,cable().getRemainingLengthMeters(restored.cable),0.0)
    }
    @Test fun serviceDropSplicesMeetActualMainStrandsInEitherOwnershipOrderAndRotation() {
        for(reverseMain in listOf(false,true))for(reverseDrop in listOf(false,true))for(front in listOf(Direction.XN,Direction.XP,Direction.ZN,Direction.ZP)) {
            val host=pole(DistributionKind.THREE.title,0,0);val next=pole(DistributionKind.THREE.title,0,16)
            val mast=pole("120/240V Service Weatherhead",12,0)
            host.front=front;next.front=front.inverse;mast.front=front
            for((element,direction)in listOf(host to front,next to front.inverse,mast to front)) {
                for(field in listOf("lowVoltageRenderAngle","idealRenderingAngle"))GridElement::class.java.getDeclaredField(field).apply { isAccessible=true }.setFloat(element,PoleSpanAlignment.frontAngle(direction))
            }
            val main=if(reverseMain)link(next,host,cable(insulated=true)) else link(host,next,cable(insulated=true))
            val original=OverheadSpanPublication.span(main)
            assertNull(original.startSplice);assertNull(original.endSplice)
            val drop=if(reverseDrop)link(mast,host,cable(insulated=true)) else link(host,mast,cable(insulated=true))
            val beforeLoads=host.electricalLoadList.toList();val beforeComponents=host.electricalComponentList.toList()
            val spec=OverheadSpanPublication.span(drop)
            assertEquals(original,OverheadSpanPublication.span(main))
            assertNotNull(if(reverseDrop)spec.endSplice else spec.startSplice)
            val delta=main.a.subtract(drop.a);val translation=Vec3(delta.x.toDouble(),delta.y.toDouble(),delta.z.toDouble())
            val strands=OverheadSpanMesh.conductors(original)
            for(i in 0..2) {
                val point=if(reverseDrop)spec.ends[i].second else spec.ends[i].first
                assertTrue(strands[i].points.any { it.add(translation).distanceTo(point)<1e-9 })
            }
            assertEquals(beforeLoads,host.electricalLoadList);assertEquals(beforeComponents,host.electricalComponentList)
            assertEquals(3,host.gridLoads(50).distinct().size)
            host.gridLinkList.remove(main);next.gridLinkList.remove(main)
            val noMain=OverheadSpanPublication.span(drop)
            assertNull(noMain.startSplice);assertNull(noMain.endSplice)
        }
    }
    @Test fun repeatedServicePublicationsRefreshCachedMainGeometryAfterGaugeOrOrientationChanges() {
        val host=pole(DistributionKind.SECONDARY.title,0,0);val next=pole(DistributionKind.SECONDARY.title,16,0)
        val mast=pole("120/240V Service Weatherhead",0,12)
        val main=link(host,next,cable(insulated=true));val drop=link(host,mast,cable(insulated=true))
        val original=OverheadSpanPublication.span(drop)
        repeat(32) { assertEquals(original,OverheadSpanPublication.span(drop)) }
        val thick=cable("250 kcmil",insulated=true)
        main.cable=thick.newItemStack().also { thick.setRemainingLengthMeters(it,48.0) }
        val resized=OverheadSpanPublication.span(drop)
        assertTrue(resized.startSplice!!.radius>original.startSplice!!.radius)
        assertNotEquals(original.ends,resized.ends)
        val angle=GridElement::class.java.getDeclaredField("lowVoltageRenderAngle").apply { isAccessible=true }
        angle.setFloat(host,90f);angle.setFloat(next,90f)
        val rotated=OverheadSpanPublication.span(drop)
        assertNotEquals(resized.ends,rotated.ends)
        repeat(32) { assertEquals(rotated,OverheadSpanPublication.span(drop)) }
    }
}
