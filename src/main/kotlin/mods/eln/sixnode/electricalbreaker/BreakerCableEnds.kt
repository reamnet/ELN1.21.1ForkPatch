package mods.eln.sixnode.electricalbreaker

import mods.eln.misc.LRDU
import mods.eln.misc.Obj3D
import net.minecraft.world.phys.Vec3

/** Clip each cable at the rotated casing, so thick insulation never paints over the case. */
object BreakerCableEnds {
    @JvmStatic fun starts(part: Obj3D.Obj3DPart?,front: LRDU): FloatArray {
        if(part==null)return FloatArray(4) { .15f }
        val points=listOf(part.yMin to part.zMin,part.yMin to part.zMax,part.yMax to part.zMin,part.yMax to part.zMax)
            .map { (y,z)->front.rotateOnXnLeft(Vec3(0.0,y.toDouble(),z.toDouble())) }
        return floatArrayOf((-points.minOf { it.z }).toFloat(),points.maxOf { it.z }.toFloat(),
            (-points.minOf { it.y }).toFloat(),points.maxOf { it.y }.toFloat()).map { (it+.002f).coerceIn(0f,.49f) }.toFloatArray()
    }
}
