package mods.eln.sixnode.lampsocket

import mods.eln.Eln
import mods.eln.misc.setBlockToAir
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel

/** Reconcile pre-3.0.13 pole clearance after its heads moved up two blocks. */
object ParkingLightClearance {
    fun update(element: LampSocketElement): Boolean {
        val origin = element.sixNode!!.coordinate
        val world = origin.world() as? ServerLevel ?: return false
        val manager = Eln.ghostManager
        val uuid = element.descriptor.ghostGroupUuid
        val group = element.descriptor.getGhostGroup(element.side,element.front) ?: return true
        val desired = group.elementList.map { origin.newWithOffset(it.x,it.y,it.z) }.toSet()
        val owned = manager.ghostTable.values.filter { it.observatorCoordonate == origin && it.uUID == uuid }
        val ownedPositions = owned.mapNotNull { it.elementCoordinate }.toSet()
        // Never load neighboring chunks or replace somebody else's blocks while upgrading.
        if (desired.any { world.chunkSource.getChunkNow(it.x shr 4,it.z shr 4) == null }) return false
        if (desired.any { it !in ownedPositions && !world.getBlockState(BlockPos(it.x,it.y,it.z)).isAir }) return false
        for (entry in owned) {
            val at = entry.elementCoordinate!!
            if (at !in desired) {
                manager.removeGhost(at)
                if (world.getBlockState(BlockPos(at.x,at.y,at.z)).block === Eln.ghostBlock) world.setBlockToAir(at.x,at.y,at.z)
            }
        }
        for (at in desired) if (at !in ownedPositions) manager.createGhost(at,origin,uuid,meta=mods.eln.ghost.GhostBlock.tClearance)
        return true
    }
}
