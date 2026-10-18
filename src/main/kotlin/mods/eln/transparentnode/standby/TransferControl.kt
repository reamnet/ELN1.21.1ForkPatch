package mods.eln.transparentnode.standby

import mods.eln.sim.mna.SubSystem
import mods.eln.sim.mna.component.Bipole
import mods.eln.sim.mna.state.State
import java.util.WeakHashMap

/** A genuinely open contact has no conductance and reports no current. */
class TransferContact(a: State,b: State) : Bipole(a,b) {
    var closed=false
        set(value) { if(field!=value) { field=value;dirty() } }
    val resistance=.002
    val power get()=voltage*current
    override fun applyToSubsystem(system: SubSystem) {
        if(!closed)return
        val conductance=1/resistance
        system.addToA(aPin,aPin,conductance);system.addToA(aPin,bPin,-conductance)
        system.addToA(bPin,bPin,conductance);system.addToA(bPin,aPin,-conductance)
    }
    override fun getCurrent()=if(closed)voltage/resistance else 0.0
}

enum class TransferFeed { OPEN, MAINS, GENERATOR }

/** Physical-time debounce and break-before-make; no timers run during solver retries. */
class TransferControl {
    var selected=TransferFeed.OPEN
        private set
    var request=false
        private set
    var backupFault=false
        private set
    private var pending=TransferFeed.OPEN
    private var breakTime=0.0
    private var failed=0.0
    private var recovered=0.0
    private var backupStable=0.0
    private var backupStopped=0.0
    fun reset() { selected=TransferFeed.OPEN;pending=TransferFeed.OPEN;request=false;backupFault=false;breakTime=0.0;failed=0.0;recovered=0.0;backupStable=0.0;backupStopped=0.0 }
    fun step(seconds: Double,mains: Double,backup: Double,nominal: Double,enabled: Boolean=true): TransferFeed {
        if(!seconds.isFinite() || seconds<=0)return selected
        if(!enabled || !validVoltage(nominal)) { reset();return selected }
        fun valid(v: Double,low: Double,high: Double)=v.isFinite() && v/nominal in low..high
        val mainGood=valid(mains,if(selected==TransferFeed.MAINS).8 else .9,if(selected==TransferFeed.MAINS)1.2 else 1.1)
        val backupGood=valid(backup,.85,1.15)
        if(selected==TransferFeed.GENERATOR && !backupGood)backupFault=true
        backupStopped=if(backup.isFinite() && backup<nominal*.1)backupStopped+seconds else 0.0
        if(backupStopped>=1.0)backupFault=false
        failed=if(mainGood)0.0 else failed+seconds
        recovered=if(mainGood)recovered+seconds else 0.0
        backupStable=if(backupGood)backupStable+seconds else 0.0
        if(failed>=1.0)request=true
        if(recovered>=5.0) { request=false;backupFault=false }
        val desired=when {
            mainGood && !request -> TransferFeed.MAINS
            request && backupStable>=1.0 && !backupFault -> TransferFeed.GENERATOR
            else -> TransferFeed.OPEN
        }
        if(selected!=TransferFeed.OPEN && selected!=desired)selected=TransferFeed.OPEN
        if(desired!=pending) { pending=desired;breakTime=0.0;return selected }
        if(selected==TransferFeed.OPEN) {
            breakTime+=seconds
            if(breakTime>=.25)selected=pending
        }
        return selected
    }
    companion object { fun validVoltage(v: Double)=v.isFinite() && v in 12.0..51_200.0 }
}

/** Weak owners, dimension-scoped requests and a five-second lease prevent stale remote starts. */
object StandbyLink {
    private data class Request(val channel: String,val expiry: Long)
    private val worlds=WeakHashMap<Any,WeakHashMap<Any,Request>>()
    fun channel(value: String)=value.trim().take(64)
    fun update(world: Any,owner: Any,channel: String,tick: Long,wanted: Boolean) {
        val entries=worlds.getOrPut(world) { WeakHashMap() }
        if(!wanted || channel.isBlank())entries.remove(owner) else entries[owner]=Request(channel,tick+100)
        entries.entries.removeIf { it.value.expiry<tick }
    }
    fun remove(world: Any,owner: Any) { worlds[world]?.remove(owner) }
    fun requested(world: Any,channel: String,tick: Long): Boolean = channel.isNotBlank() &&
        worlds[world]?.values?.any { it.channel==channel && it.expiry>=tick }==true
}
