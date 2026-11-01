package mods.eln.transparentnode.industrial

import kotlin.math.*

/** State machines advance once per physical step, never during nonlinear solver retries. */
class Protection {
    var tripped=false
    var armed=false
    var elapsed=0.0
    var reason=0
    var pendingReason=0
    fun reset() { tripped=false;armed=false;elapsed=0.0;reason=0;pendingReason=0 }
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
        pendingReason=fault
        elapsed=if(fault==0)0.0 else elapsed+dt
        if(fault!=0 && elapsed+1e-9>=delay) { tripped=true;reason=fault }
        return tripped
    }
    /** A primary bayonet link and a transformer capacity guard are distinct protections.
     * Changing the fault type restarts its delay; a long core overload cannot preheat
     * an otherwise healthy fuse timer and then blow it on a brief current spike.
     */
    fun stepPad(dt: Double,volts: Double,amps: Double,nominal: Double,fuseAmps: Double,ratedWatts: Double): Boolean {
        if(dt<=0 || !dt.isFinite() || tripped)return tripped
        val fault=when {
            !volts.isFinite() || !amps.isFinite()->4
            abs(amps)>fuseAmps->2
            abs(volts)>nominal*1.5->1
            abs(volts*amps)>ratedWatts*1.25->7
            else->0
        }
        if(fault!=pendingReason)elapsed=0.0
        pendingReason=fault;elapsed=if(fault==0)0.0 else elapsed+dt
        if(fault!=0 && elapsed+1e-9>=2.0) { tripped=true;reason=fault }
        return tripped
    }

}
/** P1 is retained longest; P4 is shed first. Timers use physical time only.
 * Remember each disconnected branch's demand at nominal voltage so restoration does not
 * repeatedly reconnect a known oversized load into the same overload.
 */
class ShedControl {
    val closed=BooleanArray(4) { true }
    val nominalDemand=DoubleArray(4)
    var status="NORMAL";private set
    var remaining=0.0;private set
    var next=-1;private set
    private var faultTime=0.0
    private var healthyTime=0.0
    fun retry() { closed.indices.filter { !closed[it] }.forEach { nominalDemand[it]=0.0 };faultTime=0.0;healthyTime=0.0 }
    fun step(dt: Double,volts: Double,nominal: Double,watts: Double,budget: Double,enabled: Boolean,
        branchWatts: DoubleArray=DoubleArray(4),shedDelay: Double=1.0,restoreDelay: Double=5.0,low: Double=.85,restore: Double=.95) {
        if(!dt.isFinite() || dt<=0)return
        remaining=0.0;next=-1
        if(!enabled) { closed.fill(false);faultTime=0.0;healthyTime=0.0;status="DISABLED";return }
        if(!volts.isFinite() || !watts.isFinite()) {
            closed.fill(false);faultTime=0.0;healthyTime=0.0;status="INVALID";return
        }
        val v=abs(volts)
        if(v<nominal*.1) { faultTime=0.0;healthyTime=0.0;status="WAIT_POWER";return }
        for(i in closed.indices)if(closed[i]) {
            val power=branchWatts.getOrElse(i) { 0.0 }
            if(power.isFinite())nominalDemand[i]=max(0.0,power)*(nominal/v).pow(2)
        }
        val bad=v<nominal*low || watts>budget
        if(bad) {
            healthyTime=0.0;faultTime+=dt;next=closed.indexOfLast { it }
            status=if(v<nominal*low)"UNDER_VOLTAGE" else "OVER_BUDGET"
            remaining=if(next>=0)max(0.0,shedDelay-faultTime) else 0.0
            if(faultTime+1e-9>=shedDelay && next>=0) { closed[next]=false;faultTime=0.0 }
            return
        }
        faultTime=0.0;next=closed.indexOfFirst { !it }
        if(next<0) { healthyTime=0.0;status="NORMAL";return }
        // Preserve 20% headroom before retrying; projected branch demand must also fit
        // below 90% of budget. Do not skip P1 to restore a lower priority branch.
        val projected=nominalDemand[next]*(v/nominal).pow(2)
        if(v<nominal*restore || watts>=budget*.8) { healthyTime=0.0;status="HOLD";return }
        if(watts+projected>budget*.9) { healthyTime=0.0;status="HEADROOM";return }
        healthyTime+=dt;status="RESTORING";remaining=max(0.0,restoreDelay-healthyTime)
        if(healthyTime+1e-9>=restoreDelay) { closed[next]=true;healthyTime=0.0 }
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
