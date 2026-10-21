package mods.eln.utility

import mods.eln.i18n.I18N.tr
import mods.eln.misc.Utils
import mods.eln.sim.ElectricalLoad

/** Read every independent circuit without requiring the player to change a GUI terminal first. */
object PoleMeter {
    fun readings(high: List<ElectricalLoad>,low: List<ElectricalLoad>,out: List<ElectricalLoad> = emptyList(),tapPhase: Int=0): List<String> = buildList {
        high.forEachIndexed { i,p -> add(tr("HV %1$: %2$",listOf("A","B","C")[i],Utils.plotUIP(p.voltage,p.current).trim())) }
        out.forEachIndexed { i,p -> add(tr("Load %1$: %2$",listOf("A","B","C")[if(out.size==1)tapPhase.coerceIn(0,2) else i],Utils.plotUIP(p.voltage,p.current).trim())) }
        low.forEachIndexed { i,p ->
            val label=when(i) { 0->tr("LV L1 (120 V)");1->tr("LV L2 (120 V)");else->tr("LV service (240 V)") }
            add(tr("%1$: %2$",label,Utils.plotUIP(p.voltage,p.current).trim()))
        }
    }
}
