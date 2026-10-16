package mods.eln.sixnode.lampsocket

import net.minecraft.nbt.CompoundTag
import kotlin.test.*

class LampChannelMemoryTest {
    @Test fun channelsArePersistentAndIndependentPerPlayer() {
        val alice = CompoundTag()
        val bob = CompoundTag()
        assertEquals("Default channel", LampChannelMemory.read(alice))
        LampChannelMemory.remember(alice, "Workshop α")
        LampChannelMemory.remember(bob, "Hallway")
        assertEquals("Workshop α", LampChannelMemory.read(alice.copy()))
        assertEquals("Hallway", LampChannelMemory.read(bob))
        LampChannelMemory.remember(alice, "")
        assertEquals("", LampChannelMemory.read(alice))
        assertEquals("Hallway", LampChannelMemory.read(bob))
    }
}
