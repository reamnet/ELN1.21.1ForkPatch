package mods.eln.transparentnode.switchgear

import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr

enum class SwitchgearKind(val title: String,val poles: Int,val changeover: Boolean,val solidState: Boolean,val amps: Double,val coilWatts: Double,val subId: Int) {
    CONTACTOR(I18N.TR_NAME(I18N.Type.NONE,"Single Pole Contactor"),1,false,false,100.0,8.0,37),
    CONTACTOR_THREE(I18N.TR_NAME(I18N.Type.NONE,"Three Pole Contactor"),3,false,false,100.0,12.0,38),
    RELAY_TWO(I18N.TR_NAME(I18N.Type.NONE,"Two Pole Changeover Relay"),2,true,false,20.0,2.0,39),
    RELAY_FOUR(I18N.TR_NAME(I18N.Type.NONE,"Four Pole Changeover Relay"),4,true,false,20.0,3.0,40),
    SSR(I18N.TR_NAME(I18N.Type.NONE,"Solid State Relay"),1,false,true,40.0,.5,41),
    SSR_THREE(I18N.TR_NAME(I18N.Type.NONE,"Three Pole Solid State Relay"),3,false,true,40.0,1.5,42);
    val resistance get()=if(solidState).02 else if(changeover).01 else .002
    val volts get()=if(changeover || solidState)600.0 else 1000.0
    val model get()="switchgear_"+name.lowercase(java.util.Locale.ROOT)
}
enum class RelayControl(val nominal: Double) {
    SIGNAL(0.0),COIL_120(120.0),COIL_240(240.0);
    fun label()=when(this) { SIGNAL->tr("ELN signal");COIL_120->tr("120 V coil");COIL_240->tr("240 V coil") }
}
