package mods.eln.transparentnode.renewable

import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.sim.IProcess
import mods.eln.sim.mna.SubSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sim.power.*
import net.minecraft.nbt.CompoundTag
import kotlin.math.*

enum class SolarPriority { SOLAR_FIRST, GRID_FIRST, SOLAR_ONLY, GRID_ONLY }

/** Perturb-and-observe matching of the panel's input impedance. Updates only on physical steps.
 * A conductance load has a unique operating point even across the panel's I-V knee; it does
 * not impose the negative input resistance of a constant-power load on a weak solar string.
 */
class MpptTracker {
    var resistance=100.0
        private set
    private var previousPower=Double.NaN
    private var direction=-1
    private var elapsed=0.0
    fun observe(seconds: Double,watts: Double,unrestricted: Boolean) {
        if(!unrestricted || !watts.isFinite() || watts<.01) { previousPower=Double.NaN;elapsed=0.0;return }
        elapsed+=seconds
        if(elapsed<.25)return
        elapsed=0.0
        // A current/power-limited upstream controller has a flat P-V plateau.
        // Do not keep reducing impedance across that plateau until its bus collapses.
        if(previousPower.isFinite() && watts<=previousPower*(1+1e-5))direction=-direction
        previousPower=watts
        resistance=(resistance*exp(direction*.035)).coerceIn(.0001,1e10)
    }
    fun read(tag: CompoundTag) {
        resistance=tag.getDouble("mpptResistance").takeIf { it.isFinite() && it in .0001..1e10 } ?: 100.0
        previousPower=Double.NaN;elapsed=0.0
    }
    fun write(tag: CompoundTag) { tag.putDouble("mpptResistance",resistance) }
}

/** A small, explicitly energy-accounted DC-link reservoir decouples solar matching from the
 * regulated output. It starts EMPTY, stores joules in NBT, and cannot supply unpaid power.
 * It is not a battery bank: 5000 J is only a quarter-second at the hybrid's full rating.
 */
