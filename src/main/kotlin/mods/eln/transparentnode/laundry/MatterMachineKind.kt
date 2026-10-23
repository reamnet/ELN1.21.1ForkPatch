package mods.eln.transparentnode.laundry

import mods.eln.i18n.I18N

enum class MatterMachineKind(val title: String,val model: String,val volts: Double,val watts: Double,val seconds: Double,val subId: Int) {
    WASHER(I18N.TR_NAME(I18N.Type.NONE,"Electric Washing Machine"),"electricwasher",120.0,800.0,60.0,9),
    DRYER(I18N.TR_NAME(I18N.Type.NONE,"Electric Dryer"),"electricdryer",240.0,6000.0,120.0,10),
    FUSION(I18N.TR_NAME(I18N.Type.NONE,"Fusion Matter Reactor"),"fusionmatterreactor",3200.0,100_000.0,600.0,11)
}
