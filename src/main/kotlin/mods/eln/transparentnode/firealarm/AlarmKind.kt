package mods.eln.transparentnode.firealarm

import mods.eln.i18n.I18N

enum class AlarmKind(val title: String,val subId: Int,val idleWatts: Double,val alarmWatts: Double) {
    PANEL(I18N.TR_NAME(I18N.Type.NONE,"Fire Alarm Control Panel"),53,5.0,0.0),
    PULL(I18N.TR_NAME(I18N.Type.NONE,"Fire Alarm Pull Station"),54,.05,0.0),
    HORN(I18N.TR_NAME(I18N.Type.NONE,"Fire Alarm Horn Strobe"),55,.1,8.0),
    BELL(I18N.TR_NAME(I18N.Type.NONE,"Fire Alarm Bell"),56,.1,6.0),
    STROBE(I18N.TR_NAME(I18N.Type.NONE,"Fire Alarm Strobe"),57,.05,3.0),
    SMOKE(I18N.TR_NAME(I18N.Type.NONE,"Fire Alarm Smoke Detector"),58,.15,0.0),
    HEAT(I18N.TR_NAME(I18N.Type.NONE,"Fire Alarm Heat Detector"),59,.15,0.0);
    val source get()=this==PULL || this==SMOKE || this==HEAT
    val sensor get()=this==SMOKE || this==HEAT
    val audible get()=this==HORN || this==BELL
    val flashing get()=this==HORN || this==STROBE
    val model get()="firealarm_"+name.lowercase(java.util.Locale.ROOT)
}
