package mods.eln.sim.mna

import mods.eln.sim.mna.state.VoltageState
import kotlin.test.*

class VoltageGaugeTest {
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
