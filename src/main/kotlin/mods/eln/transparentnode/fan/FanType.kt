package mods.eln.transparentnode.fan

import mods.eln.i18n.I18N

enum class FanType(val model: String,val title: String,val volts: Double,val watts: Double,val start: Double,val coast: Double,val rpm: Float) {
    INDUSTRIAL("industrialfloorfan",I18N.TR_NAME(I18N.Type.NONE,"Industrial Five Block Fan"),240.0,1500.0,12.0,20.0,240f),
    CLASSIC("classicboxfan",I18N.TR_NAME(I18N.Type.NONE,"Classic Box Fan"),120.0,45.0,4.0,8.0,700f),
    SHOP("metalboxfan",I18N.TR_NAME(I18N.Type.NONE,"Metal Shop Box Fan"),120.0,85.0,5.0,10.0,900f),
    COMPACT("compactboxfan",I18N.TR_NAME(I18N.Type.NONE,"Compact Desktop Box Fan"),120.0,20.0,3.0,6.0,600f);
    val industrial get()=this==INDUSTRIAL
    val radius get()=if(industrial)2.28 else if(this==COMPACT).22 else .39
    val centerY get()=if(industrial)1.9 else if(this==COMPACT)-.18 else -.02
}
