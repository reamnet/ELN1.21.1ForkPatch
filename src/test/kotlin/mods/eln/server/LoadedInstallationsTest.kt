package mods.eln.server

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.ChunkPos
import kotlin.test.*

class LoadedInstallationsTest {
    @Test fun areasAndSavesPreserveNegativeChunkCoordinates() {
        val square = LoadedInstallations.square(ChunkPos(-2, -4), 1)
        assertEquals(9, square.size)
        assertTrue(ChunkPos.asLong(-3, -5) in square)
        assertTrue(ChunkPos.asLong(-1, -3) in square)
        val saved = InstallationChunks(square.toMutableSet()).write(CompoundTag())
        assertEquals(square, InstallationChunks.load(saved).chunks)
        assertEquals(setOf(ChunkPos.asLong(-2, -4)), LoadedInstallations.square(ChunkPos(-2, -4), 0))
        assertEquals(81, LoadedInstallations.square(ChunkPos(0, 0), 4).size)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { LoadedInstallations.square(ChunkPos(0, 0), 5) }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { LoadedInstallations.square(ChunkPos(0, 0), -1) }
    }
}
