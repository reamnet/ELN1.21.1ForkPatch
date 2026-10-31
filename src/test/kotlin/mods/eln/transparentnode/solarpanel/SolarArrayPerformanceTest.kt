package mods.eln.transparentnode.solarpanel

import mods.eln.Eln
import mods.eln.sim.ElectricalConnection
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.CurrentSource
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/** Explicit panel terminals and wire connections, rather than one scaled equivalent panel. */
class SolarArrayPerformanceTest {
    private class ArrayCircuit(val optimized: Boolean, val large: Boolean = true) {
        val root = RootSystem(.01, 50)
        val positive = pin("positive bus")
        val negative = pin("negative bus")
        val panels = ArrayList<SolarPanelPowerProcess>()
        val terminals = ArrayList<NbtElectricalLoad>()
        private var load: Resistor? = null
        private var loaded = false
        val voc = if (large) 111.0 else 37.0
        val vmp = if (large) 90.0 else 30.0
        val imp = if (large) 16.0 else 8.0
        init {
            repeat(8) { string ->
                val wire = Array(13) { pin("wire $string:$it") }
                root.addComponent(ElectricalConnection(wire.first(), negative))
                root.addComponent(ElectricalConnection(wire.last(), positive))
                repeat(12) { series ->
                    val minus = pin("panel $string:$series negative").apply { serialResistance = vmp * .01 / imp / 2 }
                    val plus = pin("panel $string:$series positive").apply { serialResistance = minus.serialResistance }
                    terminals += minus; terminals += plus
                    val source = CurrentSource("pv $string:$series", plus, minus)
                    val shunt = Resistor(plus, minus)
                    root.addComponent(source); root.addComponent(shunt)
                    root.addComponent(Resistor(minus, null).pullDown())
                    root.addComponent(ElectricalConnection(minus, wire[series]))
                    root.addComponent(ElectricalConnection(plus, wire[series + 1]))
                    panels += SolarPanelPowerProcess(plus, minus, source, shunt,
                        voc, vmp, imp, if (large) 17.2 else 8.6).also {
                        it.setLightFactor(1.0); root.addProcess(it)
                    }
                }
            }
        }
        private fun pin(name: String) = NbtElectricalLoad(name).apply {
            serialResistance = 2.11544e-5
            root.addState(this)
        }
        fun light(value: Double) = panels.forEach { it.setLightFactor(value) }
        fun load(on: Boolean) {
            if (on == loaded) return
            if (on) {
                load = Resistor(positive, negative).setResistance(12 * vmp / (8 * imp))
                root.addComponent(load)
            } else {
                root.removeComponent(load)
                load!!.breakConnection()
                load = null
            }
            loaded = on
        }
        fun step(): Double {
            val key = "eln.disableDifferentialVoltageProbes"
            val old = System.getProperty(key)
            System.setProperty(key, (!optimized).toString())
            try {
                val began = System.nanoTime()
                root.step()
                return (System.nanoTime() - began) / 1e6
            } finally {
                if (old == null) System.clearProperty(key) else System.setProperty(key, old)
            }
        }
        val voltage get() = positive.voltage - negative.voltage
        val probes get() = root.systems.sumOf { it.differentialVoltageProbeCount }
    }

    @Test fun twelveSeriesEightParallelRetainsOpenLoadedShadedAndNightBehavior() {
        Eln.debugEnabled = false
        for (large in listOf(false, true)) {
            val previous = ArrayCircuit(false, large)
            val optimized = ArrayCircuit(true, large)
            fun settle(steps: Int) {
                repeat(steps) {
                    previous.step(); optimized.step()
                    assertEquals(previous.voltage, optimized.voltage, .00001)
                    previous.terminals.zip(optimized.terminals).forEach { (a, b) -> assertEquals(a.state, b.state, .00001) }
                }
            }
            settle(18)
            assertTrue(optimized.voltage in (12 * optimized.voc * .98)..(12 * optimized.voc * 1.001))
            assertTrue(optimized.probes > 10_000)
            assertEquals(0L, previous.probes)
            previous.load(true); optimized.load(true); settle(12)
            assertTrue(optimized.voltage in (12 * optimized.vmp * .80)..(12 * optimized.vmp * 1.2))
            previous.light(.35); optimized.light(.35); settle(8)
            previous.light(0.0); optimized.light(0.0); settle(4)
            assertEquals(0.0, optimized.voltage, 1e-7)
            val nightProbes = optimized.probes
            settle(2); assertEquals(nightProbes, optimized.probes, "Dark panels must not probe the circuit")
            previous.light(1.0); optimized.light(1.0); settle(8)
            previous.load(false); optimized.load(false); settle(12)
            assertTrue(optimized.voltage in (12 * optimized.voc * .98)..(12 * optimized.voc * 1.001))
        }
    }

    @Test fun benchmarkOpenSunlitLargeArrayInAlternatingOrder() {
        if (!java.lang.Boolean.getBoolean("eln.benchSolarArray")) return
        Eln.debugEnabled = false
        val previous = ArrayCircuit(false)
        val optimized = ArrayCircuit(true)
        repeat(20) { previous.step(); optimized.step() }
        val before = ArrayList<Double>(); val after = ArrayList<Double>()
        repeat(7) { round ->
            fun sample(c: ArrayCircuit) = List(12) { c.step() }.average()
            if (round % 2 == 0) { before += sample(previous); after += sample(optimized) }
            else { after += sample(optimized); before += sample(previous) }
            assertEquals(previous.voltage, optimized.voltage, .00001)
        }
        val result = mapOf("series" to 12, "parallel" to 8, "panelBlocks" to 96,
            "panelType" to "large 2x3 tracking electrical parameters", "lightFactor" to 1.0,
            "legacySweeps" to 50, "electricalPeriod" to .01, "stepsPerRound" to 12,
            "previousMillisecondsPerElectricalStep" to before, "optimizedMillisecondsPerElectricalStep" to after,
            "previousMedianMs" to before.drop(2).sorted()[2], "optimizedMedianMs" to after.drop(2).sorted()[2],
            "openCircuitVoltage" to optimized.voltage, "subsystemSizes" to optimized.root.systems.map { it.states.size },
            "differentialProbes" to optimized.probes,
            "commandRhsReuses" to optimized.root.systems.sumOf { it.commandRhsReuses })
        val report = Path.of(System.getProperty("eln.projectDir"), "build/reports/solar-array-benchmark.json")
        Files.createDirectories(report.parent)
        Files.writeString(report, com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result))
        println("SOLAR_ARRAY_BENCH $result")
    }
}
