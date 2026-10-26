package mods.eln.utility

import mods.eln.gridnode.ServiceCableMount
import net.minecraft.world.phys.Vec3
import org.junit.Test
import kotlin.test.*
import kotlin.math.*

class PoleHardwareGeometryTest {
    @Test fun allFiveCanRatingsIncreaseInDiameterHeightAndVolumeWithFixedBushingHeight() {
        val shapes=listOf(10_000,25_000,50_000,75_000,100_000).map(PoleTransformerShape::of)
        for((a,b)in shapes.zipWithNext()) {
            assertTrue(b.diameter>a.diameter && b.height>a.height)
            assertTrue(b.diameter*b.diameter*b.height>a.diameter*a.diameter*a.height)
            assertEquals(a.bushing.y,b.bushing.y,0.0)
        }
        for(s in shapes) {
            assertEquals(PoleTransformerShape.BACK,s.point(Vec3(-.015,5.535,0.0)).x,1e-10)
            assertTrue(PoleTransformerShape.BACK<-.145)
            assertEquals(s.bushing.x,s.point(Vec3(-.33,5.535,0.0)).x,1e-10)
        }
    }
    @Test fun scaledCanSecondaryLeadsExitTheCaseAndClearBothCaseAndWood() {
        for(w in listOf(10_000,25_000,50_000,75_000,100_000))for(angle in listOf(0f,45f,90f,180f,270f)) {
            val s=PoleTransformerShape.of(w);val shield=s.clearance(angle)
            val normal=Vec3(0.0,0.0,-1.0).yRot(Math.toRadians(angle.toDouble()).toFloat())
            val reach=ServiceCableMount.reach(.092,shield,angle)
            val target=normal.scale(reach).add(0.0,4.97,0.0)
            for(i in 0..2) {
                val lead=s.secondaryLead(i,angle,target)
                assertEquals(target,lead.last())
                val tank=Vec3(s.bushing.x,0.0,0.0).yRot(Math.toRadians(angle.toDouble()).toFloat())
                for((a,b)in lead.zipWithNext())for(k in 0..16) {
                    val p=a.add(b.subtract(a).scale(k/16.0))
                    assertTrue(hypot(p.x,p.z)>.27)
                    assertTrue(hypot(p.x-tank.x,p.z-tank.z)>.315*s.diameter)
                }
            }
        }
    }
    @Test fun cutoutHolderJawStaysFixedWhileTheCarrierDropsFromItsLowerHinge() {
        assertTrue(PoleCutoutPose.top(true).distanceTo(Vec3(-.83,6.40,0.0))<1e-6)
        val open=PoleCutoutPose.top(false)
        assertTrue(open.y<PoleCutoutPose.hinge.y && open.x<PoleCutoutPose.hinge.x)
        assertEquals(.55,open.distanceTo(PoleCutoutPose.hinge),1e-6)
    }
}
