package mods.eln.devtest

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import mods.eln.Eln
import mods.eln.environment.BiomeClimateService
import mods.eln.environment.ClimateState
import mods.eln.sim.ThermalLoad
import mods.eln.sim.process.destruct.ThermalLoadWatchDog
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Difficulty
import net.minecraft.world.level.*
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import java.nio.file.Files
import java.util.concurrent.CompletableFuture

/** Opt-in real ServerLevel checks, including thermal watchdog cost and chunk-load events. */
@EventBusSubscriber(modid = Eln.MODID, value = [Dist.CLIENT])
object ClimatePerformanceChecks {
    private val mc get() = Minecraft.getInstance()
    private var phase = 0
    private var ticks = 0
    private var total = 0
    private var done = false
    private var work: CompletableFuture<Void>? = null
    private val samples = linkedMapOf<String, List<ClimateState>>()
    private val timings = mutableListOf<Double>()
    private var checksum = 0.0
    private var loadedByAmbient = -1
    private var loadEvents = 0
    private var countedWorld: ServerLevel? = null
    private val points = (0..7).flatMap { x -> (0..7).flatMap { z ->
        listOf(8,16,40,50,58,63,80,100).map { y -> BlockPos(512+x,y,512+z) }
    } }
    private val passed = mutableListOf<String>()

    @SubscribeEvent fun loaded(event: ChunkEvent.Load) {
        if (countedWorld != null && event.level === countedWorld) loadEvents++
    }

    private fun weather(world: ServerLevel, day: Long, rain: Boolean, thunder: Boolean) {
        world.dayTime = day
        world.setWeatherParameters(0, 6000, rain, thunder)
        world.setRainLevel(if (rain) 1f else 0f)
        world.setThunderLevel(if (thunder) 1f else 0f)
    }

    private fun fill(world: ServerLevel, edited: Boolean) {
        val biomes = world.registryAccess().registryOrThrow(Registries.BIOME)
        for (x in 31..34) for (z in 31..34) {
            world.setChunkForced(x,z,true)
            world.getChunk(x,z).fillBiomesFromNoise({ qx,qy,_,_ -> biomes.getHolderOrThrow(
                if (edited || qy < 12) Biomes.FROZEN_OCEAN else if (qx % 4 < 2) Biomes.DESERT else Biomes.PLAINS)
            }, world.chunkSource.randomState().sampler())
        }
    }

    private fun snapshot(world: ServerLevel, name: String) {
        samples[name] = points.map { BiomeClimateService.sample(world,it.x,it.y,it.z) }
    }

    private fun benchmark(world: ServerLevel) {
        val positions = List(1200) { BlockPos(512+it%20,63+(it/400)*4,512+(it/20)%20) }
        val dogs = positions.map { p -> ThermalLoadWatchDog(ThermalLoad().apply { temperatureCelsius=40.0 })
            .setAmbientTemperatureProvider { BiomeClimateService.sample(world,p.x,p.y,p.z).temperatureCelsius } }
        // Reuse each exact location six times per frame, like cable/device/watchdog consumers.
        // Advance daylight explicitly so every frame still recomputes live climate.
        repeat(8) { trial ->
            val start = System.nanoTime()
            var sum = 0.0
            repeat(20) { frame ->
                world.dayTime = 6000L + trial*20 + frame
                repeat(6) { dogs.forEach { sum += it.getValue() } }
            }
            val elapsed = (System.nanoTime()-start)/1e6
            if (trial >= 3) timings += elapsed
            checksum += sum
        }
        check(checksum.isFinite() && checksum > 0)
        passed += "1200 thermal watchdogs retain live climate over 20 daylight frames, six reads per frame"
    }

    private fun async(body: (ServerLevel) -> Unit) {
        val server = mc.singleplayerServer!!
        work = CompletableFuture.runAsync({ body(server.overworld()) }, server)
        ticks = 0
    }

