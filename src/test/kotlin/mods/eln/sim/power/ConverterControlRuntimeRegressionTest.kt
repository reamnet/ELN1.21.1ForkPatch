package mods.eln.sim.power

import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.VoltageState
import mods.eln.transparentnode.DcDcControl
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real production source branches, controller processes and MNA solver; no mocked circuit solver. */
class ConverterControlRuntimeRegressionTest {
    private class Circuit(inputOhms: Double = 1.0, loadOhms: Double = 1000.0) {
        val root = RootSystem(.01, 1)
        val feed = VoltageState()
        val primary = VoltageState()
        val secondary = VoltageState()
        val supply = VoltageSource("supply", feed, null).setVoltage(50.0)
        val winding = Resistor(feed, primary).apply { resistance = inputOhms }
        val load = Resistor(secondary, null).apply { resistance = loadOhms }
        val input = SwitchableVoltageSource("input").apply { connectTo(primary, null) }
        val output = SwitchableVoltageSource("output").apply { connectTo(secondary, null) }

        init {
            listOf(feed, primary, secondary).forEach(root::addState)
            root.addComponent(supply)
            root.addComponent(winding)
            root.addComponent(load)
            root.addComponent(input)
            root.addComponent(output)
        }

        fun settle() { repeat(8) { root.step() } }
    }

    private fun close(actual: Double, expected: Double) =
        assertEquals(expected, actual, 1e-6 * maxOf(1.0, abs(expected)))

    private fun assertOpen(c: Circuit) {
        assertFalse(c.input.enabled)
        assertFalse(c.output.enabled)
        close(c.input.current, 0.0)
        close(c.output.current, 0.0)
        // An open input still measures the supply; commanding 0 V would short it through the winding.
        close(c.primary.voltage, 50.0)
        close(c.load.power, 0.0)
    }

    private fun limits() = ConverterLimits(.1, 120_000.0, 120_000.0,
        1000.0, 1000.0, 1_000_000.0, .97, 1.0 / 256, 256.0)

    @Test fun numericalFailureRecoversWithoutChangingSettingsAfterOnePhysicalSecond() {
        for (regulated in listOf(false, true)) {
            val c = Circuit()
            val process: ConservativePowerProcess = if (regulated)
                RegulatedPowerProcess(c.primary, null, c.secondary, null, c.input, c.output,
                    { true }, { limits() }, { 100.0 })
            else SafeTransformerProcess(c.primary, c.secondary, c.input, c.output) { true }.apply { ratio = 2.0 }
            c.root.addProcess(process)
            c.settle()
            assertTrue(c.load.power > 0.0)
            process.failClosed()
            repeat(99) { c.root.step(); assertOpen(c) }
            c.root.step()
            assertTrue(c.input.enabled && c.output.enabled, "Controller remained latched off")
            assertTrue(c.load.power > 0.0)
            close(c.output.power, -c.input.power * if (regulated) .97 else 1.0)
        }
    }

    @Test fun retryDoesNotAdvanceDuringAlgebraicTrialsOrBypassInvalidControls() {
        val c = Circuit()
        var request = 100.0
        val process = RegulatedPowerProcess(c.primary, null, c.secondary, null, c.input, c.output,
            { true }, { limits() }, { request })
        c.root.addProcess(process)
        c.settle()
        process.failClosed()
        repeat(1000) { process.prepareStep(); process.rootSystemPreStepProcess() }
        assertFalse(c.output.enabled)
        request = Double.NaN
        repeat(101) { c.root.step() }
        assertOpen(c)
        assertEquals("INVALID_CONTROL", process.status)
        request = 100.0
        c.settle()
        assertTrue(c.output.enabled)
    }

