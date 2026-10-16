package mods.eln.server

import mods.eln.Eln
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.saveddata.SavedData
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent
import net.neoforged.neoforge.common.world.chunk.TicketController

/** Explicit administrator-owned ticking tickets. Never take ownership of vanilla or another mod's tickets. */
object LoadedInstallations {
    const val MAX_CHUNKS = 256
    private val controller = TicketController(ResourceLocation.fromNamespaceAndPath(Eln.MODID, "installations"))
    private val owner = BlockPos.ZERO

    @JvmStatic fun register(event: RegisterTicketControllersEvent) = event.register(controller)

    fun chunks(level: ServerLevel): Set<Long> = data(level).chunks.toSet()

    fun setLoaded(level: ServerLevel, chunks: Set<Long>, load: Boolean): Boolean {
        val saved = data(level)
        if (load && ((saved.chunks + chunks).size > MAX_CHUNKS || chunks.any {
                !level.worldBorder.isWithinBounds(ChunkPos(it))
            })) return false
        for (packed in chunks) {
            val chunk = ChunkPos(packed)
            if (load) {
                controller.forceChunk(level, owner, chunk.x, chunk.z, true, true)
                saved.chunks.add(packed)
            } else if (saved.chunks.remove(packed)) {
                controller.forceChunk(level, owner, chunk.x, chunk.z, false, true)
            }
        }
        saved.setDirty()
        return true
    }

    private fun data(level: ServerLevel): InstallationChunks = level.dataStorage.computeIfAbsent(
        SavedData.Factory(::InstallationChunks, { tag, _ -> InstallationChunks.load(tag) }, null), "eln_installation_chunks")

    fun square(center: ChunkPos, radius: Int): Set<Long> {
        require(radius in 0..4)
        return buildSet {
            for (x in center.x - radius..center.x + radius)
                for (z in center.z - radius..center.z + radius) add(ChunkPos.asLong(x, z))
        }
    }
}

internal class InstallationChunks(val chunks: MutableSet<Long> = linkedSetOf()) : SavedData() {
    fun write(tag: CompoundTag): CompoundTag = tag.apply { putLongArray("chunks", chunks.toLongArray()) }
    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag = write(tag)
    companion object {
        fun load(tag: CompoundTag) = InstallationChunks(tag.getLongArray("chunks").toMutableSet())
    }
}
