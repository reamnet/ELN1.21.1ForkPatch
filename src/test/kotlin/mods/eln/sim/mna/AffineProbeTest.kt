package mods.eln.sim.mna

import mods.eln.sim.mna.component.*
import mods.eln.sim.mna.state.State
import mods.eln.sim.mna.state.VoltageState
import org.junit.Test
import kotlin.math.sin
import kotlin.test.*

internal class ProbeCircuit(size: Int,optimized: Boolean,asymmetric: Boolean=false,ports: Int=1,gaugeRows: Boolean=false,compactGauge: Boolean=true) {
    val system: SubSystem
    val pins=List(size) { VoltageState() }
    val feed=VoltageSource("floating feed",pins[0],pins.last()).apply { voltage=14_000.0 }
    val probes=List(ports) { i -> SwitchableVoltageSource("port$i").apply {
        connectTo(pins[1+i*(size-2)/ports],pins.last());enabled=true;voltage=13_900.0
    } }
    val wire=Resistor(pins[0],pins[1]).apply { resistance=.0001 }
    init {
        val key="eln.disableAffineProbeUpdates";val saved=System.getProperty(key)
        val rowsKey="eln.disableSparseGaugeProbeRows";val savedRows=System.getProperty(rowsKey)
        val compactKey="eln.disableCompactGaugeProbes";val savedCompact=System.getProperty(compactKey)
        System.setProperty(compactKey,(!compactGauge).toString())
        System.setProperty(rowsKey,(!gaugeRows).toString())
        try { System.setProperty(key,(!optimized).toString());system=SubSystem(null,.01) }
        finally { if(savedCompact==null)System.clearProperty(compactKey) else System.setProperty(compactKey,savedCompact);if(savedRows==null)System.clearProperty(rowsKey) else System.setProperty(rowsKey,savedRows);if(saved==null)System.clearProperty(key) else System.setProperty(key,saved) }
        pins.forEach(system::addState);system.addComponent(feed);system.addComponent(wire)
        for(i in 2 until size-1)system.addComponent(Resistor(pins[i-1],pins[i]).apply { resistance=.0001 })
        system.addComponent(Resistor(pins[size-2],pins.last()).apply { resistance=1e6 })
        probes.forEach(system::addComponent)
        if(asymmetric)system.addComponent(object: Component() {
            // Physical voltage-controlled current source: reciprocal optimization must decline it.
            override fun getConnectedStates()=arrayOf<State>(pins[2],pins[3],pins[4],pins.last())
            override fun applyToSubsystem(s: SubSystem) {
                s.addToA(pins[2],pins[4],.001);s.addToA(pins[2],pins.last(),-.001)
                s.addToA(pins[3],pins[4],-.001);s.addToA(pins[3],pins.last(),.001)
            }
        })
        system.step()
    }
    fun sample(step: Int): Double {
        feed.voltage=14_000+sin(step*.17)*.1
        var result=0.0
        for((i,probe) in probes.withIndex()) {
            probe.voltage=13_900+sin(step*.11+i)*.2
            val th=system.sourceThevenin(probe)
            check(th.voltage.isFinite() && th.resistance.isFinite())
            result+=th.voltage+th.resistance
        }
        return result
    }
}

