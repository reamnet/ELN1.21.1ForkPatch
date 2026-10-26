package mods.eln.utility

import mods.eln.gridnode.ServiceCableClearance
import net.minecraft.world.phys.Vec3

/** Common can geometry for items, installed models, bushings and cable clearance. */
internal data class PoleTransformerShape(val diameter: Double,val height: Double) {
    companion object {
        const val BACK=-.24
        const val TOP=5.535
        fun of(watts: Int)=when {
            watts<=10_000 -> PoleTransformerShape(.72,.72)
            watts<=25_000 -> PoleTransformerShape(.90,.88)
            watts<=50_000 -> PoleTransformerShape(1.10,1.10)
            watts<=75_000 -> PoleTransformerShape(1.28,1.25)
            else -> PoleTransformerShape(1.42,1.40)
        }
    }
    val bushing get()=Vec3(BACK-.315*diameter,5.715,0.0)
    fun point(original: Vec3)=Vec3(BACK+(original.x+.015)*diameter,TOP+(original.y-TOP)*height,original.z*diameter)
    /** Enclose both the wooden pole and the cylindrical can, rather than routing through the tank. */
    fun clearance(angle: Float)=ServiceCableClearance(Vec3(-(.315*diameter-.015),0.0,0.0)
        .yRot(Math.toRadians(angle.toDouble()).toFloat()),.255+.315*diameter)
    fun secondaryLead(index: Int,angle: Float,target: Vec3): List<Vec3> {
        val rotation=Math.toRadians(angle.toDouble()).toFloat()
        val from=point(Vec3(-.60,5.15+index*.05,.20)).yRot(rotation)
        val tank=Vec3(bushing.x,0.0,0.0).yRot(rotation)
        val direction=Vec3(from.x-tank.x,0.0,from.z-tank.z).normalize()
        val obstacle=clearance(angle)
        val relative=Vec3(from.x-obstacle.center.x,0.0,from.z-obstacle.center.z)
        val projection=relative.dot(direction);val required=obstacle.radius+.06
        val distance=(-projection+kotlin.math.sqrt(kotlin.math.max(0.0,projection*projection+required*required-relative.lengthSqr()))).coerceAtLeast(0.0)+.03
        val escape=from.add(direction.scale(distance))
        return listOf(from)+mods.eln.gridnode.ServiceCablePath.route(listOf(escape,target),listOf(obstacle),.06)
    }
}
