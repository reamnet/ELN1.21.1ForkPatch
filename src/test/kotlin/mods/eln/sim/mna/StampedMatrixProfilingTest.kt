package mods.eln.sim.mna

import com.google.gson.GsonBuilder
import org.junit.Test
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Repeated source-mask trials, as observed in the shaft-bank Spark capture.
 * This measures algebraic probes, not physical steps or predicted server TPS. */
class StampedMatrixProfilingTest {
    @Test fun measureAlternatingSourceMaskTrials() {
        if (System.getenv("ELN_STAMPED_MATRIX_PROFILE") != "1") return
        val key = "eln.disableStampedMatrixReuse"
        val previous = System.getProperty(key)
        fun feeder(nodes: Int, reuse: Boolean): MnaRebuildTest.Feeder {
            System.setProperty(key, (!reuse).toString())
            return MnaRebuildTest.Feeder(nodes, true)
        }
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        if (!bean.isThreadAllocatedMemoryEnabled) bean.isThreadAllocatedMemoryEnabled = true
        val thread = Thread.currentThread().threadId()
        val reports = ArrayList<Map<String, Any>>()
        try {
            for (nodes in listOf(24, 96, 240)) {
                val baseline = feeder(nodes, false); val cached = feeder(nodes, true)
                fun probe(circuit: MnaRebuildTest.Feeder, step: Int): SubSystem.Thevenin {
                    circuit.probe.enabled = step % 2 == 0
                    circuit.source.voltage = 14_000.0 + (step % 17) * .1
                    return circuit.system.sourceThevenin(circuit.probe)
                }
                repeat(40) { step ->
                    val a = probe(baseline, step); val b = probe(cached, step)
                    assertEquals(a.voltage, b.voltage, 1e-8)
                    assertEquals(a.resistance, b.resistance, 1e-10)
                }
                val batch = 40
                fun sample(circuit: MnaRebuildTest.Feeder, round: Int): List<Double> {
                    val bytes = bean.getThreadAllocatedBytes(thread)
                    val cpu = bean.currentThreadCpuTime
                    val start = System.nanoTime()
                    repeat(batch) { probe(circuit, round * batch + it) }
                    return listOf((System.nanoTime() - start) / batch / 1e6,
                        (bean.currentThreadCpuTime - cpu) / batch / 1e6,
                        (bean.getThreadAllocatedBytes(thread) - bytes).toDouble() / batch)
                }
                val before = ArrayList<List<Double>>(); val after = ArrayList<List<Double>>()
                repeat(7) { round ->
                    if (round % 2 == 0) { before += sample(baseline, round); after += sample(cached, round) }
                    else { after += sample(cached, round); before += sample(baseline, round) }
                    val a = probe(baseline, round * batch + batch - 1)
                    val b = probe(cached, round * batch + batch - 1)
                    assertTrue(b.voltage.isFinite())
                    assertEquals(a.voltage, b.voltage, 1e-8)
                    assertEquals(a.resistance, b.resistance, 1e-10)
                }
                fun median(values: List<List<Double>>, index: Int) = values.drop(2).map { it[index] }.sorted()[2]
                val report = linkedMapOf<String, Any>("nodes" to nodes, "trialsPerRound" to batch,
                    "baselineRounds" to before, "cachedRounds" to after,
                    "baselineMs" to median(before, 0), "cachedMs" to median(after, 0),
                    "baselineCpuMs" to median(before, 1), "cachedCpuMs" to median(after, 1),
                    "baselineBytes" to median(before, 2), "cachedBytes" to median(after, 2),
                    "baselineFactorizations" to baseline.system.fullFactorizations,
                    "cachedFactorizations" to cached.system.fullFactorizations,
                    "reusedSolvers" to cached.system.reusedMatrixSolvers)
                reports += report
                println("STAMPED_MATRIX_WORKLOAD $report")
            }
            val path = Path.of(System.getProperty("eln.projectDir"), "build/reports/stamped-matrix-workloads.json")
            Files.createDirectories(path.parent)
            Files.writeString(path, GsonBuilder().setPrettyPrinting().create().toJson(reports))
        } finally {
            if (previous == null) System.clearProperty(key) else System.setProperty(key, previous)
        }
    }
}
