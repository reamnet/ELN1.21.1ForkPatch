package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.gui.*
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.server.LoadedInstallations
import mods.eln.sixnode.lampsocket.*
import mods.eln.sixnode.lampsupply.LampSupplyElement
import mods.eln.sixnode.lampsupply.LampSupplyGui
import mods.eln.transparentnode.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Difficulty
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import org.lwjgl.glfw.GLFW
import java.nio.file.Files
import java.util.concurrent.CompletableFuture

/** Opt-in real client, packet, chunk ticking and full integrated-server restart regressions.
 * JAVA_TOOL_OPTIONS includes -Deln.configurationChecks=true; runClient under an X display.
 * Creates a uniquely named disposable world and writes configuration-checks.json in the run directory.
 */
@EventBusSubscriber(modid = Eln.MODID, value = [Dist.CLIENT])
object DeviceConfigurationChecks {
    private val mc get() = Minecraft.getInstance()
    private var phase = 0
    private var ticks = 0
    private var total = 0
    private var target = 0
    private var work: CompletableFuture<Void>? = null
    private var done = false
    private val results = mutableListOf<String>()
    private val worldName = "device-regression-${System.currentTimeMillis()}"
    private val ids = listOf("variable_dc-dc_converter", "one-way_dc-dc_converter", "one-way_boost_vdc_dc_converter",
        "one-way_buck_vdc_dc_converter", "one-way_boost_buck_vdc_dc_converter", "isolation_transformer",
        "120v_lamp_supply", "classic_lamp_socket")
    private fun pos(index: Int) = BlockPos(256 + index * 8, 66, 256)
    private val remote = BlockPos(1600, 66, 1600)
    private val kept = setOf(ChunkPos(remote).toLong())
    private var smeltedBeforeRestart = 0

