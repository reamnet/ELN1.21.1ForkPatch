package mods.eln.transparentnode.standby

import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.power.ConservativePowerProcess
import mods.eln.sim.power.commandMatches
import mods.eln.sim.power.pendingSourcePower
import kotlin.math.sqrt

/** One bounded source participant; stopped engines never probe the network. */
class StandbyAlternator(private val source: SwitchableVoltageSource,private val unit: StandbyGeneratorUnit) : ConservativePowerProcess {
    var problem=""
        private set
    private var budget=0.0
    private var command=0.0
    override fun beginStep(seconds: Double) {
        problem=""
        budget=if(seconds.isFinite() && seconds>0)minOf(StandbyGeneratorUnit.MAX_WATTS,unit.nominal*200,unit.fuel.availableJoules/seconds) else 0.0
    }
    override fun prepareStep() { if(source.enabled) { command=unit.nominal;source.voltage=command } }
    override fun rootSystemPreStepProcess() {
        if(!source.enabled)return
        val t=source.subSystem?.sourceThevenin(source) ?: return
        if(!t.voltage.isFinite() || t.resistance.isNaN() || t.resistance<=0) { failClosed();return }
        if(t.voltage>unit.nominal*(1+1e-6)) { problem="BACKFEED";source.enabled=false;return }
        if(t.resistance.isInfinite() || t.resistance>=1e18) { command=unit.nominal;source.voltage=command;return }
        val discriminant=sqrt(t.voltage*t.voltage+4*budget*t.resistance)
        val powerVoltage=if(t.voltage>=0)(t.voltage+discriminant)*.5 else
            2*budget*t.resistance/(discriminant-t.voltage)
        command=minOf(unit.nominal,powerVoltage,t.voltage+200*t.resistance).coerceAtLeast(0.0)
        source.voltage=command
    }
    override fun acceptsCandidate(): Boolean {
        if(!source.enabled)return true
        val power=pendingSourcePower(source)
        val current=-(source.subSystem?.pendingValue(source.currentState) ?: Double.NaN)
        return power.isFinite() && commandMatches(source.voltage,command) && power>=-1e-6 &&
            power<=budget*(1+1e-6)+1e-5 && current in -1e-6..200.0002
    }
    override fun failClosed() { problem=if(pendingSourcePower(source) < -1e-6)"BACKFEED" else "OVERLOAD";source.enabled=false }
    override fun trialSources()=if(source.enabled)listOf(source) else emptyList()
    override fun connectedSystems()=setOfNotNull(source.subSystem)
}
