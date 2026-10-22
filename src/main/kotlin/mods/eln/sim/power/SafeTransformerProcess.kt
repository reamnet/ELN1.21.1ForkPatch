package mods.eln.sim.power

import mods.eln.sim.mna.SubSystem
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.process.TransformerInterSystemProcess
import mods.eln.sim.mna.state.State
import kotlin.math.abs

/** Fixed ratio remains reversible. Windings are actual resistors outside these internal ports. */
class SafeTransformerProcess(
    private val primary: State,
    private val secondary: State,
    private val input: SwitchableVoltageSource,
    private val output: SwitchableVoltageSource,
    private val populated: () -> Boolean
) : TransformerInterSystemProcess(primary, secondary, input, output), ResettablePowerProcess {
    var maximumPrimaryVoltage = 120_000.0
    var maximumSecondaryVoltage = 120_000.0
    var enabled = true
    var voltageTarget: Double? = null
    override var status = "IDLE"
        private set
    private val retry = ConverterRetry()
    private var commandedInput = Double.NaN
    private var commandedOutput = Double.NaN
    private var powerRoundoff = 0.0

    override fun resetFault() { retry.reset() }
    override fun beginStep(seconds: Double) { retry.advance(seconds) }

    override fun rootSystemPreStepProcess() {
        if (retry.waiting) { open("NON_CONVERGENT"); return }
        if (!enabled || !populated()) { open("DISABLED"); return }
        val target = voltageTarget
        if (target != null) {
            // Zero is an off request, not a tiny positive gain or a zero-volt short.
            if (!target.isFinite() || target < 0.0) { open("INVALID_CONTROL"); return }
            if (target == 0.0) { open("DISABLED"); return }
        }
        val a = probePort(primary, null, input)
        val b = probePort(secondary, null, output)
        if (!a.volts.isFinite() || !b.volts.isFinite() || a.ohms.isNaN() || b.ohms.isNaN() ||
            a.ohms < 0.0 || b.ohms < 0.0) { open("INVALID_NETWORK"); return }
        if (target != null && a.volts > 0.0) ratio = (target / a.volts).coerceIn(1.0 / 256, 256.0)
        if (!ratio.isFinite() || ratio <= 0) { open("INVALID_CONTROL"); return }
        val induced = a.volts * ratio
        // An energized output already at the induced/set voltage needs no transfer.
        // Keep its voltage present for drive controls, but remove the unnecessary
        // input clamp. Otherwise a coasting motor and converter can chase tiny
        // opposing voltage updates around a zero-power operating point.
        if (target != null && abs(induced) > 1e-12 && a.ohms < RegulatedConverter.OPEN_OHMS && b.ohms < RegulatedConverter.OPEN_OHMS &&
            abs(induced - b.volts) <= 1e-9 * maxOf(1.0, abs(induced), abs(b.volts)) &&
            abs(a.volts) <= maximumPrimaryVoltage && abs(b.volts) <= maximumSecondaryVoltage) {
            input.voltage = a.volts; input.enabled = false
            output.voltage = b.volts; output.enabled = true
            commandedInput = input.voltage; commandedOutput = output.voltage
            powerRoundoff = portPowerResolution(output.voltage, b)
            status = "IDLE"
            return
        }
        val point = when {
            abs(a.volts) < 1e-12 && abs(b.volts) < 1e-12 -> null
            a.ohms >= RegulatedConverter.OPEN_OHMS && b.ohms >= RegulatedConverter.OPEN_OHMS -> null
            b.ohms >= RegulatedConverter.OPEN_OHMS -> OperatingPoint(a.volts, a.volts * ratio, 0.0, 0.0, 0.0, 0.0, 0.0)
            a.ohms >= RegulatedConverter.OPEN_OHMS -> OperatingPoint(b.volts / ratio, b.volts, 0.0, 0.0, 0.0, 0.0, 0.0)
            else -> RatioTransformer.solve(a, b, ratio)
        }
        if (point == null) { open("NO_INPUT"); return }
        if (!point.inputVolts.isFinite() || !point.outputVolts.isFinite()) { open("INVALID_NETWORK"); return }
        if (abs(point.inputVolts) > maximumPrimaryVoltage || abs(point.outputVolts) > maximumSecondaryVoltage) {
            open("VOLTAGE_LIMIT"); return
        }
        input.voltage = point.inputVolts; output.voltage = point.outputVolts
        // An unloaded secondary needs an induced voltage, not an ideal clamp drawing
        // exactly zero current from a loaded HV line. That zero-current clamp is both
        // unnecessary and unrepresentable at some cable impedances/voltage ULPs.
        input.enabled = b.ohms < RegulatedConverter.OPEN_OHMS
        output.enabled = a.ohms < RegulatedConverter.OPEN_OHMS
        commandedInput = input.voltage; commandedOutput = output.voltage
        powerRoundoff = portPowerResolution(input.voltage, a) + portPowerResolution(output.voltage, b)
        status = "TRANSFERRING"
    }

    private fun open(reason: String) {
        input.enabled = false
        output.enabled = false
        status = reason
    }

    override fun acceptsCandidate(): Boolean {
        if (!input.enabled && !output.enabled) return true
        if ((input.enabled && !commandMatches(input.voltage, commandedInput)) ||
            (output.enabled && !commandMatches(output.voltage, commandedOutput))) return false
        val vp = primary.subSystem?.pendingValue(primary) ?: return false
        val vs = secondary.subSystem?.pendingValue(secondary) ?: return false
        val induced = ratio * vp
        // An unloaded winding balances zero watts even with an obsolete induced
        // voltage. Validate its actual solved ratio before watchdogs see a commit.
        if (!vp.isFinite() || !vs.isFinite() || !induced.isFinite() ||
            abs(vs - induced) > 1e-7 + 1e-6 * maxOf(abs(vs), abs(induced))) return false
        val pin = -pendingSourcePower(input)
        val pout = pendingSourcePower(output)
        return balancedPower(pin, pout, roundoffWatts = powerRoundoff)
    }

    override fun startupInputSources(): List<SwitchableVoltageSource> =
        if (voltageTarget != null) listOf(input) else emptyList()

    override fun trialSources(): List<SwitchableVoltageSource> =
        // An unloaded target output is already fixed by its control law. Probing
        // it as a free variable can activate an otherwise idle attached drive.
        if (voltageTarget != null && !input.enabled && output.enabled) listOf(input)
        else listOf(input, output)

    override fun failClosed() { retry.trip(); open("NON_CONVERGENT") }
    override fun isolatesLoadOnFailure() = true
    override fun connectedSystems(): Set<SubSystem> = setOfNotNull(primary.subSystem, secondary.subSystem)
}
