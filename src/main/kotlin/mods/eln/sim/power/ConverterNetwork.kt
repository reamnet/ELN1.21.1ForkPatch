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
    /** Preferred initial bus voltage during source startup only; every trial still needs validation. */
    fun startupVoltageTargets(): Map<SwitchableVoltageSource, Double> = emptyMap()
    /** Target-voltage input clamps that need to follow a starting source's bus guess. */
    fun startupInputSources(): List<SwitchableVoltageSource> = emptyList()
    /** Target outputs reached from an energizing input, for coherent cascaded guesses only. */
    fun startupOutputTargets(): Map<SwitchableVoltageSource, Double> = emptyMap()
    /** Voltage commands eligible for a bounded algebraic convergence correction. */
    fun trialSources(): List<SwitchableVoltageSource> = emptyList()
    /** All mutable source commands, including outputs eliminated from the free trial vector. */
    fun controlledSources(): List<SwitchableVoltageSource> = trialSources()
    /** Hard source-command domains for numerical trials, never a change to device ratings. */
    fun trialVoltageBounds(): Map<SwitchableVoltageSource, ClosedFloatingPointRange<Double>> = emptyMap()
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

/** Shared controls for regulated converters and fixed-ratio transformers. */
interface ResettablePowerProcess : ConservativePowerProcess {
    val status: String
    fun resetFault()
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

fun balancedPower(inputWatts: Double, outputWatts: Double, efficiency: Double = 1.0,
                  roundoffWatts: Double = 0.0): Boolean =
    inputWatts.isFinite() && outputWatts.isFinite() &&
        roundoffWatts.isFinite() && roundoffWatts >= 0.0 &&
        abs(inputWatts * efficiency - outputWatts) <= 1e-7 + 1e-6 * max(abs(inputWatts), abs(outputWatts)) + roundoffWatts

/** Bound the power represented by rounding a voltage command and its Thevenin
 * voltage to doubles. A stiff HV port can have a voltage quantum larger than
 * the entire drop required by a standby load. This is not a load cutoff.
 */
internal fun portPowerResolution(volts: Double, port: PortThevenin): Double {
    if (!port.ohms.isFinite() || port.ohms <= 0.0) return 0.0
    val amps = (volts - port.volts) / port.ohms
    return abs(volts) * (Math.ulp(volts) + Math.ulp(port.volts)) / port.ohms +
        abs(amps) * Math.ulp(volts)
}

/** A limited supply must reach its voltage target, power ceiling or current
 * ceiling. Merely staying below all three can accept a stale coupled trial.
 * Callers independently validate signs and upper bounds before this check.
 */
internal fun supplyLimitReached(volts: Double, amps: Double, nominalVolts: Double,
                                maxWatts: Double, maxAmps: Double): Boolean =
    commandMatches(volts, nominalVolts) ||
        abs(volts * amps - maxWatts) <= 1e-5 + 1e-6 * maxWatts ||
        abs(amps - maxAmps) <= 1e-7 + 1e-6 * maxAmps

/** A numerical candidate must still obey the actual control voltage law. */
internal fun commandMatches(actual: Double, commanded: Double): Boolean =
    actual.isFinite() && commanded.isFinite() &&
        abs(actual - commanded) <= 1e-9 * maxOf(1.0, abs(actual), abs(commanded))
