package mods.eln.lighting

import mods.eln.Eln
import mods.eln.misc.*
import mods.eln.node.*
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.TransparentNode
import mods.eln.sixnode.electricalcable.ElectricalCableElement
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import mods.eln.sixnode.electricalcable.UtilityCableElement
import mods.eln.sixnode.electricalcable.UtilityCableMaterial
import mods.eln.transparentnode.cord.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.*

class OutletWiringAndCordPathTest {
    private var previous: NodeManager?=null
    private lateinit var manager: NodeManager
    @Before fun setup() { previous=NodeManager.instance;manager=NodeManager("outlet-wiring-test") }
    @After fun restore() { manager.clear();NodeManager.instance=previous }
    @Test fun everyRecessedOutletRatingAcceptsSurfaceCablesOnAllFourEdgesAndFacings() {
        for(d in Eln.transparentNodeItem.subItemList.values.filterIsInstance<OutletDescriptor>().filterNot { it.strip }) {
            for(front in listOf(Direction.XN,Direction.XP,Direction.ZN,Direction.ZP)) {
                manager.clear()
                val node=TransparentNode().apply { coordinate=Coordinate(0,80,0,0) }
                val outlet=OutletElement(node,d).apply {
                    this.front=front
                    readItemStackNBT(CompoundTag().apply { put("outletWall",net.minecraft.nbt.NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState())) })
                }
                node.element=outlet;manager.addNode(node)
                val utility=UtilityCableDescriptor.allDescriptors().first { !it.melted && it.material==UtilityCableMaterial.COPPER && it.insulated }
                for(edge in LRDU.values())for(descriptor in listOf(Eln.instance.meduimVoltageCableDescriptor,utility)) {
                    val p=node.coordinate.moved(front).moved(front.applyLRDU(edge))
                    val wire=SixNode().apply { coordinate=p }
                    wire.sideElementList[front.inverse.int]=if(descriptor is UtilityCableDescriptor)UtilityCableElement(wire,front.inverse,descriptor) else ElectricalCableElement(wire,front.inverse,descriptor)
                    manager.addNode(wire)
                    val endpoint=assertNotNull(OutletWiring.surfaceEndpoint(outlet,edge))
                    assertSame(wire,endpoint.node);assertEquals(front.inverse,endpoint.elementSide)
                    assertSame(outlet.input,outlet.getElectricalLoad(front,edge,endpoint))
                    assertEquals(NodeBase.maskElectricalPower,outlet.getConnectionMask(front,edge))
                    // A machine occupying the face cell must never obtain an implicit plug connection.
                    val other=TransparentNode().apply { coordinate=node.coordinate.moved(front) }
                    assertNull(outlet.getElectricalLoad(front,edge,other.getConnectionEndpoint(front.inverse,edge)))
                    outlet.wireless=true
                    assertNull(outlet.getElectricalLoad(front,edge,endpoint));assertEquals(0,outlet.getConnectionMask(front,edge))
                    outlet.wireless=false
                    // Surface-mounted outlets retain their original back-plane connections.
                    val plain=OutletElement(TransparentNode(),d).apply { this.front=front }
                    val lateral=front.applyLRDU(edge)
                    assertSame(plain.input,plain.getElectricalLoad(lateral,lateral.getLRDUGoingTo(front.inverse)!!))
                }
            }
        }
    }
    @Test fun obstacleClimbsStartBeforeTheEdgeAndNeverCutBelowTheirSupports() {
        fun ground(x: Double)=if(x in 2.0..3.0)1.0 else 0.0
        val a=Vec3(0.0,1.25,0.0);val b=Vec3(6.0,1.25,0.0)
        val path=CordPath.samples(a,b) { x,_,_ -> ground(x) }
        assertEquals(a,path.first());assertEquals(b,path.last())
        assertTrue(path.drop(1).dropLast(1).all { it.y>=ground(it.x)+.025-1e-9 })
        assertTrue(path.zipWithNext().all { (p,q)->kotlin.math.abs(p.y-q.y)<.4 })
        assertTrue(path.any { it.x in 1.6..1.99 && it.y>.7 })
        assertTrue(path.any { it.x in 3.01..3.4 && it.y>.7 })
        assertTrue(path.size<=65)
    }
    @Test fun floorSlabsVerticalDropsAndInvalidHeightSamplesStayFinite() {
        for(end in listOf(Vec3(0.0,0.0,0.0),Vec3(4.0,0.55,0.0))) {
            val a=Vec3(0.0,2.0,0.0)
            val path=CordPath.samples(a,end) { _,_,_ -> Double.NaN }
            assertEquals(a,path.first());assertEquals(end,path.last())
            assertTrue(path.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
        }
        val slab=CordPath.samples(Vec3(0.0,.75,0.0),Vec3(5.0,.75,0.0)) { _,_,_ -> .5 }
        assertTrue(slab.drop(1).dropLast(1).all { it.y>=.525-1e-9 })
    }
    @Test fun sharedTubeRingsKeepTheirRadiusAndDoNotFlipAtNearVerticalBends() {
        val path=listOf(Vec3.ZERO,Vec3(.4,.85,0.0),Vec3(.8,1.9,.02),Vec3(.9,2.9,.03))
        val rings=CordPath.rings(path,.018)
        rings.forEachIndexed { i,ring -> ring.forEach { assertEquals(.018,it.distanceTo(path[i]),1e-9) } }
        for(i in 1 until rings.size)assertTrue(rings[i][0].subtract(path[i]).dot(rings[i-1][0].subtract(path[i-1]))>0)
        val duplicate=CordPath.rings(listOf(Vec3.ZERO,Vec3.ZERO),.018)
        assertTrue(duplicate.flatten().all { it.length().isFinite() })
        val straight=CordPath.rings(listOf(Vec3.ZERO,Vec3(1.0,0.0,0.0)),.018)
        val a=straight[0][0];val b=straight[0][1];val c=straight[1][1]
        assertTrue(b.subtract(a).cross(c.subtract(a)).dot(a.add(straight[0][1]))>0)
    }
}
