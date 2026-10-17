package mods.eln.mechanical

import mods.eln.sim.mna.SubSystem
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.state.State
import mods.eln.sim.power.ConservativePowerProcess
import mods.eln.sim.power.ConverterRetry
import mods.eln.sim.power.probePort
import kotlin.math.abs
import kotlin.math.max

/** The generator and its converter loads must solve the same algebraic operating point.
 * Ramp and energy budget advance once per physical step, never during trial solves.
 */
class ShaftGeneratorProcess(
    private val positive: State,
    private val negative: State?,
    private val source: SwitchableVoltageSource,
    private val emf: () -> Double,
    private val energy: () -> Double,
    private val rampRate: Double,
    private val droop: Double,
    private val currentLimit: Double,
    private val efficiency: Double
) : ConservativePowerProcess {
    var voltageTarget = 0.0
        private set
    private var outputPowerBudget = 0.0
    private val retry = ConverterRetry()

    override fun beginStep(seconds: Double) {
        retry.advance(seconds)
        val requested = emf().takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        voltageTarget += (requested - voltageTarget).coerceIn(-rampRate * seconds, rampRate * seconds)
        // A sudden shaft slowdown cannot retain the previous, higher EMF during the ramp.
        voltageTarget = minOf(voltageTarget, requested)
        outputPowerBudget = (energy() / seconds * efficiency).coerceAtLeast(0.0)
    }

    override fun prepareStep() {
        if (retry.waiting) { source.enabled = false; return }
        if (!source.enabled) source.voltage = voltageTarget
        source.enabled = true
    }

    override fun rootSystemPreStepProcess() {
        if (retry.waiting) { source.enabled = false; return }
        val port = probePort(positive, negative, source)
        if (!port.volts.isFinite() || port.ohms.isNaN() || port.ohms < 0.0) {
            source.enabled = false
            return
        }
        source.voltage = ShaftElectricalMath.sourceVoltage(voltageTarget, port.volts, port.ohms,
            droop, currentLimit, outputPowerBudget)
        source.enabled = true
    }

    override fun acceptsCandidate(): Boolean {
        if (!source.enabled) return true
        val system = source.subSystem ?: return false
        val amps = -system.pendingValue(source.currentState)
        val volts = source.voltage
        if (!amps.isFinite() || !volts.isFinite() || volts < 0.0) return false
        // Check the physical I-V relation directly. Comparing successive voltage commands
        // misses huge current errors on short, low-resistance cables and stiff converter inputs.
        var expected = ((voltageTarget - volts) / droop).coerceIn(-currentLimit, currentLimit)
        if (expected > 0.0 && volts > 0.0) expected = minOf(expected, outputPowerBudget / volts)
        return abs(amps - expected) <= 1e-7 + 1e-6 * max(abs(amps), abs(expected))
    }

    override fun trialSources() = listOf(source)
    override fun connectedSystems(): Set<SubSystem> = setOfNotNull(positive.subSystem, negative?.subSystem)
    override fun failClosed() { retry.trip(); source.enabled = false }
}
