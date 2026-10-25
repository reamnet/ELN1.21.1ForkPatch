package mods.eln.sim.mna

import mods.eln.sim.mna.component.CurrentSource
import org.junit.Test
import kotlin.system.measureNanoTime
import kotlin.test.*

class SparseGaugeProbeTest {
    @Test fun sparseKclRowsMatchFullValidationAcrossSourceChangesAndPreserveState() {
        for(size in listOf(128,160,320)) {
            val full=ProbeCircuit(size,true,ports=4)
            val sparse=ProbeCircuit(size,true,ports=4,gaugeRows=true)
            val initial=sparse.pins.map { it.state }
            repeat(250) { assertEquals(full.sample(it),sparse.sample(it),1e-7,"size=$size trial=$it") }
            assertEquals(initial,sparse.pins.map { it.state })
            assertTrue(sparse.system.sparseGaugeValidations>500)
            assertTrue(sparse.system.fullProbeSolves>0,"Cold probes must retain the original path")
            assertTrue(sparse.system.fullProbeSolves<full.system.fullProbeSolves/2)
            assertTrue(full.system.fullProbeSolves>10)
        }
    }
    @Test fun removedKclExcitationsNonfiniteCommandsAndTopologyChangesNeverUseStaleValidation() {
        val full=ProbeCircuit(160,true);val sparse=ProbeCircuit(160,true,gaugeRows=true)
        val a=CurrentSource("unbalanced",full.pins[0],null);val b=CurrentSource("unbalanced",sparse.pins[0],null)
        full.system.addComponent(a);sparse.system.addComponent(b)
        repeat(320) { assertEquals(full.sample(it),sparse.sample(it),1e-8) }
        assertTrue(sparse.system.sparseGaugeValidations>0)
        a.current=1.0;b.current=1.0
        assertTrue(full.system.sourceThevenin(full.probes[0]).voltage.isNaN())
        assertTrue(sparse.system.sourceThevenin(sparse.probes[0]).voltage.isNaN())
        a.current=0.0;b.current=0.0
        repeat(30) { assertEquals(full.sample(it),sparse.sample(it),1e-8) }
        full.wire.resistance=.012;sparse.wire.resistance=.012
        repeat(30) { assertEquals(full.sample(it+30),sparse.sample(it+30),1e-8) }
        full.probes[0].enabled=false;sparse.probes[0].enabled=false
        assertEquals(full.system.sourceThevenin(full.probes[0]).voltage,sparse.system.sourceThevenin(sparse.probes[0]).voltage,1e-8)
        full.feed.voltage=Double.MAX_VALUE;sparse.feed.voltage=Double.MAX_VALUE
        assertEquals(full.system.sourceThevenin(full.probes[0]).voltage.isNaN(),sparse.system.sourceThevenin(sparse.probes[0]).voltage.isNaN())
        repeat(20) { assertEquals(full.sample(it+80),sparse.sample(it+80),1e-8) }
    }
    @Test fun nonsymmetricDependentSourcesAndResistanceRebuildsKeepTheSamePhysicalProbe() {
        val full=MnaRebuildTest.Feeder(160,true,controlled=true)
        val sparse=MnaRebuildTest.Feeder(160,true,controlled=true,gaugeRows=true)
        repeat(3) { generation ->
            val a=full.rebuild(generation);val b=sparse.rebuild(generation)
            assertEquals(a.voltage,b.voltage,1e-7);assertEquals(a.resistance,b.resistance,1e-10)
            repeat(320) { trial ->
                full.source.voltage=14_000.0+trial*.0001;sparse.source.voltage=full.source.voltage
                val x=full.system.sourceThevenin(full.probe);val y=sparse.system.sourceThevenin(sparse.probe)
                assertEquals(x.voltage,y.voltage,1e-7);assertEquals(x.resistance,y.resistance,1e-10)
            }
        }
        assertTrue(sparse.system.sparseGaugeValidations>0)
        assertEquals(0L,sparse.system.affineProbeUpdates)
    }
    @Test fun probeValidationBenchmark() {
        if(!java.lang.Boolean.getBoolean("eln.benchGaugeProbeRows"))return
        for(size in listOf(80,160,320)) {
            val full=ProbeCircuit(size,true,ports=4);val sparse=ProbeCircuit(size,true,ports=4,gaugeRows=true)
            repeat(1000) { full.sample(it);sparse.sample(it) }
            fun timed(c: ProbeCircuit)=List(7) { round -> measureNanoTime { repeat(4000) { c.sample(it+5000+round*4000) } }/1e6 }.sorted()[3]
            val baseline=timed(full);val optimized=timed(sparse)
            println("GAUGE_ROWS_BENCH nodes=$size ports=4 probes=16000 baseline_ms=$baseline optimized_ms=$optimized sparse_validations=${sparse.system.sparseGaugeValidations} full_solves=${sparse.system.fullProbeSolves}")
            fun cold(rows: Boolean)=List(7) { measureNanoTime {
                repeat(8) { wave -> val c=ProbeCircuit(size,true,ports=4,gaugeRows=rows);repeat(40) { c.sample(wave*40+it) } }
            }/1e6 }.sorted()[3]
            println("GAUGE_RELOAD_BENCH nodes=$size networks=8 commands_per_network=40 ports=4 baseline_ms=${cold(false)} optimized_ms=${cold(true)}")
        }
    }
}
