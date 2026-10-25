package mods.eln.transparentnode.firealarm

import mods.eln.environment.RoomThermalManager
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CampfireBlock
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.abs

/** Server-side smoke paths, not client particles. Every read uses an already-loaded chunk. */
object FireEnvironment {
    fun state(world: ServerLevel,pos: BlockPos): BlockState? = world.chunkSource.getChunkNow(pos.x shr 4,pos.z shr 4)?.getBlockState(pos)
    fun smokeSource(state: BlockState)=state.`is`(BlockTags.FIRE) || state.block is CampfireBlock && state.getValue(CampfireBlock.LIT)
    fun smoke(world: ServerLevel,origin: BlockPos,range: Int): Boolean {
        val visited=HashSet<BlockPos>();val queue=ArrayDeque<Pair<BlockPos,Int>>()
        visited+=origin;queue.add(origin to 0)
        while(queue.isNotEmpty()) {
            val (p,depth)=queue.removeFirst();val s=state(world,p) ?: continue
            if(smokeSource(s))return true
            if(depth>=range || p!=origin && (s.isCollisionShapeFullBlock(world,p) || !s.fluidState.isEmpty))continue
            // Smoke spreads through reachable air. It does not descend below a detector to
            // an arbitrarily deep source; the bounded path length is the detection radius.
            for(face in net.minecraft.core.Direction.entries) {
                val q=p.relative(face)
                if(q.y>origin.y+1 || !visited.add(q))continue
                queue.add(q to depth+1)
            }
        }
        return false
    }
    fun heat(world: ServerLevel,origin: BlockPos,range: Int): Double {
        var temp=RoomThermalManager.getRoomAt(world,origin.x,origin.y,origin.z)?.temperatureCelsius ?: 20.0
        for(x in -range..range)for(y in -range..range)for(z in -range..range) {
            val distance=x*x+y*y+z*z;if(distance>range*range)continue
            val p=origin.offset(x,y,z);val s=state(world,p) ?: continue
            val boost=when {
                s.`is`(BlockTags.FIRE)->300.0
                s.`is`(Blocks.LAVA)->450.0
                s.block is CampfireBlock && s.getValue(CampfireBlock.LIT)->180.0
                s.`is`(Blocks.MAGMA_BLOCK)->100.0
                else->0.0
            }
            // Short-range conducted/radiant heat can reach a heat detector behind a
            // partition even though no open smoke path exists.
            temp=maxOf(temp,20.0+boost/(1+distance))
        }
        return temp
    }
}
