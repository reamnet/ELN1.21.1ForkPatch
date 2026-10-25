package mods.eln.transparentnode.standby

import mods.eln.i18n.I18N

/** Explicit appended descriptor IDs; the original small generator remains 124:7. */
enum class GeneratorTier(val title: String,val width: Int,val height: Int,val depth: Int,val factor: Int,val subId: Int) {
    SMALL(I18N.TR_NAME(I18N.Type.NONE,"Standby Gasoline Generator"),1,1,1,1,7),
    MEDIUM(I18N.TR_NAME(I18N.Type.NONE,"Medium Standby Gasoline Generator"),2,1,1,2,24),
    LARGE(I18N.TR_NAME(I18N.Type.NONE,"Large Standby Gasoline Generator"),2,2,1,4,25),
    EXTRA_LARGE(I18N.TR_NAME(I18N.Type.NONE,"Extra Large Standby Gasoline Generator"),3,2,2,8,26),
    EXTREME(I18N.TR_NAME(I18N.Type.NONE,"Extreme Standby Gasoline Generator"),5,3,3,24,27);
    val watts get()=10_000.0*factor
    val amps get()=200.0*factor
    val tankMb get()=20_000*factor
    val model get()=if(this==SMALL)"standbygenerator" else "standbygenerator_"+name.lowercase(java.util.Locale.ROOT)
}