class SolarPowerUnit(owner: TransparentNodeElement,val hybrid: Boolean) {
    companion object {
        const val CAPACITY=5000.0
        const val MAX_INPUT=51_200.0
        const val MAX_AMPS=200.0
        const val CHARGE_EFFICIENCY=.98
        const val OUTPUT_EFFICIENCY=.97
    }
    val rating get()=if(hybrid)20_000.0 else 10_000.0
    val solar=NbtElectricalLoad("solarPositive")
    val negative=NbtElectricalLoad("solarNegative")
    val grid=NbtElectricalLoad("solarGrid")
    val output=NbtElectricalLoad("solarOutput")
    private val gridPin=NbtElectricalLoad("solarGridPin")
    private val outputPin=NbtElectricalLoad("solarOutputPin")
    val inputSource=SwitchableVoltageSource("hybridGrid").apply { connectTo(gridPin,null) }
    val outputSource=SwitchableVoltageSource("hybridOutput").apply { connectTo(outputPin,null) }
    private val gridWire=Resistor(grid,gridPin).apply { resistance=.005 }
    private val outputWire=Resistor(outputPin,output).apply { resistance=.001 }
    val harvest=Resistor(solar,negative)
    val tracker=MpptTracker()
    var energy=0.0
        private set
    var enabled=true
    var priority=SolarPriority.SOLAR_FIRST
    var target=120.0
        private set
    private var dt=.05
    private var harvestCap=0.0
    private var solarBudget=0.0
    private var solarUsed=0.0
    private var gridCommand=0.0
    private var outputCommand=0.0
    private var powerRoundoff=0.0
    var pvWatts=0.0
        private set
    var heatWatts=0.0
        private set
    var status="IDLE"
        private set
    var diagnostic=""
        private set
    val gridWatts get()=max(0.0,-inputSource.power)
    val outputWatts get()=outputSource.power.coerceAtLeast(0.0)
    private val solarAllowed get()=enabled && (!hybrid || priority!=SolarPriority.GRID_ONLY)
    private val gridAllowed get()=enabled && hybrid && priority!=SolarPriority.SOLAR_ONLY
    fun setVoltage(v: Double): Boolean {
        if(!v.isFinite() || if(hybrid)v!=120.0 && v!=240.0 else v !in 12.0..3200.0)return false
        target=v;return true
    }
    val controller=object : ConservativePowerProcess {
        override fun beginStep(seconds: Double) {
            if(seconds.isFinite() && seconds>0)dt=seconds
            solarBudget=if(solarAllowed)min(rating,energy/dt*OUTPUT_EFFICIENCY) else 0.0
            harvestCap=if(solarAllowed)min(rating/(CHARGE_EFFICIENCY*OUTPUT_EFFICIENCY),(CAPACITY-energy)/dt/CHARGE_EFFICIENCY) else 0.0
        }
        override fun prepareStep() { if(enabled && !outputSource.enabled) { outputSource.voltage=target;outputSource.enabled=true } }
        override fun rootSystemPreStepProcess() {
            // Input resistance is a controlled electronic sink; its accepted energy is
            // transferred into the reservoir below, not also counted as resistor heat.
            val voltage=solar.subSystem?.solveChecked(solar)?.minus(negative.subSystem?.solveChecked(negative) ?: 0.0) ?: 0.0
            harvest.resistance=if(harvestCap>.00001 && voltage.isFinite() && voltage in 6.0..MAX_INPUT)
                maxOf(tracker.resistance,voltage/MAX_AMPS,voltage*voltage/harvestCap,.0001) else 1e19
            if(!enabled) { open("DISABLED");return }
            val a=probePort(gridPin,null,inputSource)
            val b=probePort(outputPin,null,outputSource)
            if(!b.volts.isFinite() || b.ohms.isNaN() || b.ohms<=0 || b.volts>=target) { open("BACKFEED");return }
            val gridCap=if(gridAllowed && a.volts.isFinite() && a.volts in 12.0..MAX_INPUT && a.ohms>=0 && a.ohms<1e18) {
                val current=if(a.ohms==0.0)MAX_AMPS else minOf(MAX_AMPS,a.volts/(2*a.ohms),max(0.0,(a.volts-6)/a.ohms))
                min(rating,current*(a.volts-a.ohms*current)*OUTPUT_EFFICIENCY)
            } else 0.0
            val cap=min(rating,solarBudget+gridCap)
            if(cap<=1e-8) { open("NO_POWER");return }
            val maxVoltage=if(b.ohms>=1e18)target else {
                val disc=hypot(b.volts,2*sqrt(cap)*sqrt(b.ohms))
                val powerVoltage=if(b.volts>=0)(b.volts+disc)/2 else 2*cap*b.ohms/(disc-b.volts)
                minOf(target,powerVoltage,b.volts+MAX_AMPS*b.ohms).coerceAtLeast(0.0)
            }
            val watts=if(b.ohms>=1e18)0.0 else maxVoltage*((maxVoltage-b.volts)/b.ohms).coerceAtLeast(0.0)
            val solarFirst=!hybrid || priority!=SolarPriority.GRID_FIRST
            solarUsed=if(solarFirst)min(watts,solarBudget) else max(0.0,watts-gridCap)
            val gridOutput=(watts-solarUsed).coerceAtLeast(0.0)
            if(gridOutput>1e-9) {
                val power=gridOutput/OUTPUT_EFFICIENCY
                val amps=2*power/(a.volts+sqrt(max(0.0,a.volts*a.volts-4*a.ohms*power)))
                gridCommand=a.volts-a.ohms*amps;inputSource.voltage=gridCommand;inputSource.enabled=true
            } else inputSource.enabled=false
            outputCommand=maxVoltage;outputSource.voltage=maxVoltage;outputSource.enabled=true
            powerRoundoff=(if(inputSource.enabled)portPowerResolution(gridCommand,a)*OUTPUT_EFFICIENCY else 0.0)+
                portPowerResolution(outputCommand,b)
            status=if(maxVoltage<target*.999)"LIMITED" else if(gridOutput>1 && solarUsed>1)"ASSIST" else if(gridOutput>1)"GRID" else "SOLAR"
        }
        override fun acceptsCandidate(): Boolean {
            val system=solar.subSystem ?: run { diagnostic="no solar system";return false }
            val pv=system.pendingValue(solar)-(negative.subSystem?.pendingValue(negative) ?: return false)
            val amps=pv/harvest.resistance;val power=pv*amps
            if(!pv.isFinite() || power>harvestCap*(1+1e-6)+1e-5 || abs(amps)>MAX_AMPS*1.000001) { diagnostic="PV $pv V, $power W, cap $harvestCap R ${harvest.resistance}";return false }
            if(!outputSource.enabled)return true
            val pin=-pendingSourcePower(inputSource);val pout=pendingSourcePower(outputSource)
            val ia=if(inputSource.enabled)inputSource.subSystem?.pendingValue(inputSource.currentState) ?: Double.NaN else 0.0
            val oa=-(outputSource.subSystem?.pendingValue(outputSource.currentState) ?: Double.NaN)
            val accepted=commandMatches(outputSource.voltage,outputCommand) &&
                (!inputSource.enabled || commandMatches(inputSource.voltage,gridCommand)) &&
                ia in -1e-7..MAX_AMPS*1.000001 && oa in -1e-7..MAX_AMPS*1.000001 &&
                pout<=rating*1.000001 && solarUsed<=solarBudget+1e-6 && solarUsed>=0 &&
                balancedPower(pin+solarUsed/OUTPUT_EFFICIENCY,pout,OUTPUT_EFFICIENCY,powerRoundoff)
            if(!accepted)diagnostic="input $pin W / $ia A; output $pout W / $oa A; solar $solarUsed / $solarBudget W; commands ${inputSource.voltage} / $gridCommand, ${outputSource.voltage} / $outputCommand"
            return accepted
        }
        override fun failClosed() { harvest.resistance=1e19;open("PROTECTED") }
        override fun isolatesLoadOnFailure()=true
        override fun connectedSystems(): Set<SubSystem> = setOfNotNull(solar.subSystem,negative.subSystem,gridPin.subSystem,outputPin.subSystem)
        override fun trialSources()=listOf(inputSource,outputSource)
        override fun startupInputSources()=if(gridAllowed)listOf(inputSource) else emptyList()
        override fun startupOutputTargets()=if(enabled)mapOf(outputSource to target) else emptyMap()
        override fun iterationRegion()=status
    }
    private fun open(reason: String) { inputSource.enabled=false;outputSource.enabled=false;solarUsed=0.0;status=reason }
    init {
        listOf(solar,negative,grid,output,gridPin,outputPin).forEach { it.serialResistance=.000001;owner.electricalLoadList.add(it) }
        owner.electricalComponentList.addAll(listOf(inputSource,outputSource,gridWire,outputWire,harvest,
            // Reference the isolated PV return without a hard ground bond. A 1 Gohm
            // bleed is numerically indistinguishable from a floating island behind
            // low-resistance cable, and its discarded KCL row can reject the solve.
            Resistor(negative,null).apply { resistance=1e6 },Resistor(grid,null).pullDown(),Resistor(output,null).pullDown()))
        owner.electricalProcessList.add(IProcess(::account))
    }
    fun account(seconds: Double) {
        if(!seconds.isFinite() || seconds<=0)return
        val volts=solar.voltage-negative.voltage
        pvWatts=if(volts in 0.0..MAX_INPUT)harvest.power.coerceAtLeast(0.0) else 0.0
        val discharge=if(outputSource.enabled)solarUsed/OUTPUT_EFFICIENCY else 0.0
        energy=(energy+(pvWatts*CHARGE_EFFICIENCY-discharge)*seconds).coerceIn(0.0,CAPACITY)
        heatWatts=pvWatts*(1-CHARGE_EFFICIENCY)+(discharge+gridWatts)*(1-OUTPUT_EFFICIENCY)+gridWire.power+outputWire.power
        tracker.observe(seconds,pvWatts,solarAllowed && harvest.resistance<=tracker.resistance*1.001)
    }
    fun read(tag: CompoundTag) {
        enabled=!tag.contains("solarEnabled") || tag.getBoolean("solarEnabled")
        priority=SolarPriority.entries.getOrNull(tag.getInt("solarPriority")) ?: SolarPriority.SOLAR_FIRST
        target=120.0;setVoltage(tag.getDouble("solarTarget"))
        energy=tag.getDouble("solarJoules").takeIf { it.isFinite() }?.coerceIn(0.0,CAPACITY) ?: 0.0
        tracker.read(tag);open("IDLE")
    }
    fun write(tag: CompoundTag) {
        tag.putBoolean("solarEnabled",enabled);tag.putInt("solarPriority",priority.ordinal);tag.putDouble("solarTarget",target)
        tag.putDouble("solarJoules",energy);tracker.write(tag)
    }
}
