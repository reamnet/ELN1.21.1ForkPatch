package mods.eln.server.console

import mods.eln.i18n.I18N.tr
import mods.eln.server.LoadedInstallations
import net.minecraft.network.chat.Component
import net.minecraft.world.level.ChunkPos

class ElnChunkloadCommand : IConsoleCommand {
    override val name = "chunkload"

    override fun runCommand(ics: ICommandSender, args: List<String>) {
        fun say(text: String) = ics.sendMessage(Component.literal(text))
        val loaded = LoadedInstallations.chunks(ics.level)
        when (args.firstOrNull()) {
            "list" -> {
                if (args.size != 1) return getManPage(ics, args)
                say(tr("ELN keeps %1$ chunks ticking in this dimension (limit %2$).", loaded.size, LoadedInstallations.MAX_CHUNKS))
                loaded.sorted().forEach {
                    val chunk = ChunkPos(it)
                    say(tr("Chunk %1$, %2$ (block %3$, %4$)", chunk.x, chunk.z, chunk.minBlockX, chunk.minBlockZ))
                }
            }
            "clear" -> {
                if (args.size != 1) return getManPage(ics, args)
                LoadedInstallations.setLoaded(ics.level, loaded, false)
                say(tr("Released all ELN installation chunks in this dimension."))
            }
            "on", "off" -> {
                val radius = if (args.size == 1) 0 else args.getOrNull(1)?.toIntOrNull()
                if (args.size > 2 || radius == null || radius !in 0..4) return getManPage(ics, args)
                val chunks = LoadedInstallations.square(ChunkPos(ics.position), radius)
                val load = args[0] == "on"
                if (!LoadedInstallations.setLoaded(ics.level, chunks, load)) {
                    say(tr("Cannot load this area: it crosses the world border or exceeds the %1$ chunk limit.", LoadedInstallations.MAX_CHUNKS))
                } else {
                    say(if (load) tr("Keeping %1$ installation chunks ticking, including after a server restart.", chunks.size)
                        else tr("Released ELN tickets for %1$ installation chunks.", chunks.size))
                }
            }
            else -> getManPage(ics, args)
        }
    }

    override fun getManPage(ics: ICommandSender, args: List<String>) {
        ics.sendMessage(Component.literal(tr("Use /eln chunkload on [0-4] or off [0-4] at the installation; radius is in chunks. Use list or clear to manage this dimension.")))
    }

    override fun getTabCompletion(args: List<String>) = if (args.size <= 1)
        listOf("on", "off", "list", "clear").filter { it.startsWith(args.firstOrNull() ?: "") }
        else if (args[0] in listOf("on", "off")) (0..4).map(Int::toString) else emptyList()
}
