package mods.eln.gridnode

import kotlin.math.abs

/** Ambient changes slowly; each conductor still integrates its paid heat on every thermal step. */
internal class SpanAmbientSample {
    private var lastTick=Long.MIN_VALUE
    private var value=Double.NaN
    fun sample(tick: Long,read: ()->Double): Double {
        if(!value.isFinite() || tick<lastTick || tick-lastTick>=20) { value=read();lastTick=tick }
        return value
    }
}

/** Bounded electrical/thermal coupling: <=0.01% resistance lag, with immediate large/hot updates. */
internal class SpanResistanceRefresh {
    private var stamped=Double.NaN
    fun update(ohms: Double,write: (Double)->Unit): Boolean {
        require(ohms.isFinite() && ohms>0)
        if(stamped.isFinite() && abs(ohms-stamped)<=MAX_RELATIVE_CHANGE*stamped)return false
        stamped=ohms;write(ohms);return true
    }
    companion object { const val MAX_RELATIVE_CHANGE=1e-4 }
}