class AffineProbeTest {
    @Test fun reciprocalResponsesMatchFullSolvesWithoutCommittingStates() {
        for(size in listOf(12,80)) {
            val cached=ProbeCircuit(size,true,ports=4);val full=ProbeCircuit(size,false,ports=4)
            val initial=cached.pins.map { it.state }
            repeat(600) { i ->
                assertEquals(full.sample(i),cached.sample(i),1e-7,"size=$size step=$i")
                cached.probes.zip(full.probes).forEach { (a,b) ->
                    val expected=full.system.sourceThevenin(b);val actual=cached.system.sourceThevenin(a)
                    assertEquals(expected.voltage,actual.voltage,1e-8)
                    assertEquals(expected.resistance,actual.resistance,1e-11)
                    assertTrue(a.enabled);assertEquals(b.voltage,a.voltage)
                }
            }
            assertEquals(initial,cached.pins.map { it.state },"A probe committed physical state")
            assertTrue(cached.system.affineProbeUpdates>1000)
            assertTrue(cached.system.fullProbeSolves<full.system.fullProbeSolves/4,
                "cached=${cached.system.fullProbeSolves} full=${full.system.fullProbeSolves}")
        }
    }
    @Test fun resistorAndMaskChangesRebuildResponsesBeforeTheNextProbe() {
        val cached=ProbeCircuit(80,true);val full=ProbeCircuit(80,false)
        repeat(50) { assertEquals(full.sample(it),cached.sample(it),1e-8) }
        cached.wire.resistance=.01;full.wire.resistance=.01
        var count=cached.system.fullProbeSolves
        assertEquals(full.sample(51),cached.sample(51),1e-8)
        assertTrue(cached.system.fullProbeSolves>count)
        cached.probes[0].enabled=false;full.probes[0].enabled=false
        count=cached.system.fullProbeSolves
        repeat(50) { assertEquals(full.sample(it+52),cached.sample(it+52),1e-8) }
        assertTrue(cached.system.fullProbeSolves>count)
        cached.probes[0].enabled=true;full.probes[0].enabled=true
        assertEquals(full.sample(103),cached.sample(103),1e-8)
        // An overflowing speculative command must not poison later normal probes.
        cached.feed.voltage=Double.MAX_VALUE;full.feed.voltage=Double.MAX_VALUE
        assertEquals(full.system.sourceThevenin(full.probes[0]).voltage.isNaN(),
            cached.system.sourceThevenin(cached.probes[0]).voltage.isNaN())
        assertEquals(full.sample(104),cached.sample(104),1e-8)
    }
    @Test fun changesOnlyInDiscardedKclRowsStillRejectUnbalancedFloatingCurrent() {
        val cached=ProbeCircuit(80,true)
        val unbalanced=CurrentSource("unbalanced",cached.pins[0],null)
        cached.system.addComponent(unbalanced)
        repeat(20) { cached.sample(it) }
        unbalanced.setCurrent(1.0)
        val rejected=cached.system.sourceThevenin(cached.probes[0])
        assertTrue(rejected.voltage.isNaN() && rejected.resistance.isNaN())
        unbalanced.setCurrent(0.0)
        assertTrue(cached.system.sourceThevenin(cached.probes[0]).voltage.isFinite())
        cached.system.addComponent(CurrentSource("paired",cached.pins[4],cached.pins[0]).apply { setCurrent(1.0) })
        assertTrue(cached.system.sourceThevenin(cached.probes[0]).voltage.isFinite())
        // Global current balance cannot hide opposite faults on separate islands.
        val other=VoltageState();val otherReturn=VoltageState()
        cached.system.addState(other);cached.system.addState(otherReturn)
        val otherFeed=VoltageSource("other island",other,otherReturn).apply { voltage=24.0 }
        cached.system.addComponent(otherFeed)
        cached.system.addComponent(Resistor(other,otherReturn).apply { resistance=10.0 })
        val opposite=CurrentSource("opposite fault",other,null)
        cached.system.addComponent(opposite)
        cached.sample(25)
        unbalanced.setCurrent(1.0);opposite.setCurrent(-1.0)
        assertTrue(cached.system.sourceThevenin(cached.probes[0]).voltage.isNaN())
        unbalanced.setCurrent(0.0);opposite.setCurrent(0.0)
        repeat(40) { otherFeed.voltage=24+sin(it*.1);cached.sample(it+26) }
    }
    @Test fun nonreciprocalMatricesRetainTheOriginalFullSolvePath() {
        val cached=ProbeCircuit(80,true,true);val full=ProbeCircuit(80,false,true)
        repeat(100) { assertEquals(full.sample(it),cached.sample(it),1e-8) }
        assertEquals(0L,cached.system.affineProbeUpdates)
        assertTrue(cached.system.fullProbeSolves>=100)
    }
    @Test fun reportsAlternatingBaselineAndOptimizedProbeCosts() {
        for(size in listOf(80,240)) {
            val cached=ProbeCircuit(size,true,ports=8);val full=ProbeCircuit(size,false,ports=8)
            repeat(100) { full.sample(it);cached.sample(it) }
            fun run(c: ProbeCircuit): Double {
                val start=System.nanoTime();repeat(500) { c.sample(it+100) }
                return (System.nanoTime()-start)/1e6
            }
            val baseline=ArrayList<Double>();val optimized=ArrayList<Double>()
            repeat(4) { if(it%2==0) { baseline+=run(full);optimized+=run(cached) } else { optimized+=run(cached);baseline+=run(full) } }
            println("AFFINE_PROBE_BENCH nodes=$size ports=8 probes_per_round=4000 baseline_ms=$baseline optimized_ms=$optimized full_solves=${cached.system.fullProbeSolves} affine_updates=${cached.system.affineProbeUpdates}")
            assertEquals(full.sample(1000),cached.sample(1000),1e-7)
        }
    }
}
