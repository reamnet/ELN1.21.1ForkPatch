package mods.eln.sim.mna

import mods.eln.sim.mna.state.VoltageState
import org.apache.commons.numbers.core.DD
import org.junit.Test
import java.util.function.IntFunction
import kotlin.test.*

class CompactGaugeProbeTest {
    @Test fun compressedKeysRetainDiscardedCurrentsAndDoNotShareFullSolveCacheResults() {
        val gauge=VoltageGauge(arrayOf(doubleArrayOf(1.0,-1.0),doubleArrayOf(-1.0,1.0)),List(2) { VoltageState() })
        val inverse=arrayOf(arrayOf(DD.ONE,DD.ZERO),arrayOf(DD.ONE,DD.ONE))
        var rows=0
        val response=IntFunction<Array<DD>> { rows++;inverse[it] }
        fun compact(rhs:DoubleArray):Boolean? {
            val active=rhs.indices.filter { rhs[it]!=0.0 }.toIntArray()
            return gauge.validProbeRowsCompact(rhs,active,active.size,response)
        }
        val rhs=doubleArrayOf(1.0,-1.0)
        assertEquals(true,compact(rhs));val initial=rows
        repeat(10) { assertEquals(true,compact(rhs)) };assertEquals(initial,rows)
        // Only a discarded KCL RHS changes. Ignoring it would silently invent a ground.
        rhs[0]=2.0;assertEquals(false,compact(rhs))
        assertFalse(gauge.validProbe(rhs,java.util.function.Function { it.copyOf() }))
        rhs[0]=1.0;assertEquals(true,compact(rhs))
        assertFalse(gauge.validProbe(doubleArrayOf(5.0,-1.0),java.util.function.Function { it.copyOf() }))
        assertEquals(true,compact(rhs))
        assertEquals(true,compact(doubleArrayOf(0.0,0.0)))
        assertEquals(false,compact(doubleArrayOf(0.0,-1.0)))
        assertEquals(true,compact(doubleArrayOf(3.0,-3.0)))
    }

    @Test fun completePortResultsMatchThePreviousValidatorAcrossChangingSourceCommandsAndTopology() {
        val compact=ProbeCircuit(160,true,ports=4,gaugeRows=true,compactGauge=true)
        val previous=ProbeCircuit(160,true,ports=4,gaugeRows=true,compactGauge=false)
        fun sample(offset:Int) { repeat(220) { i->assertEquals(previous.sample(i+offset),compact.sample(i+offset),1e-8) } }
        sample(0)
        assertTrue(compact.system.compactGaugeValidations>500)
        assertEquals(0,previous.system.compactGaugeValidations)
        compact.wire.resistance=.01;previous.wire.resistance=.01;sample(220)
        compact.probes[1].enabled=false;previous.probes[1].enabled=false;sample(440)
        compact.probes[1].enabled=true;previous.probes[1].enabled=true;sample(660)
        val before=compact.pins.map { it.state }
        compact.sample(1200);assertEquals(before,compact.pins.map { it.state })
    }

    @Test fun benchmarkRepeatedTransformerProbeValidation() {
        if(!java.lang.Boolean.getBoolean("eln.compactGaugeBenchmark"))return
        val compact=ProbeCircuit(384,true,ports=8,gaugeRows=true,compactGauge=true)
        val previous=ProbeCircuit(384,true,ports=8,gaugeRows=true,compactGauge=false)
        repeat(400) { assertEquals(previous.sample(it),compact.sample(it),1e-7) }
        fun round(circuit:ProbeCircuit):Double {
            val began=System.nanoTime();repeat(1600) { circuit.sample(it+400) }
            return (System.nanoTime()-began)/1e6
        }
        val old=mutableListOf<Double>();val current=mutableListOf<Double>()
        repeat(7) { index->
            if(index%2==0) { old+=round(previous);current+=round(compact) } else { current+=round(compact);old+=round(previous) }
        }
        println("COMPACT_GAUGE_BENCH nodes=384 ports=8 probes_per_round=12800 previous_median_ms=${old.drop(2).sorted()[2]} compact_median_ms=${current.drop(2).sorted()[2]} validations=${compact.system.compactGaugeValidations}")
    }
}
