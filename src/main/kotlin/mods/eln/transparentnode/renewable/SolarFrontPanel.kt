package mods.eln.transparentnode.renewable

import mods.eln.client.power.ControlPanelDisplay
import mods.eln.client.power.PowerPanelDisplay
import mods.eln.misc.Obj3D

object SolarFrontPanel {
    val PARTS=ControlPanelDisplay.PARTS
    fun draw(model: Obj3D,d: SolarDisplay?,hybrid: Boolean) {
        ControlPanelDisplay.body(model)
        val powered=d!=null && (d.pv>6 || d.grid>12 || d.output>12 || d.energy>1)
        if(d!=null) {
            PowerPanelDisplay.equipmentNumber(model,d.pvWatts,powered,"Readout0")
            PowerPanelDisplay.equipmentNumber(model,if(hybrid)d.gridWatts else d.feedWatts,powered,"Readout1")
            PowerPanelDisplay.equipmentNumber(model,d.output,powered,"Readout2")
            ControlPanelDisplay.bar(model,"LinkTrack",if(powered)d.energy/SolarPowerUnit.CAPACITY else 0.0)
        }
        PowerPanelDisplay.lamp(model,"StatusLed0",powered && d.pvWatts>1,.15f,.8f,.95f)
        PowerPanelDisplay.lamp(model,"StatusLed1",powered && if(hybrid)d.gridWatts>1 else d.feedWatts>1,.35f,.9f,.3f)
        PowerPanelDisplay.lamp(model,"StatusLed2",powered && d.enabled && d.output>1,.15f,.9f,.6f)
        PowerPanelDisplay.lamp(model,"StatusLed3",powered && d.status in setOf("PROTECTED","BACKFEED","GRID_VOLTAGE"),1f,.18f,.05f)
    }
}
