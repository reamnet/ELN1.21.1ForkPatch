package mods.eln.client.terrain

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.phys.Vec3

/** Vanilla/Sodium select horizontal chunks, not a block-distance square around
 * the camera. Keep the detailed model throughout its loaded render chunk. */
internal object DistantVisibility {
    fun useDistant(pos: BlockPos,camera: Vec3,viewChunks: Int,chunkLoaded: Boolean): Boolean =
        !chunkLoaded || kotlin.math.abs((pos.x shr 4)-SectionPos.blockToSectionCoord(camera.x))>viewChunks ||
            kotlin.math.abs((pos.z shr 4)-SectionPos.blockToSectionCoord(camera.z))>viewChunks
}
