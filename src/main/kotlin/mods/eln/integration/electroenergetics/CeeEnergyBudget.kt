package mods.eln.integration.electroenergetics

import kotlin.math.floor

/** Joules are the stored unit. FE is converted only at the interoperability boundary. */
class CeeEnergyBudget {
    companion object { const val CAPACITY=200_000.0;const val EFFICIENCY=.95 }
    var joules=0.0
        private set
    var inputJoules=0.0;private set
    var exportedJoules=0.0;private set
    var lossJoules=0.0;private set
    private var tick=Long.MIN_VALUE
    private var quota=0
    private var used=0
    private var fraction=0.0
    fun charge(watts: Double,seconds: Double) {
        if(!watts.isFinite() || watts<0 || !seconds.isFinite() || seconds<=0)return
        val input=watts*seconds;if(!input.isFinite())return
        val stored=minOf(CAPACITY-joules,input*EFFICIENCY)
        inputJoules+=input;lossJoules+=input-stored;joules+=stored
    }
    fun restore(value: Double) { joules=value.takeIf { it.isFinite() }?.coerceIn(0.0,CAPACITY) ?: 0.0;tick=Long.MIN_VALUE;quota=0;used=0;fraction=0.0 }
    fun available(joulesPerFe: Double)=if(!joulesPerFe.isFinite() || joulesPerFe<=0)0 else floor(joules/joulesPerFe).coerceIn(0.0,Int.MAX_VALUE.toDouble()).toInt()
    fun extract(maximum: Int,simulate: Boolean,now: Long,joulesPerFe: Double,wattsLimit: Double): Int {
        if(maximum<=0 || !joulesPerFe.isFinite() || joulesPerFe<=0 || !wattsLimit.isFinite() || wattsLimit<=0)return 0
        val tokens=fraction+wattsLimit/(20*joulesPerFe)
        val whole=floor(tokens)
        val allowance=if(now==tick)(quota-used).coerceAtLeast(0) else whole.coerceIn(0.0,Int.MAX_VALUE.toDouble()).toInt()
        val count=minOf(maximum,available(joulesPerFe),allowance)
        if(!simulate) {
            if(now!=tick) { tick=now;quota=allowance;used=0;fraction=(tokens-whole).coerceIn(0.0,.9999999999999999) }
            used+=count;val energy=count*joulesPerFe;joules=(joules-energy).coerceAtLeast(0.0);exportedJoules+=energy
        }
        return count
    }
}
