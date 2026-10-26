package mods.eln.gridnode

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeDescriptor
import mods.eln.sixnode.electricalcable.*
import mods.eln.utility.*
import net.minecraft.world.phys.Vec3
import org.junit.Test
import java.io.*
import java.util.Optional
import kotlin.math.*
import kotlin.test.*

class ServiceCableClearanceTest {
    private fun pole(x: Int,z: Int,watts: Int?=null): GridElement {
        val kind=if(watts==null)DistributionKind.SECONDARY else DistributionKind.TRANSFORMER_THREE
        val descriptor=Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>().first { it.kind==kind }
        val node=TransparentNode().apply { coordinate=Coordinate(x,80,z,0) }
        return (descriptor.ElementClass.getConstructor(TransparentNode::class.java,TransparentNodeDescriptor::class.java)
            .newInstance(node,descriptor) as GridElement).also {
            node.element=it
            if(watts!=null) { it.inventory!!.setItem(0,Eln.findItemStack(PoleHardware.transformerNames[listOf(10_000,25_000,50_000,75_000,100_000).indexOf(watts)],1));it.inventoryChange(it.inventory) }
        }
    }
    private fun wire(size: String="1000 kcmil")=UtilityCableDescriptor.allDescriptors().first {
        it.sizeLabel==size && it.material==UtilityCableMaterial.ALUMINUM && it.insulated && !it.melted
    }
    private fun link(a: GridElement,b: GridElement,cable: ElectricalCableDescriptor=wire()): GridLink {
        return GridLink(a.coordinate(),b.coordinate(),50,50,GridLink.linkStack(cable,144,null),.01).also { l->
            for((field,e)in listOf("ae" to a,"be" to b))GridLink::class.java.getDeclaredField(field).apply { isAccessible=true }.set(l,Optional.of(e))
            a.gridLinkList+=l;b.gridLinkList+=l
        }
    }
    private fun angle(e: GridElement,lv: Float,hv: Float=lv) {
        GridElement::class.java.getDeclaredField("lowVoltageRenderAngle").apply { isAccessible=true }.setFloat(e,lv)
        GridElement::class.java.getDeclaredField("highVoltageRenderAngle").apply { isAccessible=true }.setFloat(e,hv)
    }
    private fun distance(a: Vec3,b: Vec3,p: Vec3): Double {
        val dx=b.x-a.x;val dz=b.z-a.z;val d=dx*dx+dz*dz
        val t=if(d<1e-16)0.0 else (((p.x-a.x)*dx+(p.z-a.z)*dz)/d).coerceIn(0.0,1.0)
        return hypot(a.x+dx*t-p.x,a.z+dz*t-p.z)
    }
    private fun checkClear(span: OverheadSpan) {
        for(obstacle in listOfNotNull(span.startPole,span.endPole))for(w in OverheadSpanMesh.geometry(span)) {
            val clearance=w.points.zipWithNext().minOf { (a,b)->distance(a,b,obstacle.center)-sqrt(2.0)*w.radius-obstacle.radius }
            assertTrue(clearance>=-1e-5,"Cable intersects pole/can by ${-clearance}: $span")
        }
    }
    @Test fun oldAlongLineSupportReproducesTheWoodIntersectionAndTheNewMountedRunClearsIt() {
        val style=OverheadCableAppearance.of(wire())
        val old=OverheadSpan(List(3) { i->OverheadSpanMesh.terminal(Vec3(.405,4.97,0.0),i,style.outsideRadius) to OverheadSpanMesh.terminal(Vec3(-23.595,4.97,0.0),i,style.outsideRadius) },style,true)
        assertTrue(OverheadSpanMesh.conductors(old).any { w->w.points.zipWithNext().any { (a,b)->distance(a,b,Vec3.ZERO)<.145+sqrt(2.0)*w.radius } })
        val host=pole(0,0);val west=pole(-24,0);val east=pole(24,0)
        checkClear(OverheadSpanPublication.span(link(host,west)));checkClear(OverheadSpanPublication.span(link(host,east)))
    }
    @Test fun thickTriplexClearsWoodAndEveryCanRatingThroughCornersAndIndependentHvLvRotations() {
        for(watts in listOf<Int?>(null,10_000,25_000,50_000,75_000,100_000))for((dx,dz)in listOf(24 to 0,-24 to 0,0 to 24,0 to -24,24 to 24,-24 to 24)) {
            val a=pole(0,0,watts);val b=pole(dx,dz,watts)
            val l=link(a,b)
            for(rotation in listOf(0f,45f,90f,180f,270f)) {
                angle(a,rotation,rotation+90);angle(b,rotation+180,rotation+45)
                checkClear(OverheadSpanPublication.span(l))
            }
        }
    }
    @Test fun serviceSplicesStayOnTheRoutedMainAndTheDropClearsTheTransformer() {
        val host=pole(0,0,100_000);val east=pole(24,0)
        val descriptor=Eln.transparentNodeItem.subItemList.values.filterIsInstance<GridDescriptor>().first { it.name=="120/240V Service Weatherhead" }
        val node=TransparentNode().apply { coordinate=Coordinate(-12,80,12,0) }
        val mast=(descriptor.ElementClass.getConstructor(TransparentNode::class.java,TransparentNodeDescriptor::class.java).newInstance(node,descriptor) as GridElement).also { node.element=it }
        val main=link(host,east);val drop=link(host,mast,wire("2 AWG"))
        val span=OverheadSpanPublication.span(drop);checkClear(OverheadSpanPublication.span(main));checkClear(span)
        assertNotNull(span.startSplice)
        val mainWires=OverheadSpanMesh.conductors(OverheadSpanPublication.span(main))
        for(i in 0..2)assertTrue(mainWires[i].points.any { it.distanceTo(span.ends[i].first)<1e-9 })
    }
    @Test fun originalCreativeCableAlsoGetsEnoughPhysicalStandoff() {
        val cable=Eln.sixNodeItem.subItemList.values.filterIsInstance<ElectricalCableDescriptor>().filter { !it.signalWire }
            .maxBy { OverheadCableAppearance.of(it).outsideRadius }
        val a=pole(0,0,100_000);val b=pole(-24,0,100_000);val l=link(a,b,cable)
        assertTrue(a.serviceMountReach>ServiceCableMount.MIN_REACH)
        checkClear(OverheadSpanPublication.span(l))
    }
    @Test fun poleAndCanEnvelopesSurviveTheSpanPacket() {
        val a=pole(0,0,100_000);val b=pole(24,24,25_000)
        val original=OverheadSpanPublication.span(link(a,b))
        val bytes=ByteArrayOutputStream().also { original.write(DataOutputStream(it)) }.toByteArray()
        val restored=OverheadSpan.read(DataInputStream(ByteArrayInputStream(bytes)))
        assertEquals(original.startPole!!.radius,restored.startPole!!.radius,1e-6)
        assertTrue(original.endPole!!.center.distanceTo(restored.endPole!!.center)<1e-6)
        checkClear(restored)
    }
    @Test fun visibilityGraphRoutesAReversedCrossingAndLeavesAnUnobstructedCatenaryUnchanged() {
        val start=Vec3(1.0,5.0,0.0);val end=Vec3(-12.0,4.0,0.0)
        val p=OverheadCableMesh.points(start,end)
        val obstacle=ServiceCableClearance(Vec3.ZERO,.65)
        val routed=ServiceCablePath.route(p,listOf(obstacle),.05)
        assertEquals(start,routed.first());assertEquals(end,routed.last())
        assertTrue(routed.zipWithNext().all { (a,b)->distance(a,b,Vec3.ZERO)>=.7-1e-7 })
        val reverse=ServiceCablePath.route(p.asReversed(),listOf(obstacle),.05)
        assertTrue(reverse.zipWithNext().all { (a,b)->distance(a,b,Vec3.ZERO)>=.7-1e-7 })
        val clear=OverheadCableMesh.points(Vec3(0.0,5.0,2.0),Vec3(12.0,5.0,2.0))
        assertSame(clear,ServiceCablePath.route(clear,listOf(obstacle),.05))
    }
    @Test fun hardwareChangesPublishBothThePoleAndItsNeighbourOwnedSpan() {
        val a=pole(0,0,25_000);val b=pole(24,0,100_000)
        link(a,b)
        a.node!!.needPublish=false;b.node!!.needPublish=false
        b.publishServiceGeometry { if(it==a.coordinate())a else null }
        assertTrue(a.node!!.needPublish && b.node!!.needPublish)
    }
}
