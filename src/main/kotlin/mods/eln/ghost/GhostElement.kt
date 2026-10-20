package mods.eln.ghost

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.INBTTReady
import net.minecraft.world.entity.player.Player
import net.minecraft.nbt.CompoundTag
import mods.eln.misc.writeToNBT
import mods.eln.gridnode.GridElement

class GhostElement : INBTTReady {
    @JvmField
    var elementCoordinate: Coordinate? = null
    var observatorCoordonate: Coordinate? = null
    var uUID = 0

    constructor() {}
    constructor(elementCoordinate: Coordinate, observatorCoordinate: Coordinate, UUID: Int) {
        this.elementCoordinate = elementCoordinate
        observatorCoordonate = observatorCoordinate
        uUID = UUID
    }

    fun breakBlock() {
        Eln.ghostManager.removeGhost(elementCoordinate)
        val observer = Eln.ghostManager.getObserver(observatorCoordonate)
        observer?.ghostDestroyed(uUID)
    }

    fun onBlockActivated(entityPlayer: Player?, side: Direction?, vx: Float, vy: Float, vz: Float): Boolean {
        val observer = Eln.ghostManager.getObserver(observatorCoordonate)
        // Only grid devices consume owner-relative hit coordinates. Other ghost interactions
        // retain their historical block-local coordinates.
        if(observer is GridElement && elementCoordinate!=null && observatorCoordonate!=null) {
            val cell=elementCoordinate!!;val owner=observatorCoordonate!!
            return observer.ghostBlockActivated(uUID,entityPlayer!!,side!!,
                vx+cell.x-owner.x,vy+cell.y-owner.y,vz+cell.z-owner.z)
        }
        return observer?.ghostBlockActivated(uUID, entityPlayer!!, side!!, vx, vy, vz) ?: false
    }

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        elementCoordinate = Coordinate(nbt, str + "elemCoord")
        observatorCoordonate = Coordinate(nbt, str + "obserCoord")
        uUID = nbt.getInt(str + "UUID")
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        elementCoordinate!!.writeToNBT(nbt, str + "elemCoord")
        observatorCoordonate!!.writeToNBT(nbt, str + "obserCoord")
        nbt.putInt(str + "UUID", uUID)
    }
}
