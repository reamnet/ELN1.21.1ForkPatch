package mods.eln.transparentnode.cord

import mods.eln.i18n.I18N
import kotlin.math.abs

/** Nominal circuit voltage; the simulation represents the live pair as one voltage. */
enum class CordRating(val volts: Double,val plug: String,val socket: String,val cordName: String,val outletName: String,val amps: Int=20) {
    V120(120.0,"NEMA 5-20P","NEMA 5-20R",I18N.TR_NAME(I18N.Type.NONE,"Flexible Appliance Cord"),I18N.TR_NAME(I18N.Type.NONE,"Experimental Wall Outlet")),
    V240(240.0,"NEMA 6-20P","NEMA 6-20R",I18N.TR_NAME(I18N.Type.NONE,"240 V Appliance Cord"),I18N.TR_NAME(I18N.Type.NONE,"240 V Wall Outlet")),
    V24(24.0,"Keyed 24 V plug","Keyed 24 V socket",I18N.TR_NAME(I18N.Type.NONE,"24 V Machine Cord"),I18N.TR_NAME(I18N.Type.NONE,"24 V Machine Outlet"),32),
    V48(48.0,"Keyed 48 V plug","Keyed 48 V socket",I18N.TR_NAME(I18N.Type.NONE,"48 V Machine Cord"),I18N.TR_NAME(I18N.Type.NONE,"48 V Machine Outlet"),32),
    V200(200.0,"Keyed 200 V plug","Keyed 200 V socket",I18N.TR_NAME(I18N.Type.NONE,"200 V Workshop Cord"),I18N.TR_NAME(I18N.Type.NONE,"200 V Workshop Outlet"),32),
    V480(480.0,"Industrial 480 V plug","Industrial 480 V socket",I18N.TR_NAME(I18N.Type.NONE,"480 V Industrial Cord"),I18N.TR_NAME(I18N.Type.NONE,"480 V Industrial Outlet"),125),
    V800(800.0,"Industrial 800 V plug","Industrial 800 V socket",I18N.TR_NAME(I18N.Type.NONE,"800 V Industrial Cord"),I18N.TR_NAME(I18N.Type.NONE,"800 V Industrial Outlet"),125),
    V3200(3200.0,"Industrial 3.2 kV coupler","Industrial 3.2 kV socket",I18N.TR_NAME(I18N.Type.NONE,"3.2 kV Industrial Cord"),I18N.TR_NAME(I18N.Type.NONE,"3.2 kV Industrial Outlet"),125);
    val industrial get()=this!=V120 && this!=V240
    val color get()=when(this) { V120->floatArrayOf(.22f,.52f,.80f);V240->floatArrayOf(.85f,.48f,.10f);V24->floatArrayOf(.52f,.32f,.68f);V48->floatArrayOf(.6f,.62f,.65f);V200->floatArrayOf(.25f,.65f,.40f);V480->floatArrayOf(.85f,.18f,.12f);V800->floatArrayOf(.12f,.15f,.18f);V3200->floatArrayOf(.95f,.73f,.12f) }
    fun accepts(nominal: Double)=nominal.isFinite() && abs(nominal-volts)<1.0
    companion object { fun read(name: String)=entries.firstOrNull { it.name==name } ?: V120 }
}
