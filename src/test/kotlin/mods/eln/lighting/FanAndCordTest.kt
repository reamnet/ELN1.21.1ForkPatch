package mods.eln.lighting

import mods.eln.Eln
import mods.eln.misc.Direction
import mods.eln.transparentnode.fan.*
import mods.eln.transparentnode.cord.*
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.*
import org.junit.Test
import java.util.UUID
import kotlin.test.*

class FanAndCordTest {
    @Test fun rotorTakesTimeToStartAndCoastsAfterPowerLoss() {
        val r=FanDynamics()
        assertEquals(.01,r.step(.05,1.0),1e-10)
        repeat(99) { r.step(.05,1.0) };assertEquals(1.0,r.speed,1e-10)
        assertEquals(.9,r.step(1.0,0.0),1e-10)
        assertEquals(.5,r.step(4.0,0.0),1e-10)
        assertEquals(.7,r.step(1.0,1.0),1e-10)
        r.step(7.0,0.0);assertEquals(0.0,r.speed)
    }
    @Test fun inertiaIsIndependentOfSimulationStepSizeAndSurvivesSaves() {
        val a=FanDynamics(12.0,20.0);val b=FanDynamics(12.0,20.0)
        repeat(120) { a.step(.05,1.0) };b.step(6.0,1.0);assertEquals(b.speed,a.speed,1e-12)
        val c=FanDynamics(12.0,20.0).also { it.read(CompoundTag().also(a::write)) }
        assertEquals(a.speed,c.speed);a.step(3.0,0.0);c.step(3.0,0.0);assertEquals(a.speed,c.speed)
        for(dt in listOf(Double.NaN,Double.POSITIVE_INFINITY,-1.0,0.0)) { val before=c.speed;c.step(dt,1.0);assertEquals(before,c.speed) }
        c.read(CompoundTag().apply { putDouble("fanRotorSpeed",Double.NaN) });assertEquals(0.0,c.speed)
    }
    @Test fun visualRotorKeepsMovingWithItsRemainingKineticSpeed() {
        val v=FanVisual();repeat(100) { v.step(.05f,1.0) };val angle=v.angle
        repeat(10) { v.step(.05f,.9) };assertTrue(v.speed>.89f);assertNotEquals(angle,v.angle)
        repeat(20) { v.step(.05f,0.0) };assertEquals(0f,v.speed)
        val stopped=v.angle;v.step(.05f,0.0);assertEquals(stopped,v.angle)
    }
    @Test fun bladeContactIsACircleAndAThinPlaneAtEveryFacing() {
        val c=Vec3(10.5,82.4,10.5)
        for(front in listOf(Direction.XN,Direction.XP,Direction.ZN,Direction.ZP)) {
            assertTrue(FanBlades.touches(AABB(c.add(-.2,-.4,-.2),c.add(.2,.4,.2)),c,front,2.28))
            val n=if(front==Direction.XN || front==Direction.XP)Vec3(1.0,0.0,0.0) else Vec3(0.0,0.0,1.0)
            val behind=c.add(n.scale(1.0));assertFalse(FanBlades.touches(AABB(behind,behind).inflate(.2),c,front,2.28))
            val outside=c.add(0.0,2.7,0.0);assertFalse(FanBlades.touches(AABB(outside,outside).inflate(.1),c,front,2.28))
        }
    }
    @Test fun cordsPreserveTheOutletIdentityAndRejectMalformedSavedLinks() {
        val a=CordAnchor(0,BlockPos(5,80,-9),UUID.randomUUID(),Direction.ZP)
        assertEquals(a,CordAnchor.read(a.write()))
        assertNull(CordAnchor.read(CompoundTag()))
        assertNull(CordAnchor.read(a.write().apply { putInt("facing",Direction.YP.int) }))
        assertNotEquals(a.id,CordAnchor(0,a.pos,UUID.randomUUID(),a.front).id)
    }
    @Test fun looseCordsFollowGroundAndKeepBothEndpoints() {
        val a=Vec3(0.0,1.5,0.0);val b=Vec3(6.0,1.05,0.0)
        val points=CordPath.samples(a,b) { _,_,_ -> 1.0 }
        assertEquals(a,points.first());assertEquals(b,points.last())
        assertTrue(points.drop(1).dropLast(1).all { it.y>=1.025 && it.y<=1.5 })
        assertTrue(points.any { it.z>.1 });assertTrue(points.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
        assertTrue(CordPath.samples(Vec3(Double.NaN,0.0,0.0),b) { _,_,_ -> 0.0 }.isEmpty())
    }
    @Test fun fanModelsContainActualRotorMeshesAndNewIdsAreAppended() {
        for(t in FanType.entries) {
            val d=Eln.transparentNodeItem.subItemList[28+t.ordinal+(124 shl 6)] as FanDescriptor
            assertEquals(t,d.type)
            val m=mods.eln.misc.Obj3D().apply { assertTrue(loadFile("${t.model}/${t.model}.obj")) }
            assertNotNull(m.getPart("Rotor"));assertNotNull(m.getPart("Case"))
            if(t.industrial) { assertTrue(m.xDim>4.5);assertTrue(d.ghostGroup!!.elementList.all { it.meta==mods.eln.ghost.GhostBlock.tClearance }) }
        }
        assertTrue(Eln.transparentNodeItem.subItemList[32+(124 shl 6)] is OutletDescriptor)
        assertTrue(Eln.transparentNodeItem.subItemList[27+(124 shl 6)] is mods.eln.transparentnode.kitchen.KitchenDescriptor)
        assertTrue(Eln.sharedItem.getDescriptor(Eln.findItemStack("Flexible Appliance Cord",1)) is CordDescriptor)
    }
}
