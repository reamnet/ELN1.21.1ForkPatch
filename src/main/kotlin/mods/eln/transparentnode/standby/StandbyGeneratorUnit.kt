package mods.eln.transparentnode.standby

import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.nbt.NbtElectricalLoad
import net.minecraft.nbt.CompoundTag

enum class StandbyMode { AUTO, MANUAL, OFF }

/** Fuel and engine timers advance only after an accepted physical electrical step. */
class StandbyGeneratorUnit(owner: TransparentNodeElement) {
    companion object { const val MAX_WATTS=10_000.0;const val IDLE_WATTS=300.0 }
    val output=NbtElectricalLoad("standbyOutput").apply { serialResistance=.000001 }
    private val internal=NbtElectricalLoad("standbyAlternator").apply { serialResistance=.000001 }
    val source=SwitchableVoltageSource("standbyEngine").apply { connectTo(internal,null) }
    private val wire=Resistor(internal,output).apply { resistance=.005 }
    private val leakage=Resistor(output,null).apply { resistance=1e10 }
    val fuel=StandbyTank { owner.needPublish() }
    val controller=StandbyAlternator(source,this)
    private var attachedRoot: mods.eln.sim.mna.RootSystem?=null
    var mode=StandbyMode.AUTO
    var channel=""
    var nominal=240.0
        private set
    var status="STANDBY"
        private set
    var fault=""
        private set
    var running=false
        private set
    private var starting=0.0
    private var cooling=0.0
    private var overload=0.0
    var requested=false
    val watts get()=if(source.enabled)source.power.coerceAtLeast(0.0) else 0.0
    init {
        owner.electricalLoadList.addAll(listOf(output,internal))
        owner.electricalComponentList.addAll(listOf(source,wire,leakage))
        owner.electricalProcessList.add(IProcess(::tick))
    }
    fun setVoltage(value: Double): Boolean {
        if(!TransferControl.validVoltage(value))return false
        nominal=value;stop();return true
    }
    fun attach(root: mods.eln.sim.mna.RootSystem) {
        if(attachedRoot===root)return
        attachedRoot?.removeProcess(controller);attachedRoot=root
        if(running)root.addProcess(controller)
    }
    fun detach() { stop();attachedRoot=null }
    fun stop() { attachedRoot?.removeProcess(controller);source.enabled=false;running=false;starting=0.0;cooling=0.0;overload=0.0 }
    fun reset() { fault="";stop() }
    private fun trip(reason: String) { fault=reason;stop();status=reason }
    fun tick(seconds: Double) {
        if(!seconds.isFinite() || seconds<=0)return
        if(running) {
            // Account the last accepted electrical solve once, including idle fuel consumption.
            if(!fuel.consume(maxOf(IDLE_WATTS,source.power.coerceAtLeast(0.0))*seconds)) { trip("EMPTY");return }
            if(controller.problem.isNotEmpty()) { trip(controller.problem);return }
            if(output.voltage<nominal*.85) { trip("OVERLOAD");return }
            val bad=source.power>MAX_WATTS*1.10 || source.current>200.0002 ||
                source.power>=minOf(MAX_WATTS,nominal*200)*.995 && output.voltage<nominal*.98 || source.power < -50.0
            overload=if(bad)overload+seconds else 0.0
            if(overload>=.5) { trip(if(source.power < -50.0)"BACKFEED" else "OVERLOAD");return }
        }
        if(mode==StandbyMode.OFF) { stop();status="OFF";return }
        if(fault=="EMPTY" && fuel.availableJoules>=MAX_WATTS*seconds)fault=""
        if(fault.isNotEmpty()) { status=fault;source.enabled=false;return }
        val demand=mode==StandbyMode.MANUAL || requested
        if(!demand && !running) { starting=0.0;status="STANDBY";return }
        if(fuel.availableJoules<MAX_WATTS*seconds) { trip("EMPTY");return }
        if(demand)cooling=0.0
        if(!running) {
            starting+=seconds;status="STARTING"
            if(starting>=3.0) { running=true;source.voltage=nominal;source.enabled=true;attachedRoot?.addProcess(controller);status="RUNNING" }
        } else if(!demand) {
            cooling+=seconds;status="COOLDOWN"
            if(cooling>=5.0) { stop();status="STANDBY" }
        } else status="RUNNING"
    }
    fun read(nbt: CompoundTag) {
        stop();fault=nbt.getString("standbyFault").takeIf { it in listOf("EMPTY","OVERLOAD","BACKFEED") } ?: ""
        nominal=nbt.getDouble("standbyVoltage").takeIf(TransferControl::validVoltage) ?: 240.0
        mode=StandbyMode.entries.getOrNull(nbt.getInt("standbyMode")) ?: StandbyMode.AUTO
        channel=StandbyLink.channel(nbt.getString("standbyChannel"));fuel.read(nbt)
    }
    fun write(nbt: CompoundTag) {
        nbt.putDouble("standbyVoltage",nominal);nbt.putInt("standbyMode",mode.ordinal);nbt.putString("standbyChannel",channel);nbt.putString("standbyFault",fault);fuel.write(nbt)
    }
}
