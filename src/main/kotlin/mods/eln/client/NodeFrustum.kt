package mods.eln.client

import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent

/** Grid spans can cross the camera even when their owning chunk section is behind it. */
object NodeFrustum {
    private var frustum: Frustum? = null
    @SubscribeEvent
    fun frame(event: RenderLevelStageEvent) {
        frustum = event.frustum
    }
    fun visible(box: AABB) = frustum?.isVisible(box) != false
    fun inRange(box: AABB, camera: Vec3, range: Int): Boolean {
        val dx = (camera.x - camera.x.coerceIn(box.minX, box.maxX))
        val dy = (camera.y - camera.y.coerceIn(box.minY, box.maxY))
        val dz = (camera.z - camera.z.coerceIn(box.minZ, box.maxZ))
        return dx*dx + dy*dy + dz*dz < range.toDouble()*range
    }
}
