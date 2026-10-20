package mods.eln.utility

import mods.eln.misc.Coordinate
import mods.eln.sim.ElectricalLoad
import mods.eln.sixnode.lampsupply.LampSupplyElement
import java.util.IdentityHashMap
import java.util.WeakHashMap

/** The existing lamp-supply protocol, also implemented by individually protected panel circuits. */
interface WirelessPowerSupply {
    val owner: Any
    val coordinate: Coordinate
    val powerLoad: ElectricalLoad
    val range: Int
    fun getChannelState(id: Int): Boolean
    fun addToRp(resistance: Double)
    fun isInRange(target: Coordinate)=target.dimension==coordinate.dimension && coordinate.trueDistanceTo(target)<=range
}
class WirelessPowerHandle(val element: WirelessPowerSupply,val id: Int)
object WirelessPowerChannels {
    private class Legacy(val source: LampSupplyElement): WirelessPowerSupply {
        override val owner get()=source
        override val coordinate get()=source.coordinate!!
        override val powerLoad get()=source.powerLoad
        override val range get()=source.range
        override fun getChannelState(id: Int)=source.getChannelState(id)
        override fun addToRp(resistance: Double)=source.addToRp(resistance)
    }
    private val legacy=WeakHashMap<LampSupplyElement.PowerSupplyChannelHandle,WirelessPowerHandle>()
    private val extra=HashMap<String,MutableList<WirelessPowerHandle>>()
    private data class Bridge(val source: Any,val disconnect: ()->Unit)
    private val bridges=IdentityHashMap<Any,Bridge>()
    fun channels(channel: String): List<WirelessPowerHandle>? {
        val old=LampSupplyElement.channelMap[channel]
        val added=extra[channel]
        if(old.isNullOrEmpty() && added.isNullOrEmpty())return null
        return buildList {
            old?.forEach { handle ->
                // Values do not retain the Java handle key: unloaded registrations can be collected.
                add(legacy.getOrPut(handle) { WirelessPowerHandle(Legacy(handle.element),handle.id) })
            }
            added?.let(::addAll)
        }
    }
    fun register(channel: String,handle: WirelessPowerHandle) {
        if(channel.isBlank())return
        val list=extra.getOrPut(channel) { ArrayList() }
        if(handle !in list)list.add(handle)
    }
    fun unregister(handle: WirelessPowerHandle) { extra.values.forEach { it.remove(handle) };extra.entries.removeIf { it.value.isEmpty() } }
    fun trackBridge(owner: Any,source: Any,disconnect: ()->Unit) { bridges[owner]=Bridge(source,disconnect) }
    fun untrackBridge(owner: Any) { bridges.remove(owner) }
    /** Called before either endpoint's electrical loads leave the simulator. */
    @JvmStatic fun disconnectOwner(owner: Any) {
        if(owner is LampSupplyElement)legacy.entries.removeIf { it.key.element===owner }
        val remove=bridges.filter { it.key===owner || it.value.source===owner }.keys.toList()
        remove.forEach { bridges.remove(it)?.disconnect?.invoke() }
        extra.values.forEach { it.removeIf { h -> h.element.owner===owner } };extra.entries.removeIf { it.value.isEmpty() }
    }
}
