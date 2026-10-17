package mods.eln.mechanical

import mods.eln.Eln
import mods.eln.metrics.MetricsSubsystem
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.state.VoltageState
import mods.eln.sim.power.ConservativePowerProcess
import mods.eln.transparentnode.utilitylight.StepDownUnitDescriptor
import mods.eln.transparentnode.utilitylight.StepDownUnitElement
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Measures changing generator speed and cable temperatures, not just a settled ideal supply. */
class GeneratorGridProfilingTest {
    private class Counted(val process: ConservativePowerProcess) : ConservativePowerProcess by process {
        var evaluations = 0L
        override fun rootSystemPreStepProcess() { evaluations++; process.rootSystemPreStepProcess() }
    }

    @Test fun changingShaftAndBranchedFeederCost() {
        Eln.debugEnabled = false; Eln.mqttEnabled = false; Eln.simMetricsEnabled = false
        MetricsSubsystem.shutdown()
        val recording = jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"))
        recording.start()
        try {
            for (count in listOf(1, 4, 16, 64)) {
                val root = RootSystem(.01, 1)
                val descriptors = Eln.transparentNodeItem.subItemList.values
                val generator = GeneratorElement(TransparentNode(), descriptors.filterIsInstance<GeneratorDescriptor>()
                    .single { it.name == "Large Generator" })
                generator.shaft._mass = generator.shaftMass
                generator.shaft.rads = 202.0
                generator.electricalLoadList.forEach(root::addState)
                generator.electricalComponentList.forEach(root::addComponent)
                val processes = mutableListOf(Counted(generator.electricalProcess))
                root.addProcess(processes.first())
                val wires = mutableListOf<Resistor>()
                var feed = generator.inputLoad as mods.eln.sim.mna.state.State
                val lights = (0 until count).map { i ->
                    val a = VoltageState(); val b = VoltageState()
                    root.addState(a); root.addState(b)
                    listOf(Resistor(feed, a).apply { resistance = .01 },
                        Resistor(feed, b).apply { resistance = .02 },
                        Resistor(a, b).apply { resistance = .03 }).forEach { wires += it; root.addComponent(it) }
                    feed = b
                    val e = StepDownUnitElement(TransparentNode(), descriptors.filterIsInstance<StepDownUnitDescriptor>().single())
                    e.electricalLoadList.forEach(root::addState)
                    e.electricalComponentList.forEach(root::addComponent)
                    root.addComponent(Resistor(a, e.unit.primary).apply { resistance = .01 })
                    root.addComponent(Resistor(e.unit.secondary, null).apply { resistance = 240.0 + i })
                    Counted(e.unit.controller).also { processes += it; root.addProcess(it) }
                    e
                }
                repeat(160) { root.step() }
                for (thermal in listOf(false, true)) {
                    val evals = processes.sumOf { it.evaluations }
                    val factors = root.systems.sumOf { it.fullFactorizations }
                    val samples = DoubleArray(60) { step ->
                        val start = System.nanoTime()
                        generator.shaft.rads = 202.0 + .01 * kotlin.math.sin(step.toDouble())
                        if (thermal && step % 5 == 0) wires.forEach { it.resistance *= 1.000001 }
                        root.step()
                        lights.forEach { assertEquals(120.0, it.unit.secondary.voltage, .1, it.unit.controller.status) }
                        assertTrue(generator.inputLoad.voltage > 3000)
                        (System.nanoTime() - start) / 1e6
                    }.sorted()
                    println("GENERATOR_GRID_BENCH count=$count thermal=$thermal states=${root.systems.maxOf { it.states.size }} " +
                        "p50_ms=${samples[29]} p95_ms=${samples[56]} p99_ms=${samples[59]} " +
                        "controller_evaluations=${processes.sumOf { it.evaluations } - evals} " +
                        "full_factorizations=${root.systems.sumOf { it.fullFactorizations } - factors}")
                }
                generator.shaft.rads = 0.0
                repeat(3) { root.step() }
                val factors = root.systems.sumOf { it.fullFactorizations }
                val samples = DoubleArray(12) {
                    val start = System.nanoTime(); root.step()
                    lights.forEach { assertEquals(0.0, it.unit.output.power, 1e-8) }
                    (System.nanoTime() - start) / 1e6
                }.sorted()
                println("GENERATOR_GRID_OFF_BENCH count=$count p50_ms=${samples[5]} p95_ms=${samples[11]} " +
                    "full_factorizations=${root.systems.sumOf { it.fullFactorizations } - factors}")
            }
        } finally {
            recording.stop()
            val out = Path.of(System.getProperty("eln.projectDir", "."), "build", "solver-profile.jfr")
            Files.createDirectories(out.parent); recording.dump(out); recording.close()
        }
    }
}
