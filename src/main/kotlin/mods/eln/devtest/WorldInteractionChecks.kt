package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.ServerKeyHandler
import mods.eln.generic.*
import mods.eln.gridnode.GridElement
import mods.eln.gridnode.GridLink
import mods.eln.gridnode.electricalpole.ElectricalPoleElement
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
    private var serverReadyAt = 0
    private var done = false
    private var work: CompletableFuture<Void>? = null
    private val passed = mutableListOf<String>()
    private val names = listOf("Variable DC-DC Converter", "One-way Buck vDC/DC Converter", "One-way Boost/Buck vDC/DC Converter")
    private fun converter(i: Int) = BlockPos(256 + i * 12, 66, 256)
    private val cover = BlockPos(256, 66, 284)
    private val standalone = BlockPos(272, 66, 284)
    private val feed = BlockPos(256, 66, 312)
    private val pole = feed.east(8)
    private val olderPole = feed.east(4)
    private var olderReference: ElectricalPoleElement? = null
    private const val longPoleCount = 120
    private fun longPole(i: Int) = pole.east((i + 1) * 32)
    private val taps = PoleTapFixture(List(4) { longPole(it) })
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
    private fun checkPoleRatio(light: UtilityStreetLightElement) {
        check(light.unit.primary.voltage>1000.0) { "Pole input is not energized" }
        val expected=light.unit.primary.voltage*light.unit.outputVoltage/MainsStepDown.NOMINAL_POLE_VOLTS
        check(abs(light.unit.secondary.voltage-expected)<1.0 && light.unit.controller.status!="NON_CONVERGENT") {
            "Pole did not follow its fixed ratio: ${light.unit.waila()}"
        }
        val expectedLight=(15*light.lamp.power/60).toInt().coerceIn(0,15)
        check(abs(light.lightLevel-expectedLight)<=1)
    }
    private fun sampleLongLine(f: NativeCampaignFixtures) {
        val old = f.node(olderPole) as? mods.eln.node.transparent.TransparentNode
        check(old != null) {
            val e = olderReference!!
            "Older pole vanished: destructing=${e.node!!.isDestructing} primary=${e.electricalLoad.voltage} secondary=${e.trafo!!.secondaryLoad.voltage} secondaryLimit=${e.trafo!!.voltageSecondaryWatchdog.max} thermal=${e.thermalLoad.temperatureCelsius}"
        }
        val transformer = (old.element as ElectricalPoleElement).trafo!!
        check(abs(transformer.primaryVoltageSource.power + transformer.secondaryVoltageSource.power) < .001) {
            "Unused older pole transformer sinks or creates power on the overhead line"
        }
        repeat(longPoleCount) { i ->
            val light = f.machine(longPole(i)) as UtilityStreetLightElement
            val expected = if (i % 2 == 0) 120 else 240
            check(light.installed && light.unit.outputVoltage == expected)
            checkPoleRatio(light)
            val fraction=light.unit.primary.voltage/MainsStepDown.NOMINAL_POLE_VOLTS
            check(light.unit.output.power > (if(i==longPoleCount-1)8900.0 else 59.0)*fraction*fraction*.98) {
                "Long-line pole $i has no loaded output: ${light.unit.waila()}"
            }
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
                        f.wire(feed.west()); f.source(feed.west(2),14000.0)
                        val street = f.placeMachine(f.requiredItem("Utility Pole Street Light"),pole) as UtilityStreetLightElement
                        val cable = UtilityCableDescriptor.allDescriptors().single { it.material == UtilityCableMaterial.ALUMINUM && it.sizeLabel == "1000 kcmil" && !it.insulated && !it.melted }
                        foundation(player,olderPole)
                        val older = f.placeMachine(f.requiredItem("Utility Pole w/DC-DC Converter"),olderPole) as ElectricalPoleElement
                        olderReference = older
                        // The unused legacy transformer is a spur, not a thermal
                        // bottleneck carrying the entire 120-light/machine feeder.
                        for ((a,b,meters) in listOf(Triple(direct,street,8),Triple(direct,older,4))) {
                            val stack = cable.newItemStack(1).also { cable.setRemainingLengthMeters(it,meters.toDouble()) }
                            GridLink.addLink(a,b,Direction.XP,Direction.XN,cable,meters,stack)
                        }
                        f.wire(pole.east()); f.load(pole.east(2),240.0); f.ground(pole.east(3))
                        var previous: GridElement = street
                        repeat(longPoleCount) { i ->
                            val at = longPole(i); foundation(player,at)
                            val next = f.placeMachine(f.requiredItem("Utility Pole Street Light"),at) as UtilityStreetLightElement
                            next.inventory.setItem(0,f.requiredItem("Mains Step-down Unit")); next.inventory.setChanged()
                            next.unit.read(net.minecraft.nbt.CompoundTag().apply { putInt("mainsOutputVoltage",if (i % 2 == 0) 120 else 240) })
                            val span = cable.newItemStack(1).also { cable.setRemainingLengthMeters(it,32.0) }
                            GridLink.addLink(previous,next,Direction.XP,Direction.XN,cable,32,span)
                            previous = next
                        }
                        // A real 9 kW load at the remote end, sharing its unit with the lamp.
                        val end = longPole(longPoleCount - 1)
                        f.wire(end.east()); f.load(end.east(2),240.0 * 240.0 / 9000.0); f.ground(end.east(3))
                        taps.install(f)
                    }; step(2,true)
                }
                2 -> if (complete()) {
                    // A 120-pole placement can outlast 100 client ticks. Wait for actual
                    // simulation ticks after construction, rather than sampling on its return.
                    serverReadyAt = mc.singleplayerServer!!.tickCount + 100
                    step(17)
                }
                17 -> if (mc.singleplayerServer!!.tickCount >= serverReadyAt) {
                    server { player ->
                        val f = fixture(player); sampleConverters(f)
                        sampleLongLine(f)
                        taps.attachLoads(f)
                        check(unit(f).unit.secondary.voltage in 119.0..121.0)
                        check(lamp(f).unit.secondary.voltage < 1.0 && lamp(f).lightLevel == 0)
                        lamp(f).inventory.setItem(0,f.requiredItem("Mains Step-down Unit")); lamp(f).inventory.setChanged()
                        repeat(6) { i ->
                            val e = f.machine(converter(i))
                            val process = if (e is VariableDcDcElement) e.interSystemProcess else
                                e.javaClass.getDeclaredField("transferProcess").apply { isAccessible = true }.get(e) as ConservativePowerProcess
                            process.failClosed()
                        }
                    }
                    serverReadyAt = mc.singleplayerServer!!.tickCount + 100
                    step(3,true)
                }
                3 -> if (complete() && mc.singleplayerServer!!.tickCount >= serverReadyAt) {
                    server { player ->
                        val f = fixture(player); sampleConverters(f)
                        sampleLongLine(f)
                        taps.sample(f)
                        checkPoleRatio(lamp(f))
                        check(lamp(f).unit.output.power > 90.0) { "Pole does not supply its external load: ${lamp(f).unit.waila()}" }
                        val head = Coordinate(-1,2,0,0).apply { applyTransformation(lamp(f).front,Coordinate(pole.x,pole.y,pole.z,player.serverLevel())) }
                        check(player.serverLevel().getBlockState(head.pos).getLightEmission(player.serverLevel(),head.pos) >= 11)
                        player.serverLevel().dayTime = 18000L
                        look(player,pole,5.0,2.0,7.0)
                    }; step(4,true)
                }
                4 -> if (complete() && ticks > 60) {
                    record("six variable/voltage-controlled converters recover without GUI toggles; removable grid-fed light powers its lamp and base output")
                    record("120 installed 120/240 V street lights span 3840 metres, including a real remote 9 kW load")
                    record("the older utility pole transformer joins the same power-conserving solve on the overhead feeder")
                    record("direct and transformer pole taps power 850 V teleporter charging ports and 3200 V Large Shaft Motors through target-voltage converters")
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
                6 -> if (complete() && mc.screen is StepDownGui && ticks > 40) {
                    clickVoltage(); serverReadyAt = mc.singleplayerServer!!.tickCount + 40; step(7)
                }
                7 -> if (ticks > 40 && mc.singleplayerServer!!.tickCount >= serverReadyAt) {
                    screenshot("stepdown-gui"); mc.setScreen(null)
                    server { player ->
                        check(unit(fixture(player)).unit.outputVoltage == 240)
                        GuiHandler.open(player,GuiHandler.nodeBaseOpen+Direction.YN.int,player.serverLevel(),pole.x,pole.y,pole.z)
                    }; step(8,true)
                }
                8 -> if (complete() && mc.screen is StepDownGui && ticks > 40) {
                    clickVoltage(); serverReadyAt = mc.singleplayerServer!!.tickCount + 40; step(9)
                }
                9 -> if (ticks > 40 && mc.singleplayerServer!!.tickCount >= serverReadyAt) {
                    screenshot("pole-gui"); mc.setScreen(null)
                    server { player ->
                        val f=fixture(player)
                        check(unit(f).unit.secondary.voltage in 239.0..241.0)
                        check(lamp(f).unit.outputVoltage == 240);checkPoleRatio(lamp(f))
                        lamp(f).inventory.setItem(0,ItemStack.EMPTY); lamp(f).inventory.setChanged()
                        serverReadyAt = player.server.tickCount + 40
                    }; step(16,true)
                }
                16 -> if (complete() && mc.singleplayerServer!!.tickCount >= serverReadyAt) {
                    server { player ->
                        val f=fixture(player); val light=lamp(f)
                        check(!light.unit.input.enabled && !light.unit.output.enabled)
                        check(light.lightLevel == 0 && light.unit.secondary.voltage < 1.0)
                        light.inventory.setItem(0,f.requiredItem("Mains Step-down Unit")); light.inventory.setChanged()
                        f.voltage(feed.west(2),3200.0)
                        player.teleportTo(player.serverLevel(),4096.0,80.0,4096.0,0f,0f)
                        serverReadyAt = player.server.tickCount + 650
                    }; step(10,true)
                }
                10 -> if (complete() && mc.singleplayerServer!!.tickCount >= serverReadyAt) {
                    server { player ->
                        val f=fixture(player)
                        check(player.serverLevel().chunkSource.getChunkNow(converter(0).x shr 4,converter(0).z shr 4) == null) { "Converter chunk never unloaded" }
                        sampleConverters(f)
                        sampleLongLine(f)
                        taps.sample(f, false)
                        check(unit(f).unit.secondary.voltage in 239.0..241.0)
                        checkPoleRatio(lamp(f))
                        look(player,pole,5.0,2.0,7.0)
                    }; step(11,true)
                }
                11 -> if (complete() && ticks > 100) {
                    server { player -> sampleConverters(fixture(player)); checkPoleRatio(lamp(fixture(player))) }
                    step(12,true)
                }
                12 -> if (complete()) {
                    record("travelling 4000 blocks, unloading and returning preserves converter power without GUI edits")
                    record("module removal opens both branches; reinsertion and a 14 kV to 3.2 kV grid change preserve the fixed transformer ratio")
                    step(13); mc.level!!.disconnect(); mc.disconnect(TitleScreen())
                }
                13 -> if (ticks > 40 && mc.singleplayerServer == null) {
                    mc.createWorldOpenFlows().openWorld(worldName) { error("Cannot reopen world") }; step(14)
                }
                14 -> if (mc.player != null && mc.level != null && mc.screen == null) {
                    serverReadyAt = mc.singleplayerServer!!.tickCount + 140
                    step(18)
                }
                18 -> if (mc.singleplayerServer!!.tickCount >= serverReadyAt) {
                    server { player ->
                        val f=fixture(player); sampleConverters(f)
                        sampleLongLine(f)
                        taps.sample(f, false)
                        check(unit(f).unit.outputVoltage == 240 && unit(f).unit.secondary.voltage in 239.0..241.0)
                        val light=lamp(f)
                        check(light.installed && light.unit.outputVoltage == 240);checkPoleRatio(light)
                        check(light.gridLinkList.size == 2)
                        check((f.node(cover) as SixNode).sixNodeCacheBlock === Blocks.STONE)
                    }; step(15,true)
                }
                15 -> if (complete()) { record("saved-world restart preserves converters, pole span/module, selected voltages, light and cover"); finish(null) }
            }
            // Sample repeatedly through stable phases to catch a brief retry blink, rather
            // than accepting a single successful screenshot before/after a longer outage.
            if (phase in setOf(3, 10, 14) && ticks > 30 && ticks % 5 == 0 && (work == null || work!!.isDone)) {
                work?.join()
                server { sampleLongLine(fixture(it)) }
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
