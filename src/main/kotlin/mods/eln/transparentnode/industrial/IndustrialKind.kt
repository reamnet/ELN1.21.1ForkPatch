package mods.eln.transparentnode.industrial

import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr

enum class IndustrialKind(val title: String,val nominal: Double,val defaults: DoubleArray) {
    LOAD_BANK(I18N.TR_NAME(I18N.Type.NONE,"Programmable Load Bank"),480.0,doubleArrayOf(480.0,50000.0)),
    SCADA(I18N.TR_NAME(I18N.Type.NONE,"Grid Control Console"),120.0,doubleArrayOf(0.0,64.0,0.0,1.0)),
    RELAY(I18N.TR_NAME(I18N.Type.NONE,"Protective Relay Cabinet"),480.0,doubleArrayOf(480.0,100.0,.8,1.2,.5,0.0)),
    ARRESTER(I18N.TR_NAME(I18N.Type.NONE,"Pole Surge Arrester"),12470.0,doubleArrayOf(12470.0)),
    PAD(I18N.TR_NAME(I18N.Type.NONE,"Pad Mounted Neighbourhood Transformer"),12470.0,doubleArrayOf(12470.0,240.0)),
    DRIVE(I18N.TR_NAME(I18N.Type.NONE,"Variable Frequency Motor Drive"),480.0,doubleArrayOf(480.0,480.0,60.0,5.0,100.0)),
    SHEDDER(I18N.TR_NAME(I18N.Type.NONE,"Automatic Load Shedding Controller"),240.0,doubleArrayOf(240.0,50000.0)),
    CHARGER(I18N.TR_NAME(I18N.Type.NONE,"Programmable EV Pack Charger"),240.0,doubleArrayOf(240.0,7200.0,0.0,0.0)),
    WELDER(I18N.TR_NAME(I18N.Type.NONE,"Electric Welding Station"),240.0,doubleArrayOf(240.0,6000.0));
    val model get()="industrial_"+name.lowercase(java.util.Locale.ROOT)
    val slots get()=when(this) { PAD->2;CHARGER->1;WELDER->3;else->0 }
    fun labels(): List<String> = when(this) {
        LOAD_BANK->listOf(tr("Nominal input V"),tr("Load watts"))
        SCADA->listOf(tr("Target X"),tr("Target Y"),tr("Target Z"),tr("Circuit (1-12)"))
        RELAY->listOf(tr("Nominal V"),tr("Overcurrent A"),tr("Undervoltage ratio"),tr("Overvoltage ratio"),tr("Trip delay seconds"),tr("Diff A (0 = off)"))
        ARRESTER->listOf(tr("Nominal line V"))
        PAD->listOf(tr("Primary V"),tr("Secondary V"))
        DRIVE->listOf(tr("Nominal input V"),tr("Nominal output V"),tr("Frequency Hz"),tr("Ramp seconds"),tr("Current limit A"))
        SHEDDER->listOf(tr("Nominal input V"),tr("Power budget W"))
        CHARGER->listOf(tr("Nominal input V"),tr("Charge power W"),tr("Start hour (0-23)"),tr("End hour (0-23)"))
        WELDER->listOf(tr("Nominal input V"),tr("Welding power W"))
    }
    fun valid(p: DoubleArray): Boolean {
        if(p.size!=defaults.size || p.any { !it.isFinite() })return false
        if(this==SCADA)return p[0] in -29999984.0..29999984.0 && p[1] in -2048.0..2048.0 && p[2] in -29999984.0..29999984.0 && p[3] in 1.0..12.0
        if(p[0] !in 12.0..230000.0)return false
        return when(this) {
            LOAD_BANK->p[1] in 0.0..1e6
            RELAY->p[1] in .01..10000.0 && p[2] in 0.0..1.0 && p[3] in 1.01..2.0 && p[4] in .05..60.0 && p[5] in 0.0..10000.0
            PAD->p[1] in 120.0..480.0
            DRIVE->p[1] in 12.0..3200.0 && p[2] in 0.0..60.0 && p[3] in .1..120.0 && p[4] in .1..1000.0
            SHEDDER->p[1] in 100.0..1e6
            CHARGER->p[0] in 120.0..480.0 && p[1] in 100.0..50000.0 && p[2] in 0.0..23.0 && p[3] in 0.0..23.0
            WELDER->p[0] in 120.0..480.0 && p[1] in 1000.0..20000.0
            else->true
        }
    }
}
