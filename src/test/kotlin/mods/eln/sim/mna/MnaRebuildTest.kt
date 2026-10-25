package mods.eln.sim.mna

import mods.eln.sim.mna.component.*
import mods.eln.sim.mna.state.State
import mods.eln.sim.mna.state.VoltageState
import org.apache.commons.numbers.core.DD
import org.junit.Test
import kotlin.random.Random
import kotlin.test.*

class MnaRebuildTest {
    @Test fun denseAndSparseWorkspacesRetainTheSamePivotsAndPreciseResults() {
        val random=Random(147)
        for(size in listOf(5,32,96)) {
            val matrix=Array(size) { r -> Array(size) { c -> DD.of(
                if(r==c)10.0+r*.01 else if(random.nextDouble()<.08)random.nextDouble(-1.0,1.0) else 0.0) } }
            val row=matrix[0];matrix[0]=matrix.last();matrix[size-1]=row
            var dense: MnaFactorization?=null;var sparse: MnaFactorization?=null
            repeat(4) { generation ->
                matrix[1][size-2]=if(generation%2==0)DD.of(.03125) else DD.ZERO
                matrix[2][1]=matrix[2][1].add(.01)
                dense=MnaFactorization(matrix,dense,false);sparse=MnaFactorization(matrix,sparse,true)
                assertEquals(generation>0,sparse!!.reusedOrdering)
                repeat(20) {
                    val rhs=DoubleArray(size) { random.nextDouble(-14_000.0,14_000.0) }
                    assertContentEquals(dense!!.solve(rhs),sparse!!.solve(rhs))
                }
                for(index in listOf(0,size/2,size-1))assertContentEquals(dense!!.inverseRow(index),sparse!!.inverseRow(index))
            }
        }
    }

    internal class Feeder(nodes: Int,sparse: Boolean,controlled: Boolean=false,worksets: Boolean=true,gaugeCopies: Boolean=true,gaugeRows: Boolean=false) {
        val system: SubSystem
        val pins=List(nodes) { VoltageState() }
        val reference=pins.last()
        val source=VoltageSource("grid",pins[0],reference).apply { voltage=14_000.0 }
        val probe=SwitchableVoltageSource("UPS input").apply { connectTo(pins[nodes-2],reference);enabled=true;voltage=13_900.0 }
        val wires=ArrayList<Resistor>()
        var gain=if(controlled).001 else 0.0
        val control=object: Component() {
            override fun getConnectedStates()=arrayOf<State>(pins[2],pins[3],pins[4],reference)
            override fun applyToSubsystem(s: SubSystem) {
                s.addToA(pins[2],pins[4],gain);s.addToA(pins[2],reference,-gain)
                s.addToA(pins[3],pins[4],-gain);s.addToA(pins[3],reference,gain)
            }
        }
        init {
            val settings = mapOf("eln.disableSparseMnaRebuild" to !sparse, "eln.disableMnaColumnWorksets" to !worksets,
                "eln.disableMnaGaugeCopyReduction" to !gaugeCopies,"eln.disableSparseGaugeProbeRows" to !gaugeRows)
            val saved = settings.mapValues { System.getProperty(it.key) }
            try {
                settings.forEach { (key, disabled) -> System.setProperty(key, disabled.toString()) }
                system=SubSystem(null,.01)
            } finally {
                saved.forEach { (key, value) -> if(value==null)System.clearProperty(key) else System.setProperty(key,value) }
            }
            pins.forEach(system::addState);system.addComponent(source);system.addComponent(probe)
            for(i in 1 until nodes-1) {
                val wire=Resistor(pins[(i-1)/2],pins[i]).apply { resistance=.01+i*.00001 }
                wires+=wire;system.addComponent(wire)
                system.addComponent(Resistor(pins[i],reference).apply { resistance=100_000.0+i })
            }
            if(controlled)system.addComponent(control)
        }
        fun rebuild(step: Int): SubSystem.Thevenin {
            wires[0].resistance=.01*(1+(step%17)*.00001)
            source.voltage=14_000.0+step*.0001
            return system.sourceThevenin(probe)
        }
    }

    @Test fun resistanceAndEnableMaskRebuildsKeepPortPhysicsAndState() {
        val dense=Feeder(128,false);val sparse=Feeder(128,true)
        val initial=sparse.pins.map { it.state }
        repeat(80) { step ->
            if(step%13==0) { dense.probe.enabled=!dense.probe.enabled;sparse.probe.enabled=!sparse.probe.enabled }
            val a=dense.rebuild(step);val b=sparse.rebuild(step)
            assertTrue(a.voltage.isFinite() && b.voltage.isFinite())
            assertEquals(a.voltage,b.voltage,0.0);assertEquals(a.resistance,b.resistance,0.0)
        }
        assertEquals(dense.system.fullFactorizations,sparse.system.fullFactorizations)
        assertTrue(sparse.system.fullFactorizations>50,"Thermal-like resistance changes must still rebuild the matrix")
        assertEquals(initial,sparse.pins.map { it.state },"A port probe committed physical state")
    }

    @Test fun sparseSymmetryDetectionKeepsOneSidedAndCancelledStamps() {
        val dense=Feeder(96,false,true);val sparse=Feeder(96,true,true)
        repeat(30) {
            val a=dense.rebuild(it);val b=sparse.rebuild(it)
            assertEquals(a.voltage,b.voltage,0.0);assertEquals(a.resistance,b.resistance,0.0)
        }
        assertEquals(0L,sparse.system.affineProbeUpdates,"A dependent-source matrix was misclassified reciprocal")
        dense.gain=0.0;sparse.gain=0.0;dense.control.dirty();sparse.control.dirty()
        repeat(30) {
            dense.source.voltage=14_000.0+it*.01;sparse.source.voltage=dense.source.voltage
            val a=dense.system.sourceThevenin(dense.probe);val b=sparse.system.sourceThevenin(sparse.probe)
            assertEquals(a.voltage,b.voltage,0.0);assertEquals(a.resistance,b.resistance,0.0)
        }
        assertTrue(sparse.system.affineProbeUpdates>10)
    }

    @Test fun reportsCompleteProbeTriggeredRebuildCosts() {
        for(nodes in listOf(80,240,720)) {
            val dense=Feeder(nodes,false);val sparse=Feeder(nodes,true)
            repeat(12) { dense.rebuild(it);sparse.rebuild(it) }
            fun time(feeder: Feeder): Double {
                val start=System.nanoTime()
                repeat(8) { check(feeder.rebuild(it+100).voltage.isFinite()) }
                return (System.nanoTime()-start)/1e6
            }
            val baseline=ArrayList<Double>();val optimized=ArrayList<Double>()
            repeat(6) { if(it%2==0) { baseline+=time(dense);optimized+=time(sparse) } else { optimized+=time(sparse);baseline+=time(dense) } }
            println("MNA_REBUILD_BENCH nodes=$nodes rebuilds_per_round=8 baseline_ms=$baseline optimized_ms=$optimized")
            assertEquals(dense.system.fullFactorizations,sparse.system.fullFactorizations)
        }
    }
}
