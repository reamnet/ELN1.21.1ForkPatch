package mods.eln.node

import mods.eln.misc.Direction
import net.minecraft.world.phys.AABB
import org.junit.Test
import kotlin.test.*

class SurfaceMountingTest {
    @Test fun mountingUsesCollisionFacesOnEverySideIncludingTransparentAndPartialBlocks() {
        val bottom=listOf(AABB(0.0,0.0,0.0,1.0,.5,1.0))
        val top=listOf(AABB(0.0,.5,0.0,1.0,1.0,1.0))
        assertEquals(.5,SurfaceMounting.surface(bottom,Direction.YN))
        assertEquals(.5,SurfaceMounting.surface(top,Direction.YP))
        for(side in Direction.entries) {
            val box=listOf(AABB(.2,.3,.4,.8,.7,.6))
            val expected=when(side) { Direction.XN->.8;Direction.XP->.2;Direction.YN->.7;Direction.YP->.3;Direction.ZN->.6;Direction.ZP->.4 }
            assertEquals(expected,SurfaceMounting.surface(box,side),"$side")
            assertNull(SurfaceMounting.surface(emptyList(),side))
        }
        assertNull(SurfaceMounting.surface(listOf(AABB(0.0,0.0,0.0,.2,.2,.2)),Direction.YN))
        assertEquals(1.5,SurfaceMounting.surface(listOf(AABB(.375,0.0,.375,.625,1.5,.625)),Direction.YN))
    }

    @Test fun cableHalvesMeetAtTheHigherSurfaceWithoutClippingTheSupport() {
        for(a in listOf(-.5,0.0,.5,.875))for(b in listOf(-.5,0.0,.5,.875)) {
            val left=-a+SurfaceMounting.cableRise(a,b)
            val right=-b+SurfaceMounting.cableRise(b,a)
            assertEquals(left,right,1e-7)
            assertTrue(left>=-a && right>=-b)
        }
    }
}
