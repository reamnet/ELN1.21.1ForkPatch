package mods.eln.utility

import mods.eln.i18n.I18N

enum class ServiceMastStyle(val title: String,val model: String,val headY: Double,val headX: Double=.36,val headZ: Double=0.0,val spacing: Double=.17,val underground: Boolean=false) {
    STANDARD(I18N.TR_NAME(I18N.Type.NONE,"120/240V Service Weatherhead"),"serviceweatherhead",.82),
    COMPACT_SIDE(I18N.TR_NAME(I18N.Type.NONE,"Compact Side Entry Service Mast"),"compactservicemast",.13,.12,.4,.09),
    THROUGH_ROOF(I18N.TR_NAME(I18N.Type.NONE,"Through Roof Service Mast"),"roofservicemast",2.17,.12,0.0,.09),
    GOOSENECK(I18N.TR_NAME(I18N.Type.NONE,"Gooseneck Service Mast"),"gooseneckservicemast",1.37,.12,.4,.09),
    UNDERGROUND(I18N.TR_NAME(I18N.Type.NONE,"Underground Service Entrance Riser"),"undergroundserviceentrance",.10,underground=true);
}
