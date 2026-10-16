package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.mechanical.GeneratorElement
import mods.eln.mechanical.SimpleShaftElement
import mods.eln.misc.Direction
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.Difficulty
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

/** RunClient with -Deln.generatorChecks=place, then =verify in a separate JVM.
 * Uses real saved blocks, shaft drive and electrical loads; never replaces cables on verification.
 */
@EventBusSubscriber(modid = Eln.MODID, value = [Dist.CLIENT])
object GeneratorColdStartChecks {
    private val mode = System.getProperty("eln.generatorChecks", "")
    private val worldName = System.getProperty("eln.generatorWorld", "generator-cold-start-expanded")
    private val mc get() = Minecraft.getInstance()
    private var phase = 0
    private var ticks = 0
    private var total = 0
    private var done = false
    private var work: CompletableFuture<Void>? = null
    private val results = mutableListOf<Map<String, Any>>()
    private val recovery = mutableListOf<String>()
    private fun origin(i: Int) = BlockPos(512 + i * 31, 65, 533)

    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if (mode !in setOf("place", "verify") || done) return
        try {
            check(++total < 6000) { "Generator check timed out in phase $phase" }
            ticks++
            when (phase) {
                0 -> if (mc.screen is TitleScreen && mc.overlay == null && ticks > 20) {
                    if (mode == "place") check(!Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(worldName))) {
                        "Fixture world already exists; choose a new -Deln.generatorWorld name"
                    }
                    phase = 1; ticks = 0
                    if (mode == "verify") mc.createWorldOpenFlows().openWorld(worldName) { error("Missing generator world") }
                    else mc.createWorldOpenFlows().createFreshLevel(worldName,
                        LevelSettings(worldName, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, GameRules(), WorldDataConfiguration.DEFAULT),
                        WorldOptions(12345L, false, false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() }, mc.screen!!)
                }
                1 -> if (mc.player != null && mc.level != null && mc.screen == null) {
                    val server = mc.singleplayerServer!!
                    work = CompletableFuture.runAsync({
                        val player = server.playerList.players.first()
                        val world = player.serverLevel()
                        player.teleportTo(world, 0.0, 72.0, 0.0, 180f, 0f)
                        player.abilities.flying = true; player.onUpdateAbilities()
                        if (mode == "place") {
                            val f = NativeCampaignFixtures(world, player)
                            repeat(8) { i ->
                                val p = origin(i)
                                for (x in -3..8) for (z in -3..3) {
                                    world.setChunkForced((p.x+x) shr 4, (p.z+z) shr 4, true)
                                    world.setBlockAndUpdate(p.offset(x,-1,z), Blocks.STONE.defaultBlockState())
                                }
                                fun cable(at: BlockPos) { f.placeSix(if (i < 4) f.requiredItem("Very High Voltage Cable") else f.spool(), at) }
                                val output = p.offset(4, 0, if (i % 4 < 2) -1 else 1)
                                if (i % 2 == 0) cable(output)
                                f.source(p.offset(-1,0,-1), Eln.VVU)
                                cable(p.north())
                                f.placeMachine(f.requiredItem("Large Shaft Motor"),p)
                                world.setBlockAndUpdate(p.east(2), Blocks.STONE.defaultBlockState())
                                f.placeMachine(f.requiredItem("Joint"),p.offset(2,1,0))
                                f.placeMachine(f.requiredItem("Large Generator"),p.east(4))
                                if (i % 2 != 0) cable(output)
                                f.load(output.east(),2000.0, Direction.XP)
                                f.ground(output.east(2))
                            }
                        }
                    }, server)
                    phase = 2; ticks = 0
                }
                2, 5 -> if (work!!.isDone && ticks > 360) {
                    work!!.join()
                    val stage = if (phase == 2) "cold-start" else "recovered"
                    val server = mc.singleplayerServer!!
                    work = CompletableFuture.runAsync({
                        val f = NativeCampaignFixtures(server.overworld(), server.playerList.players.first())
                        repeat(8) { i ->
                            val p = origin(i)
                            val generator = f.machine(p.east(4)) as GeneratorElement
                            val motor = f.machine(p) as SimpleShaftElement
                            val cable = f.six(p.offset(4,0,if(i%4<2)-1 else 1))
                            val load = cable.electricalLoadList.first()
                            val result = mapOf("case" to i, "mode" to mode, "stage" to stage, "speed" to generator.shaft.rads,
                                "sameShaft" to (generator.shaft === motor.shaft), "voltage" to load.voltage,
                                "current" to load.current, "connections" to cable.sixNode!!.nodeConnectionList.size)
                            results.add(result)
                            Eln.logger.info("GENERATOR_CHECK {}", result)
                        }
                        check(results.all { it["sameShaft"] == true && (it["speed"] as Double) > 150.0 &&
                            (it["voltage"] as Double) > 2400.0 && (it["current"] as Double) > .1 }) {
                            "Large Generator did not deliver power: $results"
                        }
                        if (mode == "place") {
                            // Half of each cable type starts the next JVM without any loaded fixture chunks.
                            for (i in listOf(1,3,5,7)) {
                                val p = origin(i)
                                for (x in -3..8) for (z in -3..3)
                                    f.world.setChunkForced((p.x+x) shr 4,(p.z+z) shr 4,false)
                            }
                        }
                    }, server)
                    phase = if (phase == 2) 3 else 6; ticks = 0
                }
                3 -> if (work!!.isDone) {
                    work!!.join()
                    if (mode == "verify") {
                        val server = mc.singleplayerServer!!
                        work = CompletableFuture.runAsync({
                            val player = server.playerList.players.first()
                            val world = player.serverLevel()
                            val f = NativeCampaignFixtures(world, player)
                            repeat(8) { i ->
                                val generator = f.machine(origin(i).east(4)) as GeneratorElement
                                val node = generator.node!!
                                val connections = node.nodeConnectionList.size
                                val energy = generator.shaft.energy
                                check(connections > 0) { "No restored generator connection in case $i" }
                                // Model an incomplete initial registration, before the final boot hook.
                                node.disconnect()
                                check(node.nodeConnectionList.isEmpty())
                                node.globalBoot()
                                check(node.nodeConnectionList.size == connections) { "Boot did not restore output connections" }
                                check(generator.shaft.energy == energy) { "Boot reset shaft energy" }
                                node.globalBoot()
                                check(node.nodeConnectionList.size == connections) { "Repeated boot duplicated connections" }
                                check(generator.shaft.energy == energy)
                                recovery.add("case $i: restores connections, preserves energy, no duplicate connections")
                            }
                        }, server)
                        phase = 5; ticks = 0
                    } else closeWorld()
                }
                6 -> if (work!!.isDone) { work!!.join(); closeWorld() }
                4 -> if (mc.singleplayerServer == null && ticks > 20) finish(null)
            }
        } catch (error: Throwable) { finish(error) }
    }

    private fun closeWorld() {
        phase = 4; ticks = 0
        mc.level!!.disconnect(); mc.disconnect(TitleScreen())
    }

    private fun finish(error: Throwable?) {
        done = true
        Files.writeString(mc.gameDirectory.toPath().resolve("generator-checks-$mode.json"),
            GsonBuilder().setPrettyPrinting().create().toJson(mapOf("world" to worldName,"results" to results,
                "recovery" to recovery,"failure" to error?.stackTraceToString())))
        if (error != null) { Eln.logger.error("GENERATOR_CHECK FAIL",error); System.exit(1) }
        else { Eln.logger.info("GENERATOR_CHECK COMPLETE {}",mode); mc.stop() }
    }
}
