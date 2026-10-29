package mods.eln.transparentnode.cord

import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.sim.nbt.NbtElectricalLoad
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/** An outlet or battery-backed receptacle uses the same cord ownership and reach rules. */
interface CordSocketSource {
    val socketOwner: TransparentNodeElement
    val id: UUID
    val rating: CordRating
    val output: NbtElectricalLoad
    val capacity: Int
    val horizontal: Boolean get()=false
    fun isOccupied(slot: Int): Boolean
    fun freeSocket(): Int?=(0 until capacity).firstOrNull { !isOccupied(it) }
    fun canClaim(cord: ApplianceCord,slot: Int=cord.anchor?.socket ?: 0): Boolean
    fun reserve(cord: ApplianceCord,slot: Int): Boolean
    fun claim(cord: ApplianceCord,slot: Int)
    fun release(cord: ApplianceCord,forget: Boolean=false)
    fun disconnectSocket(slot: Int,player: ServerPlayer?=null)
    fun anchor(socket: Int=freeSocket() ?: 0): CordAnchor
}

/** Walk actual input cords, including UPS units, to reject passive and battery-backed cord loops. */
fun cordWouldLoop(owner: Any,upstream: CordSocketSource): Boolean {
    val seen=HashSet<UUID>();var next: CordSocketSource?=upstream
    while(next!=null) {
        if(next.socketOwner===owner || !seen.add(next.id))return true
        next=(next.socketOwner as? CordDevice)?.cord?.anchor?.let(ApplianceCord::findOutlet)
    }
    return false
}
