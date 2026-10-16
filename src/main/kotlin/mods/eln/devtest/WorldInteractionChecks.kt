package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.ServerKeyHandler
import mods.eln.generic.*
import mods.eln.gridnode.GridElement
import mods.eln.gridnode.GridLink
import mods.eln.gui.GuiButtonEln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeEntity
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.power.ConservativePowerProcess
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import mods.eln.sixnode.electricalcable.UtilityCableMaterial
import mods.eln.transparentnode.*
import mods.eln.transparentnode.utilitylight.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Difficulty
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlin.math.*

/** Opt-in real client, inventory/menu packets, chunk travel, grid power and saved-world regressions.
 * Use -Deln.worldInteractionChecks=true with runClient under a working X/GL display.
 */
@EventBusSubscriber(modid = Eln.MODID, value = [Dist.CLIENT])
object WorldInteractionChecks {
    private val mc get() = Minecraft.getInstance()
    private val worldName = "world-interaction-${System.currentTimeMillis()}"
    private var phase = 0
    private var ticks = 0
    private var total = 0
    private var done = false
    private var work: CompletableFuture<Void>? = null
    private val passed = mutableListOf<String>()
    private val names = listOf("Variable DC-DC Converter", "One-way Buck vDC/DC Converter", "One-way Boost/Buck vDC/DC Converter")
    private fun converter(i: Int) = BlockPos(256 + i * 12, 66, 256)
    private val cover = BlockPos(256, 66, 284)
    private val standalone = BlockPos(272, 66, 284)
    private val feed = BlockPos(256, 66, 312)
    private val pole = feed.east(8)
    private fun fixture(player: ServerPlayer) = NativeCampaignFixtures(player.serverLevel(), player)
    private fun unit(f: NativeCampaignFixtures) = f.machine(standalone) as StepDownUnitElement
    private fun lamp(f: NativeCampaignFixtures) = f.machine(pole) as UtilityStreetLightElement
    private fun step(value: Int, keepWork: Boolean = false) { phase = value; ticks = 0; if (!keepWork) work = null }
    private fun server(action: (ServerPlayer) -> Unit) {
        val s = mc.singleplayerServer!!
        work = CompletableFuture.runAsync({ action(s.playerList.players.first()) }, s)
    }
    private fun complete(): Boolean { val w = work ?: return false; if (!w.isDone) return false; w.join(); return true }
    private fun record(name: String) { passed.add(name); Eln.logger.info("WORLD_CHECK PASS {}", name) }
    private fun foundation(player: ServerPlayer, p: BlockPos, radius: Int = 4) {
        for (x in -radius..radius) for (z in -radius..radius)
            player.serverLevel().setBlockAndUpdate(p.offset(x,-1,z),Blocks.STONE.defaultBlockState())
    }
    private fun sampleConverters(f: NativeCampaignFixtures) {
        repeat(6) { i ->
            val e = f.machine(converter(i))
            val values = f.converterValues(e)
            val expected = if (i % 2 == 0) 120.0 else 200.0
            val actual = values["outputV"] as Double
            check(abs(actual - expected) < 1.0) { "${names[i/2]} did not resume: $values" }
            check((values["outputW"] as Double) > 10.0) { "Converter has no loaded output: $values" }
        }
    }
    private fun look(player: ServerPlayer, target: BlockPos, dx: Double, dy: Double, dz: Double) {
        player.setNoGravity(true); player.abilities.flying = true; player.onUpdateAbilities()
        val x = target.x + .5 + dx; val y = target.y + dy; val z = target.z + .5 + dz
        val tx = -dx; val ty = .5 - dy - player.eyeHeight; val tz = -dz
        val yaw = Math.toDegrees(atan2(-tx, tz)).toFloat()
        val pitch = -Math.toDegrees(atan2(ty, sqrt(tx*tx+tz*tz))).toFloat()
        player.teleportTo(player.serverLevel(), x,y,z,yaw,pitch)
    }
    private fun screenshot(name: String) { Screenshot.grab(mc.gameDirectory,"world-interaction-$name.png",mc.mainRenderTarget) {} }
    private fun clickVoltage() {
        val gui = mc.screen as StepDownGui
        val button = StepDownGui::class.java.getDeclaredField("voltage").apply { isAccessible = true }.get(gui) as GuiButtonEln
        val x = button.x + button.width/2.0; val y = button.y + button.height/2.0
        gui.mouseClicked(x,y,0); gui.mouseReleased(x,y,0)
    }

    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if (!java.lang.Boolean.getBoolean("eln.worldInteractionChecks") || done) return
        try {
            mc.player?.let { it.setNoGravity(true); it.abilities.flying = true }
            check(++total < 12000) { "World interaction timeout in phase $phase" }
            ticks++
            when (phase) {
                0 -> if (mc.screen is TitleScreen && mc.overlay == null && ticks > 20) {
                    mc.options.guiScale().set(2); mc.window.setWindowed(1280,960); mc.resizeDisplay()
                    mc.createWorldOpenFlows().createFreshLevel(worldName,
                        LevelSettings(worldName, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, GameRules(), WorldDataConfiguration.DEFAULT),
                        WorldOptions(23456L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!)
                    step(1)
                }
                1 -> if (mc.player != null && mc.level != null && mc.screen == null) {
                    server { player ->
                        player.setGameMode(GameType.CREATIVE); player.abilities.flying = true; player.onUpdateAbilities()
                        player.teleportTo(player.serverLevel(),280.0,72.0,280.0,180f,0f)
                        val f = fixture(player)
                        repeat(6) { i ->
                            val p = converter(i); foundation(player,p)
                            val e = f.setupConverter(p,names[i/2],800.0,120.0)
                            val settings = if (e is VariableDcDcElement) e.settings else (e as OneWayDcDcElement).settings
                            settings.mode = if (i % 2 == 0) "VOLTAGE" else "RATIO"
                            settings.value = if (i % 2 == 0) 120.0 else .25
                            f.load(p.east(2),500.0); f.ground(p.east(3))
                        }
                        foundation(player,cover); f.wire(cover)
                        val node = f.node(cover) as SixNode
                        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Blocks.STONE))
                        ServerKeyHandler.set(player.uuid,ServerKeyHandler.WRENCH,true)
                        try { check(node.onBlockActivated(player,Direction.YP,.5f,.5f,.5f)) }
                        finally { ServerKeyHandler.set(player.uuid,ServerKeyHandler.WRENCH,false) }
                        check(node.sixNodeCacheBlock === Blocks.STONE)
                        val shape = player.serverLevel().getBlockState(cover).getCollisionShape(player.serverLevel(),cover)
                        check(shape.bounds().size >= 1.0)
                        check(player.serverLevel().getBlockState(cover).getLightBlock(player.serverLevel(),cover) == 15)
                        foundation(player,standalone)
                        f.placeMachine(f.requiredItem("Mains Step-down Unit"),standalone).apply { front = Direction.ZP; reconnect() }
                        f.wire(standalone.west()); f.source(standalone.west(2),3200.0)
                        f.wire(standalone.east()); f.load(standalone.east(2),240.0); f.ground(standalone.east(3))
                        foundation(player,feed); foundation(player,pole)
                        val direct = f.placeMachine(f.requiredItem("Direct Utility Pole"),feed) as GridElement
                        f.wire(feed.west()); f.source(feed.west(2),12800.0)
                        val street = f.placeMachine(f.requiredItem("Utility Pole Street Light"),pole) as UtilityStreetLightElement
                        val cable = UtilityCableDescriptor.allDescriptors().single { it.material == UtilityCableMaterial.ALUMINUM && it.sizeLabel == "1000 kcmil" && !it.insulated && !it.melted }
                        val stack = cable.newItemStack(1).also { cable.setRemainingLengthMeters(it,8.0) }
                        GridLink.addLink(direct,street,Direction.XP,Direction.XN,cable,8,stack)
                        f.wire(pole.east()); f.load(pole.east(2),240.0); f.ground(pole.east(3))
                    }; step(2,true)
                }
                2 -> if (complete() && ticks > 100) {
                    server { player ->
                        val f = fixture(player); sampleConverters(f)
                        check(unit(f).unit.secondary.voltage in 119.0..121.0)
                        check(lamp(f).unit.secondary.voltage < 1.0 && lamp(f).lightLevel == 0)
                        lamp(f).inventory.setItem(0,f.requiredItem("Mains Step-down Unit")); lamp(f).inventory.setChanged()
                        repeat(6) { i ->
                            val e = f.machine(converter(i))
                            val process = if (e is VariableDcDcElement) e.interSystemProcess else
                                e.javaClass.getDeclaredField("transferProcess").apply { isAccessible = true }.get(e) as ConservativePowerProcess
                            process.failClosed()
                        }
                    }; step(3,true)
                }
                3 -> if (complete() && ticks > 80) {
                    server { player ->
                        val f = fixture(player); sampleConverters(f)
                        check(lamp(f).unit.secondary.voltage in 119.0..121.0 && lamp(f).lightLevel >= 14)
                        check(lamp(f).unit.output.power > 110.0) { "Pole does not supply its external load: ${lamp(f).unit.waila()}" }
                        val head = Coordinate(-1,2,0,0).apply { applyTransformation(lamp(f).front,Coordinate(pole.x,pole.y,pole.z,player.serverLevel())) }
                        check(player.serverLevel().getBlockState(head.pos).getLightEmission(player.serverLevel(),head.pos) >= 14)
                        player.serverLevel().dayTime = 18000L
                        look(player,pole,5.0,2.0,7.0)
                    }; step(4,true)
                }
                4 -> if (complete() && ticks > 60) {
                    record("six variable/voltage-controlled converters recover without GUI toggles; removable grid-fed light powers its lamp and base output")
                    screenshot("pole")
                    server { it.serverLevel().dayTime=6000L; look(it,cover,0.0,.5,3.0) }; step(5,true)
                }
                5 -> if (complete() && ticks > 60) {
                    check((mc.level!!.getBlockEntity(cover) as SixNodeEntity).sixNodeCacheBlock === Blocks.STONE)
                    screenshot("cover")
                    record("C + stone installs an opaque solid cover and the client receives its model")
                    val tabs = listOf(Eln.creativeTabLighting,Eln.creativeTabOresMaterials,Eln.creativeTabCables,Eln.creativeTabToolsArmor)
                    for (tab in tabs) {
                        val entries = mutableListOf<ItemStack>(); CreativeTabPopulator.addEntries(tab,entries::add)
                        val seen = mutableSetOf<String>(); var previous = ""; var legacy = -1
                        for (s in entries) {
                            val item = s.item
                            val key = when (item) { is DescriptorBlockItem<*> -> item.descriptor.javaClass.name; is DescriptorItem<*> -> item.descriptor.javaClass.name; else -> item.javaClass.name }
                            if (key != previous) { check(seen.add(key)) { "Creative family split across list" }; previous=key; legacy=-1 }
                            if (item is IDescriptorItem) { check(item.legacyId() >= legacy); legacy=item.legacyId() }
                        }
                    }
                    record("creative families stay together in their registered order")
                    server { GuiHandler.open(it,GuiHandler.nodeBaseOpen+Direction.YN.int,it.serverLevel(),standalone.x,standalone.y,standalone.z) }
                    step(6,true)
                }
                6 -> if (complete() && mc.screen is StepDownGui && ticks > 40) { clickVoltage(); step(7) }
                7 -> if (ticks > 40) {
                    screenshot("stepdown-gui"); mc.setScreen(null)
                    server { player ->
                        check(unit(fixture(player)).unit.outputVoltage == 240)
                        GuiHandler.open(player,GuiHandler.nodeBaseOpen+Direction.YN.int,player.serverLevel(),pole.x,pole.y,pole.z)
                    }; step(8,true)
                }
                8 -> if (complete() && mc.screen is StepDownGui && ticks > 40) { clickVoltage(); step(9) }
                9 -> if (ticks > 40) {
                    screenshot("pole-gui"); mc.setScreen(null)
                    server { player ->
                        val f=fixture(player)
                        check(unit(f).unit.secondary.voltage in 239.0..241.0)
                        check(lamp(f).unit.outputVoltage == 240 && lamp(f).unit.secondary.voltage in 239.0..241.0)
                        lamp(f).inventory.setItem(0,ItemStack.EMPTY); lamp(f).inventory.setChanged()
                    }; step(16,true)
                }
                16 -> if (complete() && ticks > 40) {
                    server { player ->
                        val f=fixture(player); val light=lamp(f)
                        check(!light.unit.input.enabled && !light.unit.output.enabled)
                        check(light.lightLevel == 0 && light.unit.secondary.voltage < 1.0)
                        light.inventory.setItem(0,f.requiredItem("Mains Step-down Unit")); light.inventory.setChanged()
                        f.voltage(feed.west(2),3200.0)
                        player.teleportTo(player.serverLevel(),4096.0,80.0,4096.0,0f,0f)
                    }; step(10,true)
                }
                10 -> if (complete() && ticks > 650) {
                    server { player ->
                        val f=fixture(player)
                        check(player.serverLevel().chunkSource.getChunkNow(converter(0).x shr 4,converter(0).z shr 4) == null) { "Converter chunk never unloaded" }
                        sampleConverters(f)
                        check(unit(f).unit.secondary.voltage in 239.0..241.0)
                        check(lamp(f).unit.secondary.voltage in 239.0..241.0)
                        look(player,pole,5.0,2.0,7.0)
                    }; step(11,true)
                }
                11 -> if (complete() && ticks > 100) {
                    server { player -> sampleConverters(fixture(player)); check(lamp(fixture(player)).lightLevel >= 14) }
                    step(12,true)
                }
                12 -> if (complete()) {
                    record("travelling 4000 blocks, unloading and returning preserves converter power without GUI edits")
                    record("module removal opens both branches; reinsertion and a 12.8 kV to 3.2 kV grid change restore the selected output")
                    step(13); mc.level!!.disconnect(); mc.disconnect(TitleScreen())
                }
                13 -> if (ticks > 40 && mc.singleplayerServer == null) {
                    mc.createWorldOpenFlows().openWorld(worldName) { error("Cannot reopen world") }; step(14)
                }
                14 -> if (mc.player != null && mc.level != null && mc.screen == null && ticks > 140) {
                    server { player ->
                        val f=fixture(player); sampleConverters(f)
                        check(unit(f).unit.outputVoltage == 240 && unit(f).unit.secondary.voltage in 239.0..241.0)
                        val light=lamp(f)
                        check(light.installed && light.unit.outputVoltage == 240 && light.lightLevel >= 14)
                        check(light.gridLinkList.size == 1)
                        check((f.node(cover) as SixNode).sixNodeCacheBlock === Blocks.STONE)
                    }; step(15,true)
                }
                15 -> if (complete()) { record("saved-world restart preserves converters, pole span/module, selected voltages, light and cover"); finish(null) }
            }
        } catch (e: Throwable) { finish(e) }
    }
    private fun finish(error: Throwable?) {
        done=true
        Files.writeString(mc.gameDirectory.toPath().resolve("world-interaction-checks.json"),GsonBuilder().setPrettyPrinting().create().toJson(
            mapOf("world" to worldName,"passed" to passed,"failure" to error?.stackTraceToString())))
        if (error != null) { Eln.logger.error("WORLD_CHECK FAIL",error); System.exit(1) }
        else { Eln.logger.info("WORLD_CHECK COMPLETE {} checks",passed.size); mc.stop() }
    }
}
