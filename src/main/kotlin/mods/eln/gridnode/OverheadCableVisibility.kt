package mods.eln.gridnode

import net.minecraft.core.SectionPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** A span crossing unloaded/render-distance chunks must keep its whole-span fallback:
 * baking just the nearby pieces would leave a gap before its owner hands off to DH. */
internal object OverheadCableVisibility {
    fun canBake(bounds: AABB,camera: Vec3,viewChunks: Int,loaded: (Int,Int)->Boolean): Boolean {
        val cameraX=SectionPos.blockToSectionCoord(camera.x);val cameraZ=SectionPos.blockToSectionCoord(camera.z)
        val radius=(viewChunks-1).coerceAtLeast(0)
        for(x in SectionPos.blockToSectionCoord(bounds.minX)..SectionPos.blockToSectionCoord(bounds.maxX))
            for(z in SectionPos.blockToSectionCoord(bounds.minZ)..SectionPos.blockToSectionCoord(bounds.maxZ)) {
                val dx=(x-cameraX).toLong();val dz=(z-cameraZ).toLong()
                if(dx*dx+dz*dz>radius.toLong()*radius || !loaded(x,z))return false
            }
        return true
    }
}
