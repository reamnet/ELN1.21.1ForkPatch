package mods.eln.transparentnode.fan

import net.minecraft.nbt.CompoundTag
import kotlin.math.*

/** Physical rotor state; runs once per real simulation step, independently of solver retries. */
class FanDynamics(val startup: Double=5.0,val coast: Double=10.0) {
    var speed=0.0
        private set
    fun step(seconds: Double,target: Double): Double {
        if(!seconds.isFinite() || seconds<=0)return speed
        val goal=target.takeIf { it.isFinite() }?.coerceIn(0.0,1.0) ?: 0.0
        speed=if(goal>speed)min(goal,speed+seconds/startup) else max(goal,speed-seconds/coast)
        if(speed<1e-9)speed=0.0
        return speed
    }
    fun read(nbt: CompoundTag) { speed=nbt.getDouble("fanRotorSpeed").takeIf { it.isFinite() }?.coerceIn(0.0,1.0) ?: 0.0 }
    fun write(nbt: CompoundTag) { nbt.putDouble("fanRotorSpeed",speed) }
}

/** Network samples are followed smoothly; coasting continues even with the switch off. */
class FanVisual {
    var speed=0f
        private set
    var angle=0f
        private set
    fun step(seconds: Float,target: Double,rpm: Float=90f) {
        if(!seconds.isFinite() || seconds<=0)return
        val dt=seconds.coerceAtMost(.25f)
        val goal=(target.takeIf { it.isFinite() } ?: 0.0).coerceIn(0.0,1.0).toFloat()
        val before=speed
        speed+= (goal-speed).coerceIn(-2f*dt,2f*dt)
        angle=(angle+dt*(before+speed)*.5f*rpm*6f)%360f
    }
}
