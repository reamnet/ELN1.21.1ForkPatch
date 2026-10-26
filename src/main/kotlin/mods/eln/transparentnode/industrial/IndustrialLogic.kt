package mods.eln.transparentnode.industrial

import kotlin.math.*

/** State machines advance once per physical step, never during nonlinear solver retries. */
class Protection {
    var tripped=false
    var armed=false
    var elapsed=0.0
    var reason=0
    fun reset() { tripped=false;armed=false;elapsed=0.0;reason=0 }
    fun step(dt: Double,volts: Double,amps: Double,returnAmps: Double,nominal: Double,limit: Double,low: Double,high: Double,delay: Double,differential: Double=0.0): Boolean {
        if(dt<=0 || !dt.isFinite() || tripped)return tripped
        if(abs(volts)>=nominal*.9)armed=true
        val fault=when {
            !volts.isFinite() || !amps.isFinite() || !returnAmps.isFinite()->4
            differential>0 && abs(amps+returnAmps)>differential->3
            abs(amps)>limit->2
            abs(volts)>nominal*high || armed && abs(volts)<nominal*low->1
            else->0
        }
        elapsed=if(fault==0)0.0 else elapsed+dt
        if(fault!=0 && elapsed+1e-9>=delay) { tripped=true;reason=fault }
        return tripped
    }
}
class ShedControl {
    val closed=BooleanArray(4) { true }
    private var faultTime=0.0
    private var healthyTime=0.0
    fun step(dt: Double,volts: Double,nominal: Double,watts: Double,budget: Double,enabled: Boolean) {
        if(!dt.isFinite() || dt<=0)return
        if(!enabled) { closed.fill(false);faultTime=0.0;healthyTime=0.0;return }
        val bad=!volts.isFinite() || abs(volts)<nominal*.85 || watts>budget
        if(bad) {
            healthyTime=0.0;faultTime+=dt
            if(faultTime>=1) { closed.indexOfLast { it }.takeIf { it>=0 }?.let { closed[it]=false };faultTime=0.0 }
        } else {
            faultTime=0.0
            if(abs(volts)>=nominal*.95 && watts<budget*.8)healthyTime+=dt else healthyTime=0.0
            if(healthyTime>=5) { closed.indexOfFirst { !it }.takeIf { it>=0 }?.let { closed[it]=true };healthyTime=0.0 }
        }
    }
}
object IndustrialEnergy {
    fun resistance(volts: Double,watts: Double)=if(volts.isFinite() && volts>0 && watts.isFinite() && watts>0)max(.001,volts*volts/watts) else 1e19
    fun schedule(tick: Long,start: Int,end: Int): Boolean {
        if(start==end)return true
        val hour=(Math.floorMod(tick,24000L)/1000+6)%24
        return if(start<end)hour>=start && hour<end else hour>=start || hour<end
    }
    fun ramp(current: Double,target: Double,dt: Double,seconds: Double): Double = current+(target-current).coerceIn(-60*dt/max(.1,seconds),60*dt/max(.1,seconds))
    fun charge(energy: Double,capacity: Double,inputJoules: Double)=min(max(0.0,capacity-energy),max(0.0,inputJoules)*.94)
}
