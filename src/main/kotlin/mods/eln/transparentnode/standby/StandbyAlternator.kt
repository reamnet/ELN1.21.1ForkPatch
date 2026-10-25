package mods.eln.transparentnode.standby

import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.power.ConservativePowerProcess
import mods.eln.sim.power.commandMatches
import mods.eln.sim.power.pendingSourcePower
import mods.eln.sim.power.supplyLimitReached
import kotlin.math.sqrt

/** One bounded source participant; stopped engines never probe the network. */
class StandbyAlternator(private val source: SwitchableVoltageSource,private val unit: StandbyGeneratorUnit) : ConservativePowerProcess {
    var problem=""
        private set
    private var budget=0.0
    private var command=0.0
    private var region="VOLTAGE"
    override fun beginStep(seconds: Double) {
        problem=""
        budget=if(seconds.isFinite() && seconds>0)minOf(unit.maxWatts,unit.nominal*unit.maxAmps,unit.fuel.availableJoules/seconds) else 0.0
    }
    override fun prepareStep() { if(source.enabled) { command=unit.nominal;source.voltage=command } }
    override fun rootSystemPreStepProcess() {
        if(!source.enabled)return
        val t=source.subSystem?.sourceThevenin(source) ?: return
        if(!t.voltage.isFinite() || t.resistance.isNaN() || t.resistance<=0) { failClosed();return }
        // Another regulated input is also a trial voltage source. A Newton probe can
        // temporarily place it above our voltage; that is not committed reverse power.
        // Keep the source law active and reject negative current in acceptsCandidate.
        // Only failClosed, after the network has exhausted valid candidates, trips it.
        if(t.resistance.isInfinite() || t.resistance>=1e18) { command=unit.nominal;source.voltage=command;region="VOLTAGE";return }
        val discriminant=sqrt(t.voltage*t.voltage+4*budget*t.resistance)
        val powerVoltage=if(t.voltage>=0)(t.voltage+discriminant)*.5 else
            2*budget*t.resistance/(discriminant-t.voltage)
        val currentVoltage=t.voltage+unit.maxAmps*t.resistance
        command=minOf(unit.nominal,powerVoltage,currentVoltage).coerceAtLeast(0.0)
        source.voltage=command
        region=when(command) { unit.nominal -> "VOLTAGE";currentVoltage -> "CURRENT";else -> "POWER" }
    }
    override fun acceptsCandidate(): Boolean {
        if(!source.enabled)return true
        val power=pendingSourcePower(source)
        val current=-(source.subSystem?.pendingValue(source.currentState) ?: Double.NaN)
        return power.isFinite() && commandMatches(source.voltage,command) && power>=-1e-6 &&
            power<=budget*(1+1e-6)+1e-5 && current in -1e-6..(unit.maxAmps*1.000001) &&
            supplyLimitReached(source.voltage,current,unit.nominal,budget,unit.maxAmps)
    }
    override fun failClosed() { problem=if(pendingSourcePower(source) < -1e-6)"BACKFEED" else "OVERLOAD";source.enabled=false }
    override fun trialSources()=if(source.enabled)listOf(source) else emptyList()
    override fun iterationRegion()=if(source.enabled)region else "OPEN"
    override fun connectedSystems()=setOfNotNull(source.subSystem)
    override fun voltageSearchHints()=mapOf(source to doubleArrayOf(unit.nominal))
}
