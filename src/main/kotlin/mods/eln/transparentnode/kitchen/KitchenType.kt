package mods.eln.transparentnode.kitchen

import mods.eln.i18n.I18N

enum class KitchenType(val model: String,val title: String,val volts: Double,val watts: Double,val seconds: Double) {
    MICROWAVE("kitchenmicrowave",I18N.TR_NAME(I18N.Type.NONE,"Electric Microwave"),120.0,1000.0,6.0),
    TOASTER("kitchentoaster",I18N.TR_NAME(I18N.Type.NONE,"Electric Toaster"),120.0,800.0,5.0),
    STOVE("kitchenflatstove",I18N.TR_NAME(I18N.Type.NONE,"Flat Electric Cooktop"),240.0,2000.0,10.0),
    FRIDGE("kitchenfridge",I18N.TR_NAME(I18N.Type.NONE,"Electric Refrigerator"),120.0,80.0,60.0),
    COFFEE("kitchencoffeemaker",I18N.TR_NAME(I18N.Type.NONE,"Electric Coffee Maker"),120.0,900.0,10.0),
    KETTLE("kitchenkettle",I18N.TR_NAME(I18N.Type.NONE,"Electric Kettle"),120.0,1500.0,8.0),
    BLENDER("kitchenblender",I18N.TR_NAME(I18N.Type.NONE,"Countertop Blender"),120.0,400.0,5.0),
    RICE("kitchenricecooker",I18N.TR_NAME(I18N.Type.NONE,"Electric Rice Cooker"),120.0,700.0,12.0)
}

/** Processing is paid for by electrical joules, so an outage cannot finish a job. */
class KitchenWork {
    var joules=0.0
    fun advance(seconds: Double,watts: Double,required: Double): Boolean {
        if(!seconds.isFinite() || seconds<=0 || !watts.isFinite() || watts<=0 || !required.isFinite() || required<=0)return false
        joules=(joules+seconds*watts).coerceAtMost(required)
        return joules>=required
    }
}
