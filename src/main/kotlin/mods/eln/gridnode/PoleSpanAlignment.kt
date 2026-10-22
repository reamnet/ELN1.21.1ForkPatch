package mods.eln.gridnode

import mods.eln.misc.Direction
import net.minecraft.world.phys.Vec3
import kotlin.math.*

/** Same crossarm criterion as the classic poles, applied separately to HV and LV spans. */
object PoleSpanAlignment {
    fun frontAngle(front: Direction): Float = when(front) {
        Direction.XN -> 0f; Direction.XP -> 180f; Direction.ZN -> 270f; else -> 90f
    }
    fun angle(spans: Iterable<Vec3>, fallback: Float): Float {
        val bearings=spans.filter { it.x.isFinite() && it.z.isFinite() && hypot(it.x,it.z)>1e-9 }
            .map { atan2(it.z,it.x).let { a -> if(a<0)a+PI else a } }.sorted()
        if(bearings.isEmpty())return fallback
        var best=0.0;var error=Double.POSITIVE_INFINITY
        repeat(128) { i ->
            val candidate=PI*i/128
            val score=bearings.sumOf { abs(sin(candidate-it)).let { v -> v*v*v } }
            if(score<error) { error=score;best=candidate }
        }
        return if(best==0.0)0f else Math.toDegrees(-best).toFloat()
    }
    fun secondary(terminal: Int)=terminal in 40..42 || terminal==50

    /** A crossarm has two equivalent angles, but its A/B/C ordering does not. */
    fun oriented(angle: Float,reference: Float,span: Vec3): Float {
        // Compare phase order across this particular span, rather than just the
        // angles between arms: junctions can aim their arms towards other branches.
        fun side(degrees: Float): Double {
            val radians=Math.toRadians(degrees.toDouble())
            return span.x*cos(radians)-span.z*sin(radians)
        }
        return if(side(angle)*side(reference)<-1e-12)angle+180f else angle
    }
}
