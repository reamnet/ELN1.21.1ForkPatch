package mods.eln.gridnode

import net.minecraft.world.phys.Vec3
import kotlin.math.max
import kotlin.math.sqrt

/** LV hangs beside the run, rather than on the run's axis through the pole. */
internal object ServiceCableMount {
    const val HEIGHT=4.97
    const val MIN_REACH=.95
    const val POLE_RADIUS=.27
    fun reach(radius: Double,obstacle: ServiceCableClearance,angle: Float): Double {
        val normal=Vec3(0.0,0.0,-1.0).yRot(Math.toRadians(angle.toDouble()).toFloat())
        val projected=normal.dot(obstacle.center)
        val required=obstacle.radius+6*radius+.24
        return max(MIN_REACH,projected+sqrt(max(0.0,required*required-obstacle.center.horizontalDistanceSqr()+projected*projected)))
    }
    fun center(reach: Double)=Vec3(0.0,HEIGHT,-reach)
    fun point(terminal: Int,wire: Int,reach: Double)=center(reach)
        .add(0.0,((if(terminal==50)wire else terminal-40)-1)*.32,0.0)
}
