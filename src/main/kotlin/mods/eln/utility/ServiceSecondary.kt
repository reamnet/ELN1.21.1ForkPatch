package mods.eln.utility

import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.ElectricalConnection
import mods.eln.gridnode.GridElement
import mods.eln.sim.mna.SubSystem
import mods.eln.sim.mna.component.Bipole
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.state.CurrentState
import mods.eln.sim.mna.state.State
import mods.eln.sim.nbt.NbtElectricalLoad

/** Multi-port components report the current at the queried terminal, not a fictitious two-pin current. */
interface PortCurrentComponent { fun portCurrent(state: State): Double }

/** Linear, power-conserving scalar transformer tap. No nonlinear iterations or port probes.
 * Vout = ratio * Vin, Iin = ratio * Iout. The common reference is implicit in ELN. */
class ServiceTap(primary: State,secondary: State,ratio: Double): Bipole(primary,secondary),PortCurrentComponent {
    init { require(ratio.isFinite() && ratio>0) }
    val currentState=CurrentState()
    var ratio=ratio
        set(value) { require(value.isFinite() && value>0);if(field!=value) { field=value;dirty() } }
    var enabled=true
        set(value) { if(field!=value) { field=value;dirty() } }
    override fun addToSubsystem(system: SubSystem) { super.addToSubsystem(system);system.addState(currentState) }
    override fun quitSubSystem() { localSubSystem?.states?.remove(currentState);super.quitSubSystem() }
    override fun applyToSubsystem(system: SubSystem) {
        if(!enabled) { system.addToA(currentState,currentState,1.0);return }
        system.addToA(aPin,currentState,-ratio);system.addToA(bPin,currentState,1.0)
        system.addToA(currentState,aPin,-ratio);system.addToA(currentState,bPin,1.0)
    }
    override fun getCurrent() = if(enabled)-ratio*currentState.state else 0.0
    override fun portCurrent(state: State)=if(!enabled)0.0 else if(state===aPin)current else currentState.state
}

/** A normal 240 V wall feeder carries the service power into the cabinet. ELN uses
 * scalar 120/120/240 V equivalents rather than an explicit neutral and AC phases.
 * These passive taps conserve energy; they do not regulate a sagging feeder.
 * Old saved three-channel overhead feeds retain their original independent buses. */
class WiredServiceSplit(private val owner: GridElement,private val buses: List<NbtElectricalLoad>) {
    private val taps=(0..1).map { i ->
        val pin=NbtElectricalLoad("wiredServiceTap$i").apply { serialResistance=.00001 }
        owner.electricalLoadList.add(pin)
        owner.electricalComponentList.add(Resistor(pin,buses[i]).apply { resistance=.0002 })
        ServiceTap(buses[2],pin,.5).apply { enabled=false }.also(owner.electricalComponentList::add)
    }
    fun update() {
        val wired=owner.gridLinkList.isEmpty() && owner.gridLinksBooting.isEmpty() &&
            buses[2].connectedComponents.any { it is ElectricalConnection }
        taps.forEach { it.enabled=wired }
    }
}

/** Three scalar service equivalents: left/right 120 V circuits and a 240 V circuit.
 * All draw from one real transformer output, so the transformer's kW limit is shared. */
class ServiceSecondary(owner: TransparentNodeElement,source: ElectricalLoad,private val nominal: ()->Double) {
    val loads=List(3) { NbtElectricalLoad("serviceBus$it").apply { serialResistance=.00001 } }
    val voltages=listOf(120.0,120.0,240.0)
    private val taps=loads.mapIndexed { i,load ->
        val pin=NbtElectricalLoad("serviceTap$i")
        val tap=ServiceTap(source,pin,voltages[i]/nominal())
        owner.electricalLoadList.add(pin)
        owner.electricalComponentList.add(Resistor(pin,load).apply { resistance=.0002 })
        owner.electricalComponentList.add(tap)
        tap
    }
    init { owner.electricalLoadList.addAll(loads) }
    fun update() { taps.forEachIndexed { i,t -> t.ratio=voltages[i]/nominal() } }
}

object ServiceTerminals {
    const val LEFT=40;const val RIGHT=41;const val LINE240=42;const val BUNDLE=50
    const val HV_LINE_BUNDLE=52;const val HV_LOAD_BUNDLE=53
    fun index(terminal: Int)=terminal-LEFT
    fun key(terminal: Int)=when(terminal) { LEFT -> "SERVICE_L1";RIGHT -> "SERVICE_L2";LINE240 -> "SERVICE_240";BUNDLE -> "SERVICE_BUNDLE";HV_LINE_BUNDLE,HV_LOAD_BUNDLE -> "HV_THREE_PHASE";else -> "HV${(terminal-32)%4}" }
    fun label(terminal: Int)=when(terminal) {
        LEFT -> mods.eln.i18n.I18N.tr("Service L1: 120 V")
        RIGHT -> mods.eln.i18n.I18N.tr("Service L2: 120 V")
        LINE240 -> mods.eln.i18n.I18N.tr("Service: 240 V")
        BUNDLE -> mods.eln.i18n.I18N.tr("120/240 V service drop")
        HV_LINE_BUNDLE -> mods.eln.i18n.I18N.tr("Three-phase line")
        HV_LOAD_BUNDLE -> mods.eln.i18n.I18N.tr("Three-phase load")
        else -> mods.eln.i18n.I18N.tr("Phase %1$: %2$",listOf("A","B","C")[((terminal-32)%4).coerceIn(0,2)],if(terminal>=36)mods.eln.i18n.I18N.tr("Load") else mods.eln.i18n.I18N.tr("Line"))
    }
}
