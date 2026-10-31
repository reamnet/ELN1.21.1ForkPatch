package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.lightblock.LightBlock
import mods.eln.lightblock.LightBlockEntity
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.HybridNodeDirection
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.TransparentNode
import mods.eln.sixnode.lampsocket.LampSocketDescriptor
import mods.eln.sixnode.lampsocket.LampSocketElement
import mods.eln.sixnode.lampsocket.LampSocketProcess
import mods.eln.transparentnode.floodlight.FloodlightDescriptor
import mods.eln.transparentnode.floodlight.FloodlightElement
import mods.eln.transparentnode.floodlight.FloodlightProcess
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import java.nio.file.Files
import java.nio.file.Path

/** Opt-in dedicated-server reproduction; never run against a player's world. */
@EventBusSubscriber(modid = Eln.MODID)
object LightLifetimePerformanceChecks {
    private var world: ServerLevel? = null
    private var loads = 0

    @SubscribeEvent fun chunk(event: ChunkEvent.Load) {
        if (event.level === world) loads++
    }

    // Read either release's storage without adding diagnostic APIs to the block entity.
    private fun entries(entity: LightBlockEntity): Int {
        val legacy = entity.javaClass.declaredFields.firstOrNull { it.name == "lightList" }
        if (legacy != null) {
            legacy.isAccessible = true
            return (legacy.get(entity) as List<*>).size
        }
        val field = entity.javaClass.getDeclaredField("leases").apply { isAccessible = true }
        val leases = field.get(entity)
        return leases.javaClass.getMethod("getActiveLevels").invoke(leases) as Int
    }

    @SubscribeEvent fun start(event: ServerStartedEvent) {
        if (!java.lang.Boolean.getBoolean("eln.lightLifetimeChecks")) return
        val report = linkedMapOf<String, Any>()
        var failure: Throwable? = null
        val w = event.server.overworld()
        val baseline = java.lang.Boolean.getBoolean("eln.lightLifetimeBaseline")
        world = w
        try {
            val pos = BlockPos(32000, 80, 32000)
            w.getChunk(pos)
            check(!w.shouldTickBlocksAt(pos.asLong())) { "Fixture must start in a resident, non-ticking chunk" }
            LightBlockEntity.addLight(w, pos.x, pos.y, pos.z, 15, 1)
            val entity = w.getBlockEntity(pos) as LightBlockEntity
            val refreshes = 150_000
            var began = System.nanoTime()
            repeat(refreshes) { LightBlockEntity.addLight(w, pos.x, pos.y, pos.z, 15, 1) }
            val refreshMs = (System.nanoTime() - began) / 1e6
            val pending = entries(entity)
            began = System.nanoTime()
            entity.update()
            val resumeMs = (System.nanoTime() - began) / 1e6
            entity.update()
            check(w.getBlockState(pos).isAir)
            report["pausedRefresh"] = mapOf("refreshes" to refreshes, "pendingEntries" to pending,
                "refreshMilliseconds" to refreshMs, "resumeMilliseconds" to resumeMs)
            if (!baseline) check(pending <= 16) { "Paused light accumulated $pending entries" }

            w.setBlock(pos, Blocks.WATER.defaultBlockState(), 3)
            LightBlockEntity.addLight(w, pos.x, pos.y, pos.z, 15, 1)
            LightBlockEntity.addLight(w, pos.x, pos.y, pos.z, 8, 3)
            val waterLight = w.getBlockEntity(pos) as LightBlockEntity
            val levels = mutableListOf<Int>()
            repeat(3) { waterLight.update(); levels += w.getBlockState(pos).getValue(LightBlock.LIGHT) }
            check(levels == listOf(15, 8, 8))
            waterLight.update()
            check(w.getBlockState(pos).block === Blocks.WATER && w.getBlockState(pos).fluidState.isSource)
            report["overlappingWaterLight"] = mapOf("levels" to levels, "waterRestored" to true)

            fun passive(name: String, at: BlockPos, setup: () -> (() -> Unit)) {
                val action = setup()
                val before = w.chunkSource.loadedChunksCount
                loads = 0
                action()
                val added = w.chunkSource.loadedChunksCount - before
                report[name] = mapOf("chunkEntries" to added, "loadEvents" to loads)
                if (!baseline) check(added == 0 && loads == 0) { "$name loaded $added chunk entries / $loads events at $at" }
            }
            val absent = BlockPos(33024, 80, 32000)
            passive("absentLightRefresh", absent) {
                check(w.chunkSource.getChunkNow(absent.x shr 4, absent.z shr 4) == null)
                val action: () -> Unit = { LightBlockEntity.addLight(w, absent.x, absent.y, absent.z, 12, 5) }
                action
            }
            val spot = BlockPos(35087, 80, 34000)
            passive("legacySpotBeam", spot) {
                w.getChunk(spot)
                val descriptor = Eln.sixNodeItem.subItemList.values.filterIsInstance<LampSocketDescriptor>()
                    .first { it.fixture == null && it.range > 0 }
                val node = SixNode().apply { coordinate = Coordinate(spot.x, spot.y, spot.z, w) }
                val process = LampSocketProcess(LampSocketElement(node, Direction.XN, descriptor))
                val ray = process.javaClass.getDeclaredMethod("raytrace", Vec3::class.java, Int::class.javaPrimitiveType)
                    .apply { isAccessible = true }
                val action: () -> Unit = { ray.invoke(process, Vec3(1.0, 0.0, 0.0), 0); Unit }
                action
            }
            val flood = BlockPos(36111, 80, 34000)
            passive("floodlightBeam", flood) {
                w.getChunk(flood)
                val descriptor = Eln.transparentNodeItem.subItemList.values.filterIsInstance<FloodlightDescriptor>().first()
                val node = TransparentNode().apply { coordinate = Coordinate(flood.x, flood.y, flood.z, w) }
                val element = FloodlightElement(node, descriptor).apply {
                    rotationAxis = HybridNodeDirection.YP
                    blockFacing = HybridNodeDirection.XP
                }
                val process = FloodlightProcess(element)
                val beam = process.javaClass.getDeclaredMethod("placeSpots", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .apply { isAccessible = true }
                val action: () -> Unit = { beam.invoke(process, 15, 32); Unit }
                action
            }
        } catch (t: Throwable) {
            failure = t
        } finally {
            world = null
            val out = Path.of(System.getProperty("eln.lightLifetimeReport", "light-lifetime-report.json"))
            Files.createDirectories(out.toAbsolutePath().parent)
            Files.writeString(out, GsonBuilder().setPrettyPrinting().create().toJson(
                mapOf("passed" to (failure == null), "failure" to failure?.stackTraceToString(), "observations" to report)))
            Eln.LOGGER.info("LIGHT_LIFETIME_CHECKS {} {}", if (failure == null) "passed" else "failed", report)
            event.server.halt(false)
        }
    }
}