    @Test fun regulatedInvalidRequestsOpenBothBranchesAndRecover() {
        val c = Circuit()
        var request = 100.0
        var throwControlFault = false
        val process = RegulatedPowerProcess(c.primary, null, c.secondary, null, c.input, c.output,
            { true }, { limits() }, {
                require(!throwControlFault) { "Invalid active signal" }
                request
            })
        c.root.addProcess(process)
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0)) {
            request = 100.0
            c.settle()
            assertTrue(c.output.enabled)
            assertTrue(c.load.power > 0.0)
            request = invalid
            c.settle()
            assertEquals("INVALID_CONTROL", process.status)
            assertOpen(c)
        }
        request = 100.0
        throwControlFault = true
        c.settle()
        assertEquals("INVALID_CONTROL", process.status)
        assertOpen(c)
        throwControlFault = false
        c.settle()
        assertTrue(c.output.enabled)
        close(c.secondary.voltage, 100.0)
    }

    @Test fun regulatedInvalidRatingCallbackCannotEscapeTheTick() {
        val c = Circuit()
        val process = RegulatedPowerProcess(c.primary, null, c.secondary, null, c.input, c.output,
            { true }, { throw IllegalArgumentException("Invalid winding rating") }, { 100.0 })
        c.root.addProcess(process)
        c.settle()
        assertEquals("INVALID_CONTROL", process.status)
        assertOpen(c)
    }

    @Test fun regulatedZeroTargetAndDisableAreOpenCircuitsNotShorts() {
        val c = Circuit()
        var request = 100.0
        var enabled = true
        val process = RegulatedPowerProcess(c.primary, null, c.secondary, null, c.input, c.output,
            { enabled }, { limits() }, { request })
        c.root.addProcess(process)
        c.settle()
        assertTrue(c.output.enabled)
        request = 0.0
        c.settle()
        assertEquals("DISABLED", process.status)
        assertOpen(c)
        request = 100.0
        c.settle()
        assertTrue(c.output.enabled)
        enabled = false
        c.settle()
        assertEquals("DISABLED", process.status)
        assertOpen(c)
    }

    @Test fun manualModesOperateWithAnInvalidInactiveSignal() {
        for (mode in listOf("VOLTAGE", "RATIO")) {
            val c = Circuit()
            val control = DcDcControl().apply {
                this.mode = mode
                value = if (mode == "VOLTAGE") 100.0 else 2.0
            }
            val process = RegulatedPowerProcess(c.primary, null, c.secondary, null, c.input, c.output,
                { control.enabled }, { limits() }, { vin ->
                    if (control.mode == "VOLTAGE") control.value
                    else vin * control.ratio(ConverterKind.BUCK_BOOST, Double.NaN)
                })
            c.root.addProcess(process)
            c.settle()
            assertTrue(c.input.enabled)
            assertTrue(c.output.enabled)
            close(c.secondary.voltage, 100.0)
            close(c.output.power, -c.input.power * .97)
        }
    }

    @Test fun safeTransformerZeroAndInvalidVoltageTargetsDoNotBecomeMinimumGain() {
        val c = Circuit()
        val process = SafeTransformerProcess(c.primary, c.secondary, c.input, c.output) { true }
        c.root.addProcess(process)
        for (invalid in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            process.voltageTarget = 100.0
            c.settle()
            assertTrue(c.output.enabled)
            assertTrue(c.load.power > 0.0)
            process.voltageTarget = invalid
            c.settle()
            assertEquals(if (invalid == 0.0) "DISABLED" else "INVALID_CONTROL", process.status)
            assertOpen(c)
        }
    }

    @Test fun safeTransformerDisabledAndIncompleteStatesDoNotReportTransferring() {
        val c = Circuit()
        var populated = true
        val process = SafeTransformerProcess(c.primary, c.secondary, c.input, c.output) { populated }
        c.root.addProcess(process)
        c.settle()
        assertEquals("TRANSFERRING", process.status)
        process.enabled = false
        c.settle()
        assertEquals("DISABLED", process.status)
        assertOpen(c)
        process.enabled = true
        c.settle()
        assertTrue(c.output.enabled)
        populated = false
        c.settle()
        assertEquals("DISABLED", process.status)
        assertOpen(c)
    }

    @Test fun fixedStepDownUnityAndStepUpRatiosRemainLoadedAndPowerConserving() {
        for (ratio in listOf(.25, 1.0, 4.0)) {
            val c = Circuit()
            c.root.addProcess(SafeTransformerProcess(c.primary, c.secondary, c.input, c.output) { true }
                .apply { this.ratio = ratio })
            c.settle()
            val expectedPrimary = 50.0 / (1.0 + ratio * ratio / 1000.0)
            close(c.primary.voltage, expectedPrimary)
            close(c.secondary.voltage, expectedPrimary * ratio)
            close(-c.input.power, c.output.power)
            close(c.supply.power, c.winding.power + c.load.power)
        }
    }

    @Test fun longerSeriesWireChangesLoadedVoltageNotTheInternalTransformerRatio() {
        var previousOutput = Double.POSITIVE_INFINITY
        for (wireOhms in listOf(1.0, 10.0, 100.0)) {
            val c = Circuit(inputOhms = wireOhms)
            val process = SafeTransformerProcess(c.primary, c.secondary, c.input, c.output) { true }
                .apply { ratio = 2.0 }
            c.root.addProcess(process)
            c.settle()
            close(c.secondary.voltage, c.primary.voltage * 2.0)
            assertTrue(c.secondary.voltage < previousOutput)
            close(c.supply.power, c.winding.power + c.load.power)
            previousOutput = c.secondary.voltage
        }
    }

    @Test fun anIdleHvTapUsesOnlyItsVoltageResolutionBudgetAndRejectsLargerErrors() {
        val c = Circuit(inputOhms = .002, loadOhms = 1e9)
        c.supply.voltage = 14000.0
        val process = SafeTransformerProcess(c.primary, c.secondary, c.input, c.output) { true }
            .apply { voltageTarget = 850.0 }
        c.root.addProcess(process)
        c.settle()
        assertEquals("TRANSFERRING", process.status)
        assertTrue(c.input.enabled && c.output.enabled)
        assertEquals(850.0 * 850.0 / 1e9, c.output.power, 1e-10)
        assertTrue(-c.input.power > 0.0, "The idle load was discarded")
        val quantumWatts = 14000.0 * (Math.ulp(14000.0) + Math.ulp(c.input.voltage)) / .002
        assertTrue(abs(c.input.power + c.output.power) <= quantumWatts + 1e-7)
        repeat(128) { c.input.voltage = Math.nextUp(c.input.voltage) }
        c.primary.subSystem.stepCalc()
        assertFalse(process.acceptsCandidate(), "A power error larger than voltage quantization was accepted")
    }

    @Test fun aRejectedTransformerIsIsolatedBeforeItsHealthySourceIsTripped() {
        val c = Circuit()
        var sourceTrips = 0
        c.root.addProcess(object : ConservativePowerProcess {
            override fun rootSystemPreStepProcess() {}
            override fun acceptsCandidate() = true
            override fun connectedSystems() = setOfNotNull(c.feed.subSystem)
            override fun failClosed() { sourceTrips++ }
        })
        val process = SafeTransformerProcess(c.primary, c.secondary, c.input, c.output) { true }
        c.root.addProcess(object : ConservativePowerProcess by process {
            override fun acceptsCandidate() = !c.input.enabled && !c.output.enabled
        })
        c.root.step()
        assertEquals("NON_CONVERGENT", process.status)
        assertOpen(c)
        assertEquals(0, sourceTrips)
        close(c.feed.voltage, 50.0)
    }

    @Test fun anEnergizedOutputAtTheSetpointNeedsNoTransferButStillAllowsForwardAndReversePower() {
        val c = Circuit(inputOhms = .002, loadOhms = 1e18)
        c.supply.voltage = 3200.0
        val motor = VoltageState(); c.root.addState(motor)
        val emf = VoltageSource("coasting output", motor, null).apply { voltage = 3200.0000001 }
        c.root.addComponent(emf)
        c.root.addComponent(Resistor(motor, c.secondary).apply { resistance = .003 })
        val controller = SafeTransformerProcess(c.primary, c.secondary, c.input, c.output) { true }
            .apply { voltageTarget = 3200.0 }
        c.root.addProcess(controller)
        c.settle()
        assertEquals("IDLE", controller.status)
        assertFalse(c.input.enabled)
        assertTrue(c.output.enabled)
        assertEquals(emf.voltage, c.secondary.voltage, 1e-9)
        assertEquals(0.0, c.input.power, 1e-7)
        assertEquals(0.0, c.output.power, 1e-7)
        emf.voltage = 3200.01
        c.settle()
        assertTrue(c.input.enabled && c.output.enabled)
        assertTrue(c.input.power > 0.0 && c.output.power < 0.0)
        close(-c.input.power, c.output.power)
        emf.voltage = 3199.99
        c.settle()
        assertTrue(c.input.power < 0.0 && c.output.power > 0.0)
        close(-c.input.power, c.output.power)
    }
    @Test fun powerBalanceAloneCannotAcceptAnOffTargetControlCommand() {
        for (regulated in listOf(false, true)) {
            val c = Circuit()
            val efficiency = if (regulated) .97 else 1.0
            val controller: ConservativePowerProcess = if (regulated)
                RegulatedPowerProcess(c.primary, null, c.secondary, null, c.input, c.output,
                    { true }, { limits() }, { 100.0 })
            else SafeTransformerProcess(c.primary, c.secondary, c.input, c.output) { true }.apply { ratio = 2.0 }
            c.root.addProcess(controller); c.settle()
            c.output.voltage = 80.0
            val watts = 80.0 * 80.0 / c.load.resistance / efficiency
            c.input.voltage = (50.0 + kotlin.math.sqrt(2500.0 - 4 * c.winding.resistance * watts)) / 2
            setOf(c.primary.subSystem, c.secondary.subSystem).forEach { it.stepCalc() }
            assertTrue(balancedPower(-pendingSourcePower(c.input), pendingSourcePower(c.output), efficiency))
            assertFalse(controller.acceptsCandidate(), "A balanced but off-target command bypassed control")
        }
    }

    @Test fun anUnloadedTransformerCannotCommitAnObsoleteInducedVoltageAfterASupplyChange() {
        val c = Circuit(loadOhms = 1e18)
        val controller = SafeTransformerProcess(c.primary, c.secondary, c.input, c.output) { true }.apply { ratio = .25 }
        c.root.addProcess(controller); c.settle()
        assertFalse(c.input.enabled)
        c.supply.voltage = 10.0
        setOf(c.primary.subSystem, c.secondary.subSystem).forEach { it.stepCalc() }
        assertTrue(balancedPower(-pendingSourcePower(c.input), pendingSourcePower(c.output)))
        assertFalse(controller.acceptsCandidate(), "Zero watts hid a stale transformer ratio")
        c.root.step()
        assertEquals(2.5, c.secondary.voltage, 1e-8)
        assertFalse(c.input.enabled)
    }

}
