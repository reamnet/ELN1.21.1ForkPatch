package mods.eln.gridnode

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.Test
import kotlin.test.*

class OverheadCableVisibilityTest {
    @Test fun wholeSpanBakingRequiresAllItsChunksAndKeepsAFallbackAtTheRenderBoundary() {
        val camera=Vec3(.5,90.0,.5)
        assertTrue(OverheadCableVisibility.canBake(AABB(.1,80.0,.1,48.0,90.0,1.0),camera,8) { _,_->true })
        assertFalse(OverheadCableVisibility.canBake(AABB(.1,80.0,.1,48.0,90.0,1.0),camera,8) { x,_->x!=2 })
        assertFalse(OverheadCableVisibility.canBake(AABB(.1,80.0,.1,80.0,90.0,1.0),camera,4) { _,_->true })
        assertFalse(OverheadCableVisibility.canBake(AABB(48.0,80.0,48.0,63.0,90.0,63.0),camera,4) { _,_->true })
        assertTrue(OverheadCableVisibility.canBake(AABB(-48.0,80.0,-1.0,-.1,90.0,-.1),Vec3(-.5,90.0,-.5),8) { _,_->true })
        assertFalse(OverheadCableVisibility.canBake(AABB(0.0,80.0,0.0,1.0,90.0,1.0),Vec3(1048576.5,90.0,.5),8) { _,_->true })
    }
}
