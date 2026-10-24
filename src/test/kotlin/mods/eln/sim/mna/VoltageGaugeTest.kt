package mods.eln.sim.mna

import mods.eln.sim.mna.state.VoltageState
import kotlin.test.*

class VoltageGaugeTest {
    @Test fun readOnlySelectionHonorsEarlierReferencesInNonsymmetricMatrices() {
        // With no coefficient for the first unknown, its reference row is
        // selected first. The following group's test must see that virtual
        // unit row, exactly as the original in-place algorithm does.
        val physical = arrayOf(doubleArrayOf(0.0, 1.0), doubleArrayOf(0.0, 0.0))
        val legacy = Array(2) { physical[it].copyOf() }
        val states = List(2) { VoltageState() }
        val oldGauge = VoltageGauge(legacy, states)
        val gauge = VoltageGauge(physical, states, true)
        val precise = Array(2) { r -> Array(2) { c -> org.apache.commons.numbers.core.DD.of(physical[r][c]) } }
        val references = gauge.referenceCopy(precise)
        for (row in 0 until 2) {
            assertEquals(oldGauge.isReference(row), gauge.isReference(row))
            assertTrue(gauge.isReference(row))
            assertContentEquals(legacy[row], references[row].map { it.toDouble() }.toDoubleArray())
        }
        assertContentEquals(doubleArrayOf(0.0, 1.0), physical[0])
        gauge.applyRhs(doubleArrayOf(1.0, 0.0))
        assertFalse(gauge.valid(doubleArrayOf(0.0, 0.0)))
    }

    @Test fun referenceCopiesPreservePhysicalStampsAndDiscardedEquationsForMultipleIslands() {
        val physical = arrayOf(
            doubleArrayOf(1.0, -1.0, 0.0, 0.0),
            doubleArrayOf(-1.0, 1.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, 2.0, -2.0),
            doubleArrayOf(0.0, 0.0, -2.0, 2.0)
        )
        val legacy = Array(4) { physical[it].copyOf() }
        val states = List(4) { VoltageState() }
        val oldGauge = VoltageGauge(legacy, states)
        val gauge = VoltageGauge(physical, states, true)
        val precise = Array(4) { row -> Array(4) { col -> org.apache.commons.numbers.core.DD.of(physical[row][col]) } }
        val references = gauge.referenceCopy(precise)
        for (row in 0 until 4) {
            assertContentEquals(legacy[row], references[row].map { it.toDouble() }.toDoubleArray())
            for (col in 0 until 4) assertEquals(physical[row][col], precise[row][col].toDouble())
            if (gauge.isReference(row)) assertNotSame(precise[row], references[row])
            else assertSame(precise[row], references[row])
        }
        // Later changes to a caller's physical matrix cannot corrupt the saved
        // discarded equations. An unbalanced island must still be rejected.
        physical[0].fill(0.0); physical[2].fill(0.0)
        for (rhs in listOf(doubleArrayOf(1.0, -1.0, 2.0, -2.0), doubleArrayOf(3.0, -1.0, 2.0, -2.0))) {
            oldGauge.applyRhs(rhs.copyOf()); gauge.applyRhs(rhs.copyOf())
            assertEquals(oldGauge.valid(doubleArrayOf(0.0, -1.0, 0.0, -1.0)),
                gauge.valid(doubleArrayOf(0.0, -1.0, 0.0, -1.0)))
        }
    }

    @Test fun referenceCopiesKeepGroundedMatricesAndPhysicalDiagnosticsUnchanged() {
        val system = SubSystem(null, .01)
        val a = VoltageState(); val b = VoltageState()
        system.addState(a); system.addState(b)
        val source = mods.eln.sim.mna.component.VoltageSource("feed", a, b).apply { voltage = 12.0 }
        val port = mods.eln.sim.mna.component.SwitchableVoltageSource("probe").apply { connectTo(a, b) }
        system.addComponent(source); system.addComponent(port)
        system.addComponent(mods.eln.sim.mna.component.Resistor(a, b).apply { resistance = 2.0 })
        system.sourceThevenin(port)
        val snapshot = system.captureDebugSnapshot()
        assertEquals(.5, snapshot.conductanceMatrix[0][0], 0.0)
        assertEquals(-.5, snapshot.conductanceMatrix[0][1], 0.0)
        val grounded = arrayOf(doubleArrayOf(2.0, -1.0), doubleArrayOf(-1.0, 1.0))
        val gauge = VoltageGauge(grounded, listOf(a, b), true)
        assertFalse(gauge.active)
        val precise = Array(2) { r -> Array(2) { c -> org.apache.commons.numbers.core.DD.of(grounded[r][c]) } }
        val references = gauge.referenceCopy(precise)
        for (row in 0 until 2) assertSame(precise[row], references[row])
        assertContentEquals(doubleArrayOf(2.0, -1.0), grounded[0])
    }

