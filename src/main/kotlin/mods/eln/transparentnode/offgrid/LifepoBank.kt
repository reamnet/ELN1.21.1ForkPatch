package mods.eln.transparentnode.offgrid

import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.sim.IProcess
import mods.eln.sim.mna.SubSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sim.nbt.NbtThermalLoad
import mods.eln.sim.power.*
import net.minecraft.nbt.CompoundTag
import kotlin.math.*

/** Stored energy is measured in joules. The cabinet contains cells and a BMS, never an inverter. */
class LifepoState(val kwh: Int) {
    val capacity=kwh*3_600_000.0
    val maxAmps=kwh/16.0*100.0
    var energy=0.0;private set
    var enabled=true
    var fault=""
    val fraction get()=(energy/capacity).coerceIn(0.0,1.0)
    val cellVolts get()=50.0+4.0*fraction
    fun transfer(joules: Double): Double {
        if(!joules.isFinite())return 0.0
        val before=energy;energy=(energy+joules).coerceIn(0.0,capacity);return energy-before
    }
    fun read(tag: CompoundTag) {
        energy=tag.getDouble("lfpJoules").takeIf { it.isFinite() }?.coerceIn(0.0,capacity) ?: 0.0
        enabled=!tag.contains("lfpEnabled") || tag.getBoolean("lfpEnabled")
        fault=tag.getString("lfpFault").takeIf { it in setOf("OVERVOLTAGE","REVERSE","TEMPERATURE") } ?: ""
    }
    fun write(tag: CompoundTag) { tag.putDouble("lfpJoules",energy);tag.putBoolean("lfpEnabled",enabled);tag.putString("lfpFault",fault) }
}

class LifepoBank(owner: TransparentNodeElement,kwh: Int) {
    val state=LifepoState(kwh)
    val positive=NbtElectricalLoad("lfpPositive")
    val negative=NbtElectricalLoad("lfpNegative")
    private val pin=NbtElectricalLoad("lfpCells")
    val source=SwitchableVoltageSource("lfpBms").apply { connectTo(pin,negative) }
    private val wire=Resistor(pin,positive).apply { resistance=.008/(kwh/16.0) }
    val thermal=NbtThermalLoad("lfpThermal").apply { heatCapacity=kwh*30_000.0;Rp=.08;Rs=.1;setAsSlow() }
    val temperature=CabinetTemperature(owner,thermal)
    private var seconds=.05
    private var command=0.0
    private var discharging=0.0
    private var charging=0.0
    var watts=0.0;private set
    val volts get()=positive.voltage-negative.voltage
    val status get()=when {
        state.fault.isNotEmpty()->state.fault
        !state.enabled->"DISABLED"
        watts>1->"BATTERY"
        watts< -1->"CHARGING"
        state.energy<=1e-6->"EMPTY"
        state.fraction>=.99999->"FULL"
        else->"READY"
    }
    val controller=object: ConservativePowerProcess {
        override fun beginStep(dt: Double) {
            if(dt.isFinite() && dt>0)seconds=dt
            temperature.advance(dt)
            if(volts>60.0)state.fault="OVERVOLTAGE"
            if(volts< -1.0)state.fault="REVERSE"
            if(temperature.celsius>65)state.fault="TEMPERATURE"
        }
        override fun prepareStep() {
            if(state.enabled && state.fault.isEmpty()) { source.voltage=state.cellVolts;source.enabled=true }
        }
        override fun rootSystemPreStepProcess() {
            if(!state.enabled || state.fault.isNotEmpty()) { source.enabled=false;return }
            val p=probePort(pin,negative,source)
            if(!p.volts.isFinite() || p.volts>60 || p.volts< -1 || p.ohms.isNaN() || p.ohms<=0) { source.enabled=false;return }
            val bound=maxOf(1.0,state.cellVolts,abs(p.volts))
            discharging=min(state.maxAmps,state.energy/(seconds*bound))
            charging=if(temperature.celsius in 0.0..55.0)min(state.maxAmps,(state.capacity-state.energy)/(.98*seconds*bound)) else 0.0
            // At the energy endpoints the BMS behaves as a directional disconnect,
            // rather than an active voltage clamp that would fight the charger.
            if((discharging<=1e-9 && p.volts<state.cellVolts) || (charging<=1e-9 && p.volts>state.cellVolts)) { source.enabled=false;return }
            command=if(p.ohms>=1e18)state.cellVolts else state.cellVolts.coerceIn(p.volts-charging*p.ohms,p.volts+discharging*p.ohms)
            source.voltage=command;source.enabled=true
        }
        override fun acceptsCandidate(): Boolean {
            if(!source.enabled)return true
            val system=source.subSystem ?: return false
            val amps=-system.pendingValue(source.currentState)
            val power=pendingSourcePower(source)
            return commandMatches(source.voltage,command) && amps in -charging-1e-6..discharging+1e-6 &&
                power*seconds<=state.energy+1e-4 && -power*.98*seconds<=state.capacity-state.energy+1e-4
        }
        override fun failClosed() { source.enabled=false }
        override fun isolatesLoadOnFailure()=true
        override fun connectedSystems(): Set<SubSystem> =setOfNotNull(pin.subSystem,negative.subSystem)
        override fun controlledSources()=listOf(source)
        override fun trialSources()=listOf(source)
        override fun iterationRegion()=when { !source.enabled->"OPEN";source.voltage<state.cellVolts-1e-7->"DISCHARGE_LIMIT";source.voltage>state.cellVolts+1e-7->"CHARGE_LIMIT";else->"CELLS" }
    }
    init {
        listOf(positive,negative,pin).forEach { it.serialResistance=.000001;owner.electricalLoadList.add(it) }
        owner.electricalComponentList.addAll(listOf(source,wire,Resistor(negative,null).apply { resistance=1e6 }))
        owner.thermalLoadList.add(thermal)
        owner.electricalProcessList.add(IProcess(::account))
    }
    fun account(dt: Double) {
        if(!dt.isFinite() || dt<=0)return
        watts=if(source.enabled)source.power.takeIf { it.isFinite() } ?: 0.0 else 0.0
        val requested=-watts*dt*(if(watts<0).98 else 1.0)
        val accepted=state.transfer(requested)
        thermal.movePowerTo(wire.power+max(0.0,-watts*.02)+max(0.0,requested-accepted)/dt)
    }
}
