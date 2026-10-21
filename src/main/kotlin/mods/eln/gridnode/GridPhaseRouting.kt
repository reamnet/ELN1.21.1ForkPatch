package mods.eln.gridnode

import mods.eln.sim.ElectricalLoad
import java.util.Collections
import java.util.IdentityHashMap

/** A single-conductor branch inherits its upstream leg; it must not become a bridge between legs. */
object GridPhaseRouting {
    fun phases(start: GridElement,terminal: Int): Set<Int> {
        val result=HashSet<Int>()
        val visited=Collections.newSetFromMap(IdentityHashMap<ElectricalLoad,Boolean>())
        val queue=ArrayDeque<Pair<GridElement,Int>>();queue.add(start to terminal)
        while(queue.isNotEmpty()) {
            val (element,port)=queue.removeFirst()
            val explicit=element.gridPhase(port)
            if(explicit!=null) { result.add(explicit);continue }
            val bus=element.phaseBusTerminals(port).flatMap(element::gridLoads).toSet()
            val newlySeen=bus.map { visited.add(it) }.any { it }
            if(!newlySeen)continue
            for(link in element.gridLinkList) {
                val local=link.getTerminal(element);val loads=element.gridLoads(local)
                val index=loads.indexOfFirst { it in bus }
                if(index<0)continue
                val other=runCatching { link.getOtherElement(element) }.getOrNull() ?: continue
                val remote=link.getTerminal(other)
                val single=when(remote) { 52->32+index;53->36+index;else->remote }
                if(other.gridLoads(single).size==1)queue.add(other to single)
            }
        }
        return result
    }
    fun compatible(a: GridElement,from: Int,b: GridElement,to: Int)=(phases(a,from)+phases(b,to)).size<=1
}
