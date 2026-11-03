package mods.eln.sim.power

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.*
import mods.eln.transparentnode.offgrid.*
import mods.eln.transparentnode.solarpanel.SolarArrayPerformanceTest
import org.junit.Test
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import jdk.jfr.Recording
import java.time.Duration
import kotlin.test.*

/** Opt-in repeatable server electrical workloads. Reports measured time and allocation,
 * not TPS predictions; all verification is outside the timed batches. */
class ServerPerformanceAuditTest {
    private class Scene(val name: String,val root: RootSystem,val step: ()->Unit,val verify: ()->Unit)
    private fun batteryScene(count: Int,backup: Boolean): Scene {
        val root=RootSystem(.05,1)
        fun element(t: OffGridType)=OffGridElement(TransparentNode(),Eln.transparentNodeItem.subItemList[t.ordinal+(128 shl 6)]!!).also {
            it.electricalLoadList.forEach(root::addState);it.electricalComponentList.forEach(root::addComponent)
        }
        val box=element(OffGridType.INVERTER);val ac=box.ac!!
        ac.mode=if(backup)IslandMode.BACKUP else IslandMode.OFF_GRID
        val packs=List(count) { i->element(OffGridType.BANK64).also {
            it.bank!!.state.transfer(it.bank!!.state.capacity*(.45+i*.0005))
            root.addComponent(Resistor(it.bank!!.positive,ac.dc).apply { resistance=.002 })
            root.addComponent(Resistor(it.bank!!.negative,ac.negative).apply { resistance=.002 })
            root.addProcess(it.bank!!.controller)
        } }
        ac.controllers.forEach(root::addProcess)
        root.addComponent(VoltageSource("auditGrid",ac.grid,null).apply { voltage=240.0 })
        root.addComponent(Resistor(ac.load,null).apply { resistance=if(backup)57.6 else 11.52 })
        val processes=(packs+box).flatMap { it.electricalProcessList }
        return Scene("${count}_battery_banks_${if(backup)"grid_charge" else "offgrid"}",root,
            { root.step();processes.forEach { it.process(.05) } },{
                assertTrue(ac.outputWatts>if(backup)990 else 4990,"${ac.status}: ${ac.outputWatts}")
                packs.forEach { assertTrue(it.bank!!.state.energy in 0.0..it.bank!!.state.capacity);assertTrue(kotlin.math.abs(it.bank!!.source.current)<400.001) }
            })
    }
    private fun solarScene(loaded: Boolean,dark: Boolean=false): Scene {
        val array=SolarArrayPerformanceTest.ArrayCircuit(true)
        array.load(loaded);if(dark)array.light(0.0)
        return Scene("96_large_solar_${if(dark)"night" else if(loaded)"loaded" else "open"}",array.root,{ array.root.step() },{
            assertTrue(array.voltage.isFinite());if(dark)assertEquals(0.0,array.voltage,1e-6) else assertTrue(array.voltage>900)
        })
    }
    private fun mixedScene(): Scene {
        val grid=ServerTickHotspotBenchmarkTest.Grid(40,true);var tick=0
        return Scene("40_pole_mixed_grid",grid.root,{ grid.wind(tick++);grid.root.step() },{ grid.step(0,false) })
    }
    @Test fun compareAlternatingWorkloads() {
        if(System.getenv("ELN_AUDIT_PAIRED")!="1")return
        Eln.debugEnabled=false;Eln.mqttEnabled=false;Eln.simMetricsEnabled=false
        val key="eln.disableProbeDataReuse";val saved=System.getProperty(key)
        fun create(make: ()->Scene,disabled: Boolean): Scene {
            System.setProperty(key,disabled.toString())
            // Subsystems are created on the first physical step and capture the opt-out then.
            return make().also { it.step() }
        }
        val bean=ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        if(!bean.isThreadAllocatedMemoryEnabled)bean.isThreadAllocatedMemoryEnabled=true
        val thread=Thread.currentThread().threadId()
        val results=ArrayList<Map<String,Any>>()
        val folder=Path.of(System.getProperty("eln.projectDir"),"build/reports");Files.createDirectories(folder)
        try {
            for(make in listOf<()->Scene>({solarScene(false)},{solarScene(true)},{solarScene(false,true)},
                {batteryScene(1,true)},{batteryScene(8,false)},{batteryScene(32,false)},::mixedScene)) {
                val baseline=create(make,true);val optimized=create(make,false)
                repeat(100) { baseline.step();optimized.step() };baseline.verify();optimized.verify()
                val warm=System.nanoTime();repeat(20) { baseline.step() }
                val batch=(100_000_000.0/((System.nanoTime()-warm)/20.0)).toInt().coerceIn(20,2000)
                fun sample(s: Scene): List<Double> {
                    val bytes=bean.getThreadAllocatedBytes(thread);val cpu=bean.currentThreadCpuTime;val start=System.nanoTime()
                    repeat(batch) { s.step() }
                    val elapsed=(System.nanoTime()-start)/batch/1e6
                    val cpuMs=(bean.currentThreadCpuTime-cpu)/batch/1e6
                    val allocated=(bean.getThreadAllocatedBytes(thread)-bytes).toDouble()/batch
                    s.verify();return listOf(elapsed,cpuMs,allocated)
                }
                val before=ArrayList<List<Double>>();val after=ArrayList<List<Double>>()
                repeat(7) { if(it%2==0) { before+=sample(baseline);after+=sample(optimized) }
                    else { after+=sample(optimized);before+=sample(baseline) } }
                fun median(values: List<List<Double>>,index: Int)=values.drop(2).map { it[index] }.sorted()[2]
                val result=linkedMapOf<String,Any>("scene" to baseline.name,"stepsPerRound" to batch,"baselineRounds" to before,"optimizedRounds" to after,
                    "baselineMs" to median(before,0),"optimizedMs" to median(after,0),"baselineCpuMs" to median(before,1),"optimizedCpuMs" to median(after,1),
                    "baselineBytes" to median(before,2),"optimizedBytes" to median(after,2),
                    "directLegacyProbes" to optimized.root.systems.sumOf { it.directLegacyProbes },
                    "differentialResultReuses" to optimized.root.systems.sumOf { it.differentialResultReuses },"portResultReuses" to optimized.root.systems.sumOf { it.portResultReuses })
                results+=result;println("PAIRED_SERVER_AUDIT $result")
                Files.writeString(folder.resolve("server-audit-paired.json"),GsonBuilder().setPrettyPrinting().create().toJson(results))
            }
        } finally { if(saved==null)System.clearProperty(key) else System.setProperty(key,saved) }
    }
    @Test fun measureRepresentativeServerWorkloads() {
        if(System.getenv("ELN_PERFORMANCE_AUDIT")!="1")return
        val reuseKey="eln.disableProbeDataReuse";val previousReuse=System.getProperty(reuseKey)
        System.setProperty(reuseKey,(System.getenv("ELN_AUDIT_DISABLE_PROBE_REUSE")=="1").toString())
        Eln.debugEnabled=false;Eln.mqttEnabled=false;Eln.simMetricsEnabled=false
        val label=(System.getenv("ELN_AUDIT_LABEL") ?: "current").replace(Regex("[^a-zA-Z0-9_-]"),"_")
        val folder=Path.of(System.getProperty("eln.projectDir"),"build/reports");Files.createDirectories(folder)
        // Only performance events: omit environment variables and JVM/system properties.
        val recording=Recording()
        recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10)).withStackTrace()
        recording.enable("jdk.ObjectAllocationSample").with("throttle","150/s").withStackTrace()
        recording.enable("jdk.GarbageCollection")
        recording.start()
        val bean=ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        if(!bean.isThreadAllocatedMemoryEnabled)bean.isThreadAllocatedMemoryEnabled=true
        val thread=Thread.currentThread().threadId()
        val results=ArrayList<Map<String,Any>>()
        try {
            val scenes=listOf<()->Scene>({solarScene(false)},{solarScene(true)},{solarScene(false,true)},
                {batteryScene(1,true)},{batteryScene(8,false)},{batteryScene(32,false)},::mixedScene)
            for(make in scenes) {
                val s=make();repeat(100) { s.step() };s.verify()
                val warm=System.nanoTime();repeat(50) { s.step() }
                val perStep=(System.nanoTime()-warm)/50.0
                val batch=(200_000_000.0/perStep).toInt().coerceIn(50,5000)
                val timings=ArrayList<Double>();val allocations=ArrayList<Double>();val cpu=ArrayList<Double>()
                repeat(7) {
                    val bytes=bean.getThreadAllocatedBytes(thread);val cpuStart=bean.currentThreadCpuTime;val start=System.nanoTime()
                    repeat(batch) { s.step() }
                    timings+=(System.nanoTime()-start)/batch/1e6
                    cpu+=(bean.currentThreadCpuTime-cpuStart)/batch/1e6
                    allocations+=(bean.getThreadAllocatedBytes(thread)-bytes).toDouble()/batch
                    s.verify()
                }
                val item=linkedMapOf<String,Any>("scene" to s.name,"stepsPerRound" to batch,"millisecondsPerStep" to timings,
                    "cpuMillisecondsPerStep" to cpu,"allocatedBytesPerStep" to allocations,
                    "medianMs" to timings.drop(2).sorted()[2],"medianAllocatedBytes" to allocations.drop(2).sorted()[2],
                    "matrixSizes" to s.root.systems.map { it.states.size },"fullFactorizations" to s.root.systems.sumOf { it.fullFactorizations },
                    "fullProbeSolves" to s.root.systems.sumOf { it.fullProbeSolves },"commandRhsReuses" to s.root.systems.sumOf { it.commandRhsReuses },
                    "probeValidationReuses" to s.root.systems.sumOf { it.probeValidationReuses },
                    "differentialResultReuses" to s.root.systems.sumOf { it.differentialResultReuses },"portResultReuses" to s.root.systems.sumOf { it.portResultReuses },
                    "directLegacyProbes" to s.root.systems.sumOf { it.directLegacyProbes })
                results+=item;println("SERVER_AUDIT $item")
                Files.writeString(folder.resolve("server-audit-$label.json"),GsonBuilder().setPrettyPrinting().create().toJson(results))
            }
        } finally {
            recording.stop();recording.dump(folder.resolve("server-audit-$label.jfr"));recording.close()
            if(previousReuse==null)System.clearProperty(reuseKey) else System.setProperty(reuseKey,previousReuse)
        }
    }
}
