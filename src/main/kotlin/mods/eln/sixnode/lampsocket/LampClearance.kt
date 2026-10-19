package mods.eln.sixnode.lampsocket

import mods.eln.Eln
import mods.eln.ghost.GhostBlock
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel

/** Upgrade saved decorative-light reservations in place; ownership and break handling stay intact. */
object LampClearance {
    fun update(element: LampSocketElement): Boolean {
        if(element.descriptor.fixture==null)return true
        val group=element.descriptor.getGhostGroup(element.side,element.front) ?: return true
        val origin=element.sixNode!!.coordinate
        val world=origin.world() as? ServerLevel ?: return false
        var complete=true
        for(cell in group.elementList) {
            val at=origin.newWithOffset(cell.x,cell.y,cell.z)
            // Defer unloaded neighbors instead of force-loading them to update collision.
            if(world.chunkSource.getChunkNow(at.x shr 4,at.z shr 4)==null) { complete=false;continue }
            val pos=BlockPos(at.x,at.y,at.z)
            val state=world.getBlockState(pos)
            val ghost=Eln.ghostManager.getGhost(at)
            if(ghost==null) {
                // The owning saved-data table can become available after the chunk itself.
                if(state.block===Eln.ghostBlock && state.getValue(GhostBlock.SHAPE)==GhostBlock.tCube)complete=false
                continue
            }
            if(ghost.observatorCoordonate!=origin || ghost.uUID!=element.descriptor.ghostGroupUuid)continue
            if(state.block===Eln.ghostBlock && state.getValue(GhostBlock.SHAPE)==GhostBlock.tCube)
                world.setBlock(pos,state.setValue(GhostBlock.SHAPE,GhostBlock.tClearance),3)
        }
        return complete
    }
}
