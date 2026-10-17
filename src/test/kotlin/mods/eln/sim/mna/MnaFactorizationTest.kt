package mods.eln.sim.mna

import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.VoltageState
import mods.eln.sim.power.probePort
import org.apache.commons.numbers.core.DD
import kotlin.random.Random
import kotlin.test.*

class MnaFactorizationTest {
    @Test fun pivotedSolveAndCachedInverseRowsSatisfyIndependentResiduals() {
        val random = Random(103)
        for (size in listOf(3, 24, 80)) {
            val matrix = Array(size) { r -> Array(size) { c ->
                DD.of(if (r == c) 10.0 else if (random.nextDouble() < .1) random.nextDouble(-1.0, 1.0) else 0.0)
            } }
            // Force row pivoting, including previously stored L columns.
            val first = matrix[0]; matrix[0] = matrix[size - 1]; matrix[size - 1] = first
            val rhs = DoubleArray(size) { random.nextDouble(-100.0, 100.0) }
            val factor = MnaFactorization(matrix)
            val value = factor.solve(rhs)
            for (r in 0 until size) {
                var residual = DD.of(-rhs[r])
                for (c in 0 until size) residual = residual.add(matrix[r][c].multiply(value[c]))
                assertTrue(residual.abs().toDouble() < 1e-25, "row=$r residual=$residual")
            }
            for (index in listOf(0, size / 2, size - 1)) {
                val row = factor.inverseRow(index)
                assertSame(row, factor.inverseRow(index))
                for (c in 0 until size) {
                    var residual = DD.of(if (index == c) -1.0 else 0.0)
                    for (r in 0 until size) residual = residual.add(row[r].multiply(matrix[r][c]))
                    assertTrue(residual.abs().toDouble() < 1e-25)
                }
            }
            // Reuse the symbolic order after both coefficient and constraint changes.
            matrix[0][size / 2] = matrix[0][size / 2].add(.123)
            val reused = MnaFactorization(matrix, factor)
            assertTrue(reused.reusedOrdering)
            val next = reused.solve(rhs)
            for (r in 0 until size) {
                var residual = DD.of(-rhs[r])
                for (c in 0 until size) residual = residual.add(matrix[r][c].multiply(next[c]))
                assertTrue(residual.abs().toDouble() < 1e-25)
            }
        }
    }

    @Test fun largeGroundedAndFloatingCircuitsKeepTinyCurrentsAndDoNotRebuildForOpenProbes() {
        for (floating in listOf(false, true)) {
            val system = SubSystem(null, .01)
            val pins = List(80) { VoltageState().also(system::addState) }
            val reference = if (floating) pins.last() else null
            if (!floating) system.addComponent(Resistor(pins.last(), null).apply { resistance = 10.0 })
            system.addComponent(VoltageSource("grid", pins[0], reference).apply { voltage = 3200.0 })
            for (i in 1..78) system.addComponent(Resistor(pins[i - 1], pins[i]).apply { resistance = .0001 })
            val load = Resistor(pins[78], reference).apply { resistance = 1e9 }
            system.addComponent(load)
            val source = SwitchableVoltageSource("open input").apply { connectTo(pins[78], reference) }
            system.addComponent(source)
            system.step()
            assertEquals(3200.0 / (1e9 + .0078), load.current, 1e-14)
            val expectedVolts = pins[78].voltage - (reference?.voltage ?: 0.0)
            val count = system.fullFactorizations
            repeat(50) {
                val port = probePort(pins[78], reference, source)
                assertEquals(expectedVolts, port.volts, 1e-7)
                assertEquals(.0078, port.ohms, 1e-10)
                assertFalse(source.enabled)
            }
            assertEquals(count, system.fullFactorizations, "An open probe changed the matrix")
            source.enabled = true; source.voltage = 120.0
            val active = probePort(pins[78], reference, source)
            assertEquals(expectedVolts, active.volts, 1e-7)
            assertEquals(.0078, active.ohms, 1e-10)
            system.addComponent(VoltageSource("conflicting grid", pins[0], reference).apply { voltage = 1.0 })
            system.stepCalc()
            assertFalse(system.hasValidStepSolution(), "A contradictory large network was accepted")
        }
    }

    @Test fun singularCurrentConstraintsAreNotRepairedByTheFactorization() {
        var rejected = false
        try {
            MnaFactorization(arrayOf(arrayOf(DD.ONE, DD.ONE), arrayOf(DD.ONE, DD.ONE)))
        } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
    }
}