    private fun node(index: Int) = NodeManager.instance!!.getNodeFromCoordonate(Coordinate(pos(index).x, pos(index).y, pos(index).z, mc.singleplayerServer!!.overworld()))!!
    private fun socket() = (node(7) as SixNode).getElement(Direction.YN) as LampSocketElement
    private fun supply() = (node(6) as SixNode).getElement(Direction.YN) as LampSupplyElement
    private fun record(name: String) { results.add(name); Eln.logger.info("CONFIG_CHECK PASS {}", name) }
    private fun next(value: Int) { phase = value; ticks = 0; work = null }
    private fun server(action: (ServerPlayer) -> Unit) {
        work = CompletableFuture.runAsync({ action(mc.singleplayerServer!!.playerList.players.first()) }, mc.singleplayerServer!!)
    }
    private fun complete(): Boolean {
        val future = work ?: return false
        if (!future.isDone) return false
        future.join()
        return true
    }
    private fun place(player: ServerPlayer, id: String, p: BlockPos) {
        val world = player.serverLevel()
        for (x in -2..2) for (z in -2..2) world.setBlockAndUpdate(p.offset(x, -1, z), Blocks.STONE.defaultBlockState())
        val stack = ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("eln", id)))
        check(!stack.isEmpty) { "Missing fixture item $id" }
        player.setItemInHand(InteractionHand.MAIN_HAND, stack)
        val hit = BlockHitResult(Vec3.atCenterOf(p.below()).add(0.0, .5, 0.0), net.minecraft.core.Direction.UP, p.below(), false)
        check(stack.useOn(UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction()) { "Placement failed: $id" }
    }
    private fun click(widget: AbstractWidget) {
        check(widget.visible && widget.active)
        val screen = mc.screen!!
        val x = widget.x + widget.width / 2.0; val y = widget.y + widget.height / 2.0
        screen.mouseClicked(x, y, 0); screen.mouseReleased(x, y, 0)
    }
    private fun objects(): List<*> {
        val f = GuiHelper::class.java.getDeclaredField("objectList").apply { isAccessible = true }
        return f.get((mc.screen as GuiContainerEln).helper) as List<*>
    }
    private fun field(o: Any, name: String): Any = o.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(o)
    private fun shortcuts() {
        val screen = mc.screen as GuiContainerEln
        val fields = objects().filterIsInstance<GuiTextFieldEln>().filter { it.visible && it.enabled }
        fields.forEachIndexed { index, field ->
            click(field)
            check(field.isFocused)
            val expected = when (target) { 6 -> if (index % 2 == 0) "Light-$index" else "true"; 7 -> "Light-0"; else -> "2" }
            val modifier = if (Minecraft.ON_OSX) GLFW.GLFW_MOD_SUPER else GLFW.GLFW_MOD_CONTROL
            screen.keyPressed(GLFW.GLFW_KEY_A, 0, modifier)
            check(field.highlighted == field.text) { "Select-all failed" }
            mc.keyboardHandler.clipboard = expected
            screen.keyPressed(GLFW.GLFW_KEY_V, 0, modifier)
            check(field.text == expected) { "Paste failed" }
            screen.keyPressed(GLFW.GLFW_KEY_A, 0, modifier)
            screen.keyPressed(GLFW.GLFW_KEY_C, 0, modifier)
            check(mc.keyboardHandler.clipboard == expected) { "Copy failed" }
            screen.keyPressed(GLFW.GLFW_KEY_X, 0, modifier)
            check(field.text.isEmpty()) { "Cut failed" }
            screen.keyPressed(GLFW.GLFW_KEY_V, 0, modifier)
            check(field.text == expected)
            screen.keyPressed(GLFW.GLFW_KEY_E, 0, 0)
            check(mc.screen === screen) { "Inventory hotkey closed a focused field" }
            screen.charTyped('9', 0)
            check(field.text == expected + "9") { "Character event was lost" }
            screen.keyPressed(GLFW.GLFW_KEY_A, 0, modifier)
            screen.keyPressed(GLFW.GLFW_KEY_V, 0, modifier)
            check(field.text == expected)
        }
        record(if (fields.isEmpty()) "${ids[target]}: geometry checked (fixed device has no editable text)"
            else "${ids[target]}: shortcuts on ${fields.size} visible fields")
        MenuGeometryChecks.verify(screen, true)
        val helper = screen.helper
        val left = (screen.width - helper.xSize) / 2; val top = (screen.height - helper.ySize) / 2
        for (field in fields) check(field.x >= left && field.x + field.width <= left + helper.xSize &&
            field.y >= top && field.y + field.height <= top + helper.ySize) { "Field outside device panel" }
        Screenshot.grab(mc.gameDirectory, "configuration-${ids[target]}.png", mc.mainRenderTarget) { }
    }

    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if (!java.lang.Boolean.getBoolean("eln.configurationChecks") || done) return
        try {
            check(++total < 12000) { "Configuration check timeout in phase $phase" }
            ticks++
            when (phase) {
                0 -> if (mc.screen is TitleScreen && mc.overlay == null && ticks > 20) {
                    mc.options.guiScale().set(2)
                    mc.window.setWindowed(1280, 960); mc.resizeDisplay()
                    mc.createWorldOpenFlows().createFreshLevel(worldName,
                        LevelSettings(worldName, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, GameRules(), WorldDataConfiguration.DEFAULT),
                        WorldOptions(12345L, false, false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() }, mc.screen!!)
                    next(1)
                }
                1 -> if (mc.player != null && mc.level != null && mc.screen == null) {
                    server { player ->
                        player.setGameMode(GameType.CREATIVE)
                        player.teleportTo(player.serverLevel(), 280.0, 70.0, 260.0, 180f, 0f)
                        player.abilities.flying = true; player.onUpdateAbilities()
                        for (chunk in LoadedInstallations.square(ChunkPos(pos(3)), 3)) player.serverLevel().setChunkForced(ChunkPos(chunk).x, ChunkPos(chunk).z, true)
                        val commands = mc.singleplayerServer!!.commands
                        commands.performPrefixedCommand(player.createCommandSourceStack(), "eln chunkload on 1")
                        check(LoadedInstallations.chunks(player.serverLevel()).size == 9)
                        commands.performPrefixedCommand(player.createCommandSourceStack(), "eln chunkload off 1")
                        check(LoadedInstallations.chunks(player.serverLevel()).isEmpty())
                        commands.performPrefixedCommand(player.createCommandSourceStack(), "eln chunkload on 5")
                        check(LoadedInstallations.chunks(player.serverLevel()).isEmpty())
                        val oversized = (0..256).map { ChunkPos.asLong(it, 100) }.toSet()
                        check(!LoadedInstallations.setLoaded(player.serverLevel(), oversized, true))
                        check(LoadedInstallations.chunks(player.serverLevel()).isEmpty())
                        record("chunkload commands, invalid radius and maximum chunk limit")
                        ids.forEachIndexed { i, id -> place(player, id, pos(i)) }
                        supply().inventory!!.setItem(0, Eln.findItemStack("Low Voltage Cable", 1))
                        socket().inventory.setItem(0, Eln.findItemStack("120V Halogen Light Bulb", 1))
                        socket().inventory.setItem(1, Eln.findItemStack("Low Voltage Cable", 1))
                    }
                    nextKeepWork(2)
                }
                2 -> if (complete() && ticks > 40) {
                    server { player -> GuiHandler.open(player, GuiHandler.nodeBaseOpen + Direction.YN.int,
                        player.serverLevel(), pos(target).x, pos(target).y, pos(target).z) }
                    nextKeepWork(3)
                }
                3 -> if (complete() && mc.screen is GuiContainerEln && ticks > 40) {
                    if (target == 0 || target in 2..4) click(field(field(mc.screen!!, "controls"), "mode") as AbstractWidget)
                    if (target == 6) click(field(mc.screen!!, "rangeButton") as AbstractWidget)
                    next(4)
                }
                4 -> if (ticks > 35) {
                    shortcuts()
                    mc.window.setWindowed(640, 480); mc.resizeDisplay()
                    next(5)
                }
                5 -> if (ticks > 25) {
                    MenuGeometryChecks.verify(mc.screen!!, false)
                    // Resizing rebuilds from the synchronized render snapshot; commit final input at this size too.
                    shortcuts()
                    mc.screen!!.onClose()
                    mc.player!!.closeContainer()
                    next(6)
                }
                6 -> if (ticks > 30) {
                    server {
                        when (target) {
                            in 0..4 -> {
                                val element = (node(target) as mods.eln.node.transparent.TransparentNode).element
                                val settings = if (element is VariableDcDcElement) element.settings else (element as OneWayDcDcElement).settings
                                check(settings.value == (if (target == 1) 1.0 else 2.0)) { "Close did not save numeric field" }
                            }
                            6 -> check(supply().range == 64) { "Range button did not reach server" }
                            7 -> {
                                check(socket().lampSupplyChannel == "Light-0")
                                check(LampChannelMemory.read(it.persistentData) == "Light-0")
                                place(it, "classic_lamp_socket", pos(8))
                                val placed = (NodeManager.instance!!.getNodeFromCoordonate(Coordinate(pos(8).x, pos(8).y, pos(8).z, it.serverLevel())) as SixNode).getElement(Direction.YN) as LampSocketElement
                                check(placed.lampSupplyChannel == "Light-0") { "New socket did not remember channel" }
                                val world = it.serverLevel()
                                check(LoadedInstallations.setLoaded(world, kept, true))
                                world.setBlockAndUpdate(remote, Blocks.FURNACE.defaultBlockState())
                                val furnace = world.getBlockEntity(remote) as AbstractFurnaceBlockEntity
                                furnace.setItem(0, ItemStack(Items.IRON_ORE, 16)); furnace.setItem(1, ItemStack(Items.COAL, 4))
                            }
                        }
                    }
                    nextKeepWork(7)
                }
                7 -> if (complete()) {
                    record("${ids[target]}: server accepted GUI edits")
                    if (++target < ids.size) {
                        mc.window.setWindowed(1280, 960); mc.resizeDisplay(); next(2)
                        work = CompletableFuture.completedFuture(null)
                    } else next(8)
                }
                8 -> if (ticks > 260) {
                    server { player ->
                        val chunk = player.serverLevel().chunkSource.getChunkNow(remote.x shr 4, remote.z shr 4)
                        check(chunk != null) { "Remote forced chunk is absent" }
                        val furnace = chunk.getBlockEntity(remote) as AbstractFurnaceBlockEntity
                        smeltedBeforeRestart = furnace.getItem(2).count
                        check(furnace.getItem(2).`is`(Items.IRON_INGOT)) { "Remote chunk did not tick with no players nearby" }
                    }
                    nextKeepWork(9)
                }
                9 -> if (complete()) {
                    record("remote loaded chunk smelts without a nearby player")
                    next(10)
                    mc.level!!.disconnect()
                    mc.disconnect(TitleScreen())
                }
                10 -> if (ticks > 40 && mc.singleplayerServer == null) {
                    mc.createWorldOpenFlows().openWorld(worldName) { error("Cannot reopen regression world") }; next(11)
                }
                11 -> if (mc.player != null && mc.level != null && mc.screen == null && ticks > 260) {
                    server { player ->
                        check(LampChannelMemory.read(player.persistentData) == "Light-0") { "Player memory lost across restart" }
                        check(supply().range == 64) { "Supply range lost across restart" }
                        check(socket().lampSupplyChannel == "Light-0") { "Lamp name lost across restart" }
                        check(kept.all { it in LoadedInstallations.chunks(player.serverLevel()) })
                        val loadedChunk = player.serverLevel().chunkSource.getChunkNow(remote.x shr 4, remote.z shr 4)
                        check(loadedChunk != null) { "Forced chunk did not restore without a block lookup" }
                        val furnace = loadedChunk.getBlockEntity(remote) as? AbstractFurnaceBlockEntity
                        check(furnace != null && furnace.getItem(2).count > smeltedBeforeRestart) { "Restored chunk did not keep ticking" }
                        // Releasing ELN tickets must preserve a separate vanilla forceload ticket.
                        val chunk = ChunkPos(remote)
                        player.serverLevel().setChunkForced(chunk.x, chunk.z, true)
                        LoadedInstallations.setLoaded(player.serverLevel(), kept, false)
                        check(chunk.toLong() in player.serverLevel().forcedChunks)
                        player.serverLevel().setChunkForced(chunk.x, chunk.z, false)
                    }
                    nextKeepWork(12)
                }
                12 -> if (complete()) {
                    record("full server restart preserves channels, range and chunk tickets; release preserves foreign tickets")
                    finish(null)
                }
            }
        } catch (error: Throwable) { finish(error) }
    }
    private fun nextKeepWork(value: Int) { phase = value; ticks = 0 }
    private fun finish(error: Throwable?) {
        done = true
        val result = mapOf("world" to worldName, "passed" to results, "failure" to error?.stackTraceToString())
        Files.writeString(mc.gameDirectory.toPath().resolve("configuration-checks.json"), GsonBuilder().setPrettyPrinting().create().toJson(result))
        if (error != null) { Eln.logger.error("CONFIG_CHECK FAIL", error); System.exit(1) }
        else { Eln.logger.info("CONFIG_CHECK COMPLETE {} checks", results.size); mc.stop() }
    }
}