    @Test fun probeValidationReusesOnlyIdenticalPhysicalCurrentsAndRetainsDiscardedEquations() {
        val matrix=arrayOf(doubleArrayOf(1.0,-1.0),doubleArrayOf(-1.0,1.0))
        val gauge=VoltageGauge(matrix,List(2) { VoltageState() })
        var solves=0
        val solve=java.util.function.Function<DoubleArray,DoubleArray> { solves++;it.copyOf() }
        val physical=doubleArrayOf(1.0,-1.0)
        repeat(20) { assertTrue(gauge.validProbe(physical,solve)) }
        assertEquals(1,solves)
        // Only a discarded equation changes: comparing the gauged RHS would miss this fault.
        physical[0]=2.0
        assertFalse(gauge.validProbe(physical,solve));assertEquals(2,solves)
        physical[0]=1.0
        assertTrue(gauge.validProbe(physical,solve));assertEquals(3,solves)
        repeat(20) { assertTrue(gauge.supportsProbe(0,1,physical,solve)) }
        assertEquals(4,solves)
        repeat(20) { assertFalse(gauge.supportsProbe(0,-1,physical,solve)) }
        assertEquals(5,solves)
        // An excitation must not leave its discarded current in the physical validator.
        assertTrue(gauge.valid(doubleArrayOf(0.0,-1.0)))
    }

    @Test fun aNewGroundConnectionInvalidatesBothProbeCaches() {
        val system=SubSystem(null,.01)
        val a=VoltageState();val b=VoltageState()
        system.addState(a);system.addState(b)
        val grid=mods.eln.sim.mna.component.VoltageSource("floating feed",a,b).apply { voltage=12.0 }
        val port=mods.eln.sim.mna.component.SwitchableVoltageSource("probe").apply { connectTo(a,null) }
        system.addComponent(grid);system.addComponent(port)
        repeat(3) { assertTrue(system.sourceThevenin(port).resistance.isInfinite()) }
        val ground=mods.eln.sim.mna.component.Resistor(b,null).apply { resistance=1.0 }
        system.addComponent(ground)
        repeat(3) {
            val th=system.sourceThevenin(port);assertEquals(12.0,th.voltage,1e-10);assertEquals(1.0,th.resistance,1e-10)
        }
        grid.voltage=24.0
        assertEquals(24.0,system.sourceThevenin(port).voltage,1e-10)
        system.removeComponent(ground)
        assertTrue(system.sourceThevenin(port).resistance.isInfinite())
    }

    @Test fun separateFloatingIslandsRetainTheirOwnDiscardedCurrentsAcrossProbes() {
        val matrix = arrayOf(
            doubleArrayOf(1.0, -1.0, 0.0, 0.0),
            doubleArrayOf(-1.0, 1.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0, -1.0),
            doubleArrayOf(0.0, 0.0, -1.0, 1.0)
        )
        val gauge = VoltageGauge(matrix, List(4) { VoltageState() })
        assertTrue(gauge.active)
        val physical = doubleArrayOf(1.0, -1.0, 2.0, -2.0)
        val rhs = physical.copyOf()
        gauge.applyRhs(rhs)
        assertContentEquals(doubleArrayOf(0.0, -1.0, 0.0, -2.0), rhs)
        assertTrue(gauge.valid(doubleArrayOf(0.0, -1.0, 0.0, -2.0)))
        gauge.applyRhs(doubleArrayOf(3.0, -3.0, 5.0, -5.0))
        assertFalse(gauge.valid(doubleArrayOf(0.0, -1.0, 0.0, -2.0)))
        assertTrue(gauge.valid(doubleArrayOf(0.0, -3.0, 0.0, -5.0)))
        gauge.applyRhs(physical.copyOf())
        assertTrue(gauge.valid(doubleArrayOf(0.0, -1.0, 0.0, -2.0)))
        assertFalse(gauge.valid(doubleArrayOf(0.0, Double.NaN, 0.0, -2.0)))
    }
}
