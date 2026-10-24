package mods.eln.sim.mna

import org.apache.commons.numbers.core.DD
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.random.Random
import kotlin.test.*

class MnaColumnWorksetTest {
    @Test fun rowAndColumnPivotingAndChangingFillRetainExactSolutions() {
        val random = Random(148)
        for (size in listOf(4, 19, 64, 144)) for (density in listOf(.02, .15, .7)) {
            val matrix = Array(size) { row -> Array(size) { column -> DD.of(
                if (row == column) size.toDouble() else if (random.nextDouble() < density)
                    random.nextDouble(-1.0, 1.0) else 0.0
            ) } }
            var scan: MnaFactorization? = null
            var worksets: MnaFactorization? = null
            repeat(4) { generation ->
                // Changing both input order and the zero pattern exercises warm row
                // pivoting, permutation updates, new fill and removed memberships.
                val pivot = (generation + 1) % size
                val swap = matrix[0]; matrix[0] = matrix[pivot]; matrix[pivot] = swap
                matrix[1][size - 2] = if (generation % 2 == 0) DD.of(.125) else DD.ZERO
                scan = MnaFactorization(matrix, scan, true, false)
                worksets = MnaFactorization(matrix, worksets, true, true)
                repeat(12) {
                    val rhs = DoubleArray(size) { random.nextDouble(-14_000.0, 14_000.0) }
                    assertContentEquals(scan.solve(rhs), worksets.solve(rhs))
                }
                for (index in listOf(0, size / 2, size - 1))
                    assertContentEquals(scan.inverseRow(index), worksets.inverseRow(index))
            }
        }
    }

    @Test fun exactCancellationRemovesWorksetEntriesWithoutChangingResults() {
        val random = Random(1480)
        repeat(12) {
            val size = 24
            // Products of triangular integer matrices create exact elimination
            // cancellations, in addition to ordinary sparse fill.
            val lower = Array(size) { r -> DoubleArray(size) { c ->
                if (r == c) 1.0 else if (c < r && random.nextDouble() < .3) 1.0 else 0.0
            } }
            val upper = Array(size) { r -> DoubleArray(size) { c ->
                if (r == c) 2.0 else if (c > r && random.nextDouble() < .3) -2.0 else 0.0
            } }
            val matrix = Array(size) { r -> Array(size) { c -> DD.of(
                (0 until size).sumOf { k -> lower[r][k] * upper[k][c] }
            ) } }
            val scan = MnaFactorization(matrix, null, true, false)
            val worksets = MnaFactorization(matrix, null, true, true)
            repeat(12) {
                val rhs = DoubleArray(size) { random.nextDouble(-10.0, 10.0) }
                assertContentEquals(scan.solve(rhs), worksets.solve(rhs))
            }
        }
    }

    @Test fun switchedFeederRebuildsKeepReadingsCountsAndCommittedState() {
        val scan = MnaRebuildTest.Feeder(128, true, worksets = false, gaugeCopies = false)
        val worksets = MnaRebuildTest.Feeder(128, true, worksets = true)
        val initial = worksets.pins.map { it.state }
        repeat(80) { step ->
            if (step % 7 == 0) {
                scan.probe.enabled = !scan.probe.enabled
                worksets.probe.enabled = !worksets.probe.enabled
            }
            val a = scan.rebuild(step); val b = worksets.rebuild(step)
            assertEquals(a.voltage, b.voltage, 0.0)
            assertEquals(a.resistance, b.resistance, 0.0)
        }
        assertEquals(scan.system.fullFactorizations, worksets.system.fullFactorizations)
        assertTrue(worksets.system.fullFactorizations > 50)
        assertEquals(initial, worksets.pins.map { it.state })
    }

    @Test fun emptyPivotColumnsStillRejectSingularMatrices() {
        val identity = Array(4) { r -> Array(4) { c -> if (r == c) DD.ONE else DD.ZERO } }
        for (warm in listOf(false, true)) for (enabled in listOf(false, true)) {
            val previous = if (warm) MnaFactorization(identity, null, true, enabled) else null
            for (emptyRow in listOf(0, 3)) {
                val matrix = Array(4) { identity[it].clone() }
                matrix[emptyRow].fill(DD.ZERO)
                assertThrows(IllegalStateException::class.java) {
                    MnaFactorization(matrix, previous, true, enabled)
                }
            }
        }
    }

    @Test fun reportsCompleteProbeTriggeredRebuildCosts() {
        for (nodes in listOf(80, 240, 720)) {
            val scan = MnaRebuildTest.Feeder(nodes, true, worksets = false, gaugeCopies = false)
            val worksets = MnaRebuildTest.Feeder(nodes, true, worksets = true)
            repeat(12) { scan.rebuild(it); worksets.rebuild(it) }
            fun time(feeder: MnaRebuildTest.Feeder): Double {
                val start = System.nanoTime()
                repeat(8) { check(feeder.rebuild(it + 100).voltage.isFinite()) }
                return (System.nanoTime() - start) / 1e6
            }
            val baseline = ArrayList<Double>(); val optimized = ArrayList<Double>()
            repeat(6) {
                if (it % 2 == 0) { baseline += time(scan); optimized += time(worksets) }
                else { optimized += time(worksets); baseline += time(scan) }
            }
            println("MNA_WORKSET_COPY_BENCH nodes=$nodes rebuilds_per_round=8 baseline_ms=$baseline optimized_ms=$optimized")
            assertEquals(scan.system.fullFactorizations, worksets.system.fullFactorizations)
        }
    }
}
