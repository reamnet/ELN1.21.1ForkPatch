package mods.eln.sixnode.lampsocket

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player

/** Stored on the placing player, rather than shared across every player and world in the JVM. */
object LampChannelMemory {
    const val DEFAULT_CHANNEL = "Default channel"
    private const val KEY = "elnLastLampChannel"

    fun read(playerData: CompoundTag): String {
        val persisted = playerData.getCompound(Player.PERSISTED_NBT_TAG)
        return if (persisted.contains(KEY, 8)) persisted.getString(KEY) else DEFAULT_CHANNEL
    }

    fun remember(playerData: CompoundTag, channel: String) {
        val persisted = playerData.getCompound(Player.PERSISTED_NBT_TAG)
        persisted.putString(KEY, channel)
        playerData.put(Player.PERSISTED_NBT_TAG, persisted)
    }
}
