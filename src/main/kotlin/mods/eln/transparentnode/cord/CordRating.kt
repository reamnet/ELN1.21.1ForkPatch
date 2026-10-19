package mods.eln.transparentnode.cord

import mods.eln.i18n.I18N
import kotlin.math.abs

/** Nominal circuit voltage; the simulation represents the live pair as one voltage. */
enum class CordRating(val volts: Double,val plug: String,val socket: String,val cordName: String,val outletName: String) {
    V120(120.0,"NEMA 5-20P","NEMA 5-20R",I18N.TR_NAME(I18N.Type.NONE,"Flexible Appliance Cord"),I18N.TR_NAME(I18N.Type.NONE,"Experimental Wall Outlet")),
    V240(240.0,"NEMA 6-20P","NEMA 6-20R",I18N.TR_NAME(I18N.Type.NONE,"240 V Appliance Cord"),I18N.TR_NAME(I18N.Type.NONE,"240 V Wall Outlet"));
    fun accepts(nominal: Double)=nominal.isFinite() && abs(nominal-volts)<1.0
    companion object { fun read(name: String)=entries.firstOrNull { it.name==name } ?: V120 }
}