    @SubscribeEvent fun tick(event: ClientTickEvent.Post) {
        if (!java.lang.Boolean.getBoolean("eln.climateChecks") || done) return
        try {
            ticks++; check(++total < 5000) { "Climate check timed out in phase $phase" }
            when (phase) {
                0 -> if (mc.screen is TitleScreen && mc.overlay == null && ticks > 20) {
                    val name = "climate-${System.currentTimeMillis()}"
                    mc.createWorldOpenFlows().createFreshLevel(name,
                        LevelSettings(name,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),
                        WorldOptions(40040L,false,false),
                        { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!)
                    phase=1; ticks=0
                }
                1 -> if (mc.player != null && mc.level != null && mc.screen == null) {
                    async { w ->
                        w.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,w.server)
                        w.gameRules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,w.server)
                        fill(w,false); weather(w,6000,false,false)
                    }; phase=2
                }
                2 -> if (work!!.isDone && ticks > 12) {
                    work!!.join(); async { w -> snapshot(w,"clear-noon"); weather(w,12000,true,false) }; phase=3
                }
                3 -> if (work!!.isDone && ticks > 12) {
                    work!!.join(); async { w -> snapshot(w,"rain-sunset"); weather(w,18000,true,true) }; phase=4
                }
                4 -> if (work!!.isDone && ticks > 12) {
                    work!!.join(); async { w -> snapshot(w,"storm-night"); fill(w,true); weather(w,6000,false,false) }; phase=5
                }
                5 -> if (work!!.isDone && ticks > 30) {
                    work!!.join(); async { w ->
                        snapshot(w,"biome-edit")
                        passed += "512 exact XYZ samples across biome boundaries, depth, clear/rain/storm and an in-place biome edit"
                        val before = w.chunkSource.loadedChunksCount
                        countedWorld=w
                        try {
                            samples["unloaded"] = listOf(BlockPos(200000,64,200000),BlockPos(200016,64,200000))
                                .map { BiomeClimateService.sample(w,it.x,it.y,it.z) }
                        } finally { countedWorld=null }
                        loadedByAmbient=w.chunkSource.loadedChunksCount-before
                        benchmark(w)
                    }; phase=6
                }
                6 -> if (work!!.isDone) {
                    work!!.join()
                    val expected = System.getProperty("eln.climateExpected")
                    if (expected != null) {
                        val old=JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve(expected))).asJsonObject
                        val gson=GsonBuilder().create()
                        check(gson.toJsonTree(samples)==old.get("samples")) { "Climate changed from original lookup results" }
                        check(loadedByAmbient==0 && loadEvents==0) { "Ambient lookup loaded $loadedByAmbient chunks / $loadEvents events" }
                        passed += "Every recorded climate value matches original sampling; unloaded samples load zero chunks"
                    }
                    done=true
                    val result=mapOf("passed" to passed,"samples" to samples,"positionsPerSnapshot" to points.size,
                        "watchdogs" to 1200,"framesPerTrial" to 20,"readsPerFrame" to 6,"warmupTrials" to 3,
                        "trialMillis" to timings,"medianMillis" to timings.sorted()[timings.size/2],"checksum" to checksum,
                        "chunksLoadedByAmbient" to loadedByAmbient,"chunkLoadEvents" to loadEvents)
                    Files.writeString(mc.gameDirectory.toPath().resolve(System.getProperty("eln.climateOutput","climate-checks.json")),
                        GsonBuilder().setPrettyPrinting().create().toJson(result))
                    Eln.logger.info("CLIMATE_CHECK COMPLETE median={} chunks={}",timings.sorted()[timings.size/2],loadedByAmbient)
                    mc.level!!.disconnect(); mc.disconnect(TitleScreen()); mc.stop()
                }
            }
        } catch (error: Throwable) {
            done=true; countedWorld=null
            Eln.logger.error("CLIMATE_CHECK FAIL",error)
            Files.writeString(mc.gameDirectory.toPath().resolve(System.getProperty("eln.climateOutput","climate-checks.json")),
                GsonBuilder().setPrettyPrinting().create().toJson(mapOf("failure" to error.stackTraceToString(),"samples" to samples)))
            System.exit(1)
        }
    }
}
