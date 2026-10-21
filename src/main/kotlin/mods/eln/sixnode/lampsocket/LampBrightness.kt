package mods.eln.sixnode.lampsocket

import mods.eln.item.lampitem.BoilerplateLampData
import kotlin.math.abs
import kotlin.math.roundToInt

/** Quantize the voltage response once; a tiny real wire loss must not lose a whole light level. */
object LampBrightness {
    fun level(voltage: Double, nominal: Double, minimumFactor: Double, nominalLight: Int): Int {
        if(!voltage.isFinite() || nominal<=0 || minimumFactor>=1)return BoilerplateLampData.MIN_LIGHT_VALUE
        val fraction=(abs(voltage)/nominal-minimumFactor)/(1-minimumFactor)
        return (fraction*nominalLight).coerceIn(BoilerplateLampData.MIN_LIGHT_VALUE.toDouble(),BoilerplateLampData.MAX_LIGHT_VALUE.toDouble()).roundToInt()
            .coerceIn(BoilerplateLampData.MIN_LIGHT_VALUE,BoilerplateLampData.MAX_LIGHT_VALUE)
    }
}
