package mods.eln.transparentnode.cord

import mods.eln.Eln
import mods.eln.node.six.SixNodeElement
import mods.eln.sim.mna.component.Resistor
import mods.eln.sixnode.lampsupply.LampSupplyElement
import java.util.Collections
import java.util.IdentityHashMap

/** A passive link to the selected supply, rather than a second ideal source or a copied voltage.
 * Both outlet modes feed the same breaker. Stable links never rebuild solver topology. */
class WirelessOutletPower(private val outlet: OutletElement) {
    val conductor=Resistor(null,null).apply { resistance=.08 }
    var supply: LampSupplyElement?=null
        private set
    fun refresh() {
        val here=outlet.coordinate()
        val next=if(outlet.wireless)LampSupplyElement.channelMap[outlet.channel]?.asSequence()
            ?.filter { it.element.isInRange(here) && it.element.getChannelState(it.id) }
            ?.minByOrNull { it.element.coordinate!!.trueDistanceTo(here) }?.element else null
        if(next===supply)return
        disconnect()
        if(next!=null) {
            supply=next;links.add(this)
            conductor.connectTo(next.powerLoad,outlet.input)
            conductor.setOwner("WirelessOutlet@${outlet.coordinate()}")
            Eln.simulator.addElectricalComponent(conductor)
        }
        outlet.needPublish()
    }
    fun disconnect() {
        if(supply!=null) { Eln.simulator.removeElectricalComponent(conductor);conductor.connectTo(null,null) }
        supply=null;links.remove(this)
    }
    companion object {
        private val links=Collections.newSetFromMap(IdentityHashMap<WirelessOutletPower,Boolean>())
        /** Remove bridges before their source loads leave the solver, including chunk unload. */
        fun disconnectSupply(element: SixNodeElement) { links.filter { it.supply===element }.forEach { it.disconnect();it.outlet.needPublish() } }
    }
}
