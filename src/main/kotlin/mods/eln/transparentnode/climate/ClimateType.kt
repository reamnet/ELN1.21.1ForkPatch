package mods.eln.transparentnode.climate

import mods.eln.i18n.I18N
import net.minecraft.nbt.CompoundTag

/** Stable order: appended IDs in group 124, after the original kitchen machines. */
enum class ClimateType(val model: String,val title: String,val watts: Double,val cooling: Boolean=false) {
    PORTABLE("portablespaceheater",I18N.TR_NAME(I18N.Type.NONE,"Portable Electric Space Heater"),1500.0),
    MODERN("modernfireplacestand",I18N.TR_NAME(I18N.Type.NONE,"Modern Fireplace TV Stand"),1500.0),
    FARMHOUSE("farmhousefireplacestand",I18N.TR_NAME(I18N.Type.NONE,"Farmhouse Fireplace TV Stand"),1500.0),
    CORNER("cornerfireplacestand",I18N.TR_NAME(I18N.Type.NONE,"Corner Fireplace TV Stand"),1500.0),
    WINDOW("windowairconditioner",I18N.TR_NAME(I18N.Type.NONE,"Window Air Conditioner"),1000.0,true),
    MINI_SPLIT("wallminisplit",I18N.TR_NAME(I18N.Type.NONE,"Wall-mounted Mini-split Air Conditioner"),900.0,true),
    PATIO("deckpatioheater",I18N.TR_NAME(I18N.Type.NONE,"Electric Patio Heater"),1500.0);
    val subId get()=if(this==PATIO)62 else 21+ordinal
    val volts get()=120.0
    val sound get()=when(this) {
        WINDOW -> "appliance_window_ac"
        MINI_SPLIT -> "appliance_minisplit"
        else -> "appliance_heater"
    }
}

/** Hysteresis applies only at physical ticks; a brownout supplies no artificial heat or cooling. */
class ClimateControl(val cooling: Boolean) {
    var target=if(cooling)22.0 else 24.0
        private set
    var enabled=true
    var calling=false
        private set
    fun setTarget(value: Double): Boolean {
        if(!value.isFinite() || value !in 16.0..32.0)return false
        target=value;return true
    }
    fun update(temperature: Double): Boolean {
        if(!enabled || !temperature.isFinite())calling=false
        else if(cooling) {
            if(temperature>=target+.5)calling=true
            if(temperature<=target-.5)calling=false
        } else {
            if(temperature<=target-.5)calling=true
            if(temperature>=target+.5)calling=false
        }
        return calling
    }
    fun heat(watts: Double): Pair<Double,Double> {
        val power=watts.takeIf { it.isFinite() && it>0 } ?: 0.0
        return if(cooling)Pair(-3*power,4*power) else Pair(power,0.0)
    }
    fun read(nbt: CompoundTag) {
        if(nbt.contains("climateTarget"))setTarget(nbt.getDouble("climateTarget"))
        enabled=!nbt.contains("climateEnabled") || nbt.getBoolean("climateEnabled")
    }
    fun write(nbt: CompoundTag) { nbt.putDouble("climateTarget",target);nbt.putBoolean("climateEnabled",enabled) }
}
