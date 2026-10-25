package mods.eln.transparentnode.firealarm

import net.minecraft.core.BlockPos
import java.util.WeakHashMap

/** Loaded devices renew short leases. Channels are isolated by world identity and range. */
object AlarmNetwork {
    const val RANGE=128
    private const val LEASE=50L
    data class Panel(val pos: BlockPos,val tick: Long,val powered: Boolean,val alarm: Boolean,val silenced: Boolean)
    data class Field(val pos: BlockPos,val tick: Long,val label: String,val source: Boolean,val active: Boolean,val ownPower: Boolean,val owner: Long?,val standbyWatts: Double,val alarmWatts: Double,val silencedWatts: Double=0.0,val silenced: Boolean=false)
    data class Lease(val owner: Long?,val powered: Boolean,val alarm: Boolean,val silenced: Boolean)
    data class Snapshot(val members: List<Field>,val active: List<Field>,val watts: Double)
    private class Channel { val panels=HashMap<Long,Panel>();val fields=HashMap<Long,Field>();var pruned=Long.MIN_VALUE }
    private val worlds=WeakHashMap<Any,MutableMap<String,Channel>>()
    fun channel(name: String)=name.trim().take(64)
    private fun get(world: Any,name: String): Channel?=channel(name).takeIf { it.isNotEmpty() }?.let { worlds.getOrPut(world) { HashMap() }.getOrPut(it) { Channel() } }
    private fun near(a: BlockPos,b: BlockPos)=a.distSqr(b)<=RANGE.toDouble()*RANGE
    private fun prune(c: Channel,now: Long) {
        if(now-c.pruned in 0..19)return
        c.pruned=now;c.panels.entries.removeIf { now-it.value.tick>LEASE };c.fields.entries.removeIf { now-it.value.tick>LEASE }
    }
    fun publishPanel(world: Any,name: String,panel: Panel) { get(world,name)?.let { prune(it,panel.tick);it.panels[panel.pos.asLong()]=panel } }
    fun lease(world: Any,name: String,pos: BlockPos,now: Long): Lease {
        val c=get(world,name) ?: return Lease(null,false,false,false);prune(c,now)
        val p=c.panels.values.filter { it.powered && near(it.pos,pos) && now-it.tick<=LEASE }.minByOrNull { it.pos.distSqr(pos) }
        return if(p==null)Lease(null,false,false,false) else Lease(p.pos.asLong(),true,p.alarm,p.silenced)
    }
    fun publishField(world: Any,name: String,field: Field) { get(world,name)?.let { prune(it,field.tick);it.fields[field.pos.asLong()]=field } }
    fun snapshot(world: Any,name: String,pos: BlockPos,now: Long,alarm: Boolean,silenced: Boolean): Snapshot {
        val c=get(world,name) ?: return Snapshot(emptyList(),emptyList(),0.0);prune(c,now)
        val members=c.fields.values.filter { near(it.pos,pos) && now-it.tick<=LEASE }
        return Snapshot(members,members.filter { it.source && it.active },members.sumOf { if(it.ownPower || it.owner!=null && it.owner!=pos.asLong())0.0 else it.standbyWatts+if((alarm || it.active) && !it.source) { if(if(it.active)it.silenced else silenced)it.silencedWatts else it.alarmWatts } else 0.0 })
    }
    fun remove(world: Any,name: String,pos: BlockPos) { get(world,name)?.let { it.fields.remove(pos.asLong());it.panels.remove(pos.asLong()) } }
    fun clear()=worlds.clear()
}

/** Accepted physical steps account input, alarm loads and backup energy once. */
class AlarmPanelState {
    companion object { const val CAPACITY=86_400.0;const val IDLE_WATTS=5.0;const val CHARGE_WATTS=5.0 }
    var energy=0.0
    var latched=false
    var silenced=false
    var powered=false;private set
    var testSeconds=0.0
    val alarm get()=latched || testSeconds>0
    fun account(seconds: Double,inputWatts: Double,deviceWatts: Double) {
        if(!seconds.isFinite() || seconds<=0)return
        val input=inputWatts.takeIf { it.isFinite() && it>=0 } ?: 0.0
        val demand=IDLE_WATTS+deviceWatts.coerceAtLeast(0.0)
        powered=input+energy/seconds+1e-9>=demand
        energy=(energy+(input-if(powered)demand else 0.0)*seconds).coerceIn(0.0,CAPACITY)
        testSeconds=(testSeconds-seconds).coerceAtLeast(0.0)
    }
    fun trigger(active: Boolean) { if(powered && active && !latched) { latched=true;silenced=false } }
    fun reset(active: Boolean): Boolean { testSeconds=0.0;if(active)return false;latched=false;silenced=false;return true }
    fun test() { if(powered) { testSeconds=10.0;silenced=false } }
}
