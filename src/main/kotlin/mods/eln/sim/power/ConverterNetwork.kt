package mods.eln.sim.power

import mods.eln.sim.mna.SubSystem
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.misc.IRootSystemPreStepProcess
import mods.eln.sim.mna.state.State
import kotlin.math.abs
import kotlin.math.max

/** Participant in the root solver's trial/commit boundary. */
interface ConservativePowerProcess : IRootSystemPreStepProcess {
    /** Once per physical step, before algebraic trials. Retry timers must not advance per trial. */
    fun beginStep(seconds: Double) {}
    /** Initial trial commands only. Never advances a physical state or bypasses acceptance. */
    fun prepareStep() {}
    /** Voltage commands eligible for a bounded algebraic convergence correction. */
    fun trialSources(): List<SwitchableVoltageSource> = emptyList()
    /** Piecewise operating region for a one-sided numerical derivative only. */
    fun iterationRegion(): String = ""
    /** Source I-V breakpoints for restarting a stalled algebraic solve; never bypass acceptance. */
    fun voltageSearchHints(): Map<SwitchableVoltageSource, DoubleArray> = emptyMap()
    /** Opening this load locally may let the remaining network settle safely. */
    fun isolatesLoadOnFailure(): Boolean = false
    fun acceptsCandidate(): Boolean
    fun failClosed()
    fun connectedSystems(): Set<SubSystem>
}

/** A failed algebraic solve opens the converter for a second, then retries normal validation.
 * This is a numerical recovery timer, separate from persistent insulation/winding damage.
 */
internal class ConverterRetry {
    private var remaining = 0.0
    val waiting get() = remaining > 1e-9
    fun trip() { remaining = 1.0 }
    fun reset() { remaining = 0.0 }
    fun advance(seconds: Double) {
        if (seconds.isFinite() && seconds > 0.0) remaining = (remaining - seconds).coerceAtLeast(0.0)
    }
}

/** Precision-preserving affine Thevenin probe; never commits physical state. */
fun probePort(positive: State, negative: State?, source: SwitchableVoltageSource): PortThevenin {
    val system = positive.subSystem ?: return PortThevenin(0.0, Double.POSITIVE_INFINITY)
    if (negative != null && negative.subSystem !== system) return PortThevenin(Double.NaN, Double.NaN)
    val response = system.sourceThevenin(source)
    return PortThevenin(response.voltage, response.resistance)
}

fun pendingSourcePower(source: SwitchableVoltageSource): Double {
    if (!source.enabled) return 0.0
    val system = source.subSystem ?: return Double.NaN
    return -source.voltage * system.pendingValue(source.currentState)
}

fun balancedPower(inputWatts: Double, outputWatts: Double, efficiency: Double = 1.0): Boolean =
    inputWatts.isFinite() && outputWatts.isFinite() &&
        abs(inputWatts * efficiency - outputWatts) <= 1e-7 + 1e-6 * max(abs(inputWatts), abs(outputWatts))
