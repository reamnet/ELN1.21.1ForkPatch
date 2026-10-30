package mods.eln.node

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

/** Passive simulation/shape probes may read resident chunks, never request or generate one. */
object LoadedBlockStates {
    @JvmStatic fun get(world: BlockGetter, pos: BlockPos): BlockState? {
        if(world.isOutsideBuildHeight(pos))return Blocks.AIR.defaultBlockState()
        return when(world) {
            is ServerLevel->world.chunkSource.getChunkNow(pos.x shr 4,pos.z shr 4)?.getBlockState(pos)
            is Level->if(world.hasChunkAt(pos))world.getBlockState(pos) else null
            else->world.getBlockState(pos) // Chunk/render views are already bounded.
        }
    }
}
