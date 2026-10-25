package mods.eln.transparentnode.switchgear

import mods.eln.sim.mna.SubSystem
import mods.eln.sim.mna.component.Bipole
import mods.eln.sim.mna.state.State

/** Each pole is an independent physical circuit. Open contacts stamp no conductance. */
class SwitchContact(a: State,b: State,val resistance: Double): Bipole(a,b) {
    var closed=false
        set(value) { if(field!=value) { field=value;dirty() } }
    val power get()=voltage*current
    override fun applyToSubsystem(s: SubSystem) {
        if(!closed)return
        val g=1/resistance
        s.addToA(aPin,aPin,g);s.addToA(aPin,bPin,-g);s.addToA(bPin,bPin,g);s.addToA(bPin,aPin,-g)
    }
    override fun getCurrent()=if(closed)voltage/resistance else 0.0
}
