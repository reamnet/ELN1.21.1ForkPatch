package mods.eln.sixnode.lampsocket

import net.minecraft.nbt.CompoundTag

/** Extra drop below the ceiling-mounted canopy, in metres/blocks. */
class WarehouseSuspension {
    var drop = 1.0
        private set
    fun set(value: Double): Boolean {
        if (!value.isFinite()) return false
        drop = (kotlin.math.round(value.coerceIn(0.0, 7.0) * 4.0) / 4.0)
        return true
    }
    fun read(tag: CompoundTag) { if (tag.contains("warehouseDrop")) set(tag.getDouble("warehouseDrop")) }
    fun write(tag: CompoundTag) { tag.putDouble("warehouseDrop", drop) }
}
