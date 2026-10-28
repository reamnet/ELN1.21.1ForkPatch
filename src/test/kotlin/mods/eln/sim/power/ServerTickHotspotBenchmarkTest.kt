package mods.eln.sim.power

import mods.eln.Eln
import mods.eln.mechanical.GeneratorDescriptor
import mods.eln.mechanical.GeneratorElement
import mods.eln.mechanical.ShaftNetwork
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.state.State
import mods.eln.sim.mna.state.VoltageState
import mods.eln.transparentnode.renewable.RenewableSupply
import mods.eln.utility.DistributionKind
import mods.eln.utility.DistributionPoleDescriptor
import mods.eln.utility.DistributionPoleElement
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/** Same physical circuit; baseline retains 3.0.65 optimizations and disables only the new work. */
class ServerTickHotspotBenchmarkTest {
    private class Grid(poles: Int, optimized: Boolean) {
        val root: RootSystem
        val hv: VoltageState
        private var windWatts=0.0
        private val controllers=ArrayList<ConservativePowerProcess>()
        private val secondaries=ArrayList<State>()
        init {
            val options=listOf("eln.disableLocalCommandJacobian",
                "eln.disableIdenticalRhsReuse","eln.disableDirectPortDispatch")
            val saved=options.associateWith(System::getProperty)
            options.forEach { System.setProperty(it,(!optimized).toString()) }
            try {
                root=RootSystem(.01,1)
                fun pin()=VoltageState().also(root::addState)
                fun wire(a: State,b: State,r: Double)=Resistor(a,b).apply { resistance=r }.also(root::addComponent)
                fun source(name: String,a: State)=SwitchableVoltageSource(name).apply { connectTo(a,null);root.addComponent(this) }
                val bus=pin();val windBus=pin()
                val generatorDescriptor=Eln.transparentNodeItem.subItemList.values.filterIsInstance<GeneratorDescriptor>().single { it.name=="Large Generator" }
                repeat(2) {
                    val g=GeneratorElement(TransparentNode(),generatorDescriptor)
                    g.shaft=ShaftNetwork(g,g.shaftConnectivity.iterator()).apply { rads=202.0 }
                    g.electricalLoadList.forEach(root::addState);g.electricalComponentList.forEach(root::addComponent)
                    wire(g.inputLoad,bus,.01);controllers+=g.electricalProcess
                }
                // Warm the unloaded generators physically before attaching the
                // feeder, rather than mixing their field ramp with grid timings.
                controllers.forEach(root::addProcess)
                repeat(200) { root.step() }
                val alreadyRegistered=controllers.size
                repeat(10) { i ->
                    val p=pin();val s=source("wind$i",p);wire(p,windBus,.02)
                    controllers+=RenewableSupply(s) { windWatts*(1-i*.03) }
                }
                fun transformer(feed: State,target: Double,energized: Boolean=false): VoltageState {
                    val p=pin();val s=pin();wire(feed,p,.01)
                    val input=source("input",p).apply { voltage=3200.0;enabled=energized }
                    val output=source("output",s).apply { voltage=target;enabled=energized }
                    controllers+=SafeTransformerProcess(p,s,input,output) { true }.apply { voltageTarget=target }
                    return s
                }
                wire(transformer(windBus,3300.0),bus,.01)
                hv=transformer(bus,15_200.0,true)
                root.addComponent(Resistor(hv,null).apply { resistance=15_200.0*15_200/60_000 })
                val descriptor=Eln.transparentNodeItem.subItemList.values.filterIsInstance<DistributionPoleDescriptor>().single { it.kind==DistributionKind.TRANSFORMER_THREE }
                var previous: State=hv
                repeat(poles) {
                    val pole=DistributionPoleElement(TransparentNode(),descriptor)
                    pole.inventory.setItem(0,Eln.findItemStack("10 kW Pole Transformer",1));pole.inventoryChange(pole.inventory)
                    pole.electricalLoadList.forEach(root::addState);pole.electricalComponentList.forEach(root::addComponent)
                    wire(previous,pole.lines[0],.1);previous=pole.lines[0]
                    root.addComponent(Resistor(pole.services[0],null).apply { resistance=120.0*120/70 })
                    // Initial guesses only, equivalent to saved running commands.
                    // Every subsequent command still needs real MNA acceptance.
                    val unit=requireNotNull(pole.unit)
                    unit.input.apply { voltage=15_200.0;enabled=true }
                    unit.output.apply { voltage=15_200.0*unit.outputVoltage/12_470;enabled=true }
                    controllers+=unit.controller;secondaries+=pole.services[0]
                }
                controllers.drop(alreadyRegistered).forEach(root::addProcess)
                repeat(20) { root.step() }
            } finally { saved.forEach { (k,v)->if(v==null)System.clearProperty(k) else System.setProperty(k,v) } }
        }
        fun step(i: Int,changeWind: Boolean=true): Double {
            if(changeWind)windWatts=listOf(15_000.0,5000.0,1000.0,20_000.0)[(i/5)%4]
            val start=System.nanoTime();root.step();val ms=(System.nanoTime()-start)/1e6
            assertTrue(hv.voltage>14_000,"Grid blinked at step $i")
            // A physical flush deliberately clears pending trial values. Re-solve
            // the committed commands before using the trial acceptance API; this
            // validation is outside the timed step and advances no physical time.
            root.systems.forEach { it.stepCalc() }
            assertTrue(controllers.all { it.acceptsCandidate() },"An unconverged state was accepted")
            // Pole transformers are fixed-ratio at 12.47 kV nominal: a 15.2 kV
            // feed correctly raises the selected 120 V output proportionally.
            val expected=hv.voltage*120/12_470
            secondaries.forEach { assertEquals(expected,it.state,.5) }
            return ms
        }
        fun counters()=mapOf("fullFactorizations" to root.systems.sumOf { it.fullFactorizations },
            "localJacobianBuilds" to root.localJacobianBuilds,
            "identicalRhsReuses" to root.systems.sumOf { it.identicalRhsReuses })
    }
    @Test fun pairedMixedSourceAndTransformerGridMeasurements() {
        Eln.debugEnabled=false
        val results=ArrayList<Map<String,Any>>()
        val report=Path.of(System.getProperty("eln.projectDir"),"build/reports/server-hotspot-benchmark.json")
        Files.createDirectories(report.parent)
        val samples=(System.getenv("ELN_BENCH_MIXED_SAMPLES")?.toIntOrNull() ?: 40).coerceAtLeast(20)
        val progress=report.resolveSibling("server-hotspot-progress.txt")
        for(count in listOf(40,120)) {
            Files.writeString(progress,"poles=$count constructing\n")
            val base=Grid(count,false);val fast=Grid(count,true)
            repeat(20) { base.step(it);fast.step(it) }
            val a=ArrayList<Double>();val b=ArrayList<Double>()
            repeat(samples) { i ->
                if(i%2==0) { a+=base.step(i);b+=fast.step(i) } else { b+=fast.step(i);a+=base.step(i) }
                assertEquals(base.hv.voltage,fast.hv.voltage,.01)
                if(i%10==0)Files.writeString(progress,"poles=$count samples=$i/$samples\n")
            }
            fun stats(v: List<Double>): String { val s=v.sorted();return "p50=${s[samples/2-1]},p95=${s[(samples*.95).toInt()-1]},mean=${s.average()}" }
            println("SERVER_HOTSPOT_BENCH poles=$count baseline_ms={${stats(a)}} optimized_ms={${stats(b)}} max_states=${fast.root.systems.maxOf { it.states.size }}")
            repeat(30) { base.step(it,false);fast.step(it,false) }
            val steadyA=ArrayList<Double>();val steadyB=ArrayList<Double>()
            repeat(100) { i->
                if(i%2==0) { steadyA+=base.step(i,false);steadyB+=fast.step(i,false) }
                else { steadyB+=fast.step(i,false);steadyA+=base.step(i,false) }
                assertEquals(base.hv.voltage,fast.hv.voltage,.01)
            }
            println("SERVER_STEADY_BENCH poles=$count baseline_mean_ms=${steadyA.average()} optimized_mean_ms=${steadyB.average()}")
            results+=mapOf("poles" to count,"warmupSteps" to 20,"baselineMilliseconds" to a,"optimizedMilliseconds" to b,
                "steadyBaselineMilliseconds" to steadyA,"steadyOptimizedMilliseconds" to steadyB,
                "baselineCounters" to base.counters(),"optimizedCounters" to fast.counters(),
                "maxStates" to fast.root.systems.maxOf { it.states.size })
            Files.writeString(report,com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results))
        }
        Files.writeString(progress,"passed\n")
    }
}
