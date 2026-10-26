package mods.eln.gridnode

import net.minecraft.world.phys.Vec3
import kotlin.math.*

/** Closed cable sections shared by adjacent segments, including the exact terminals. */
internal object OverheadCableMesh {
    fun points(start: Vec3, end: Vec3, segments: Int = 16): List<Vec3> {
        require(segments > 0)
        val delta = end.subtract(start)
        val horizontal = hypot(delta.x, delta.z)
        if (horizontal < 1e-6) return (0..segments).map { start.add(delta.scale(it.toDouble() / segments)) }
        val length = delta.length() * 1.005
        val target = sqrt(length * length - delta.y * delta.y) / horizontal
        var lo = 1e-8
        var hi = 20.0
        repeat(48) {
            val mid = (lo + hi) * .5
            if (sinh(mid) / mid < target) lo = mid else hi = mid
        }
        val l = (lo + hi) * .5
        val a = horizontal / (2 * l)
        val p = (horizontal - a * ln((length + delta.y) / (length - delta.y))) * .5
        val q = (delta.y - length * cosh(l) / sinh(l)) * .5
        return (0..segments).map { i ->
            when (i) {
                0 -> start
                segments -> end
                else -> {
                    val fraction = i.toDouble() / segments
                    Vec3(start.x + delta.x * fraction,
                        start.y + a * cosh((horizontal * fraction - p) / a) + q,
                        start.z + delta.z * fraction)
                }
            }
        }
    }

    fun sections(start: Vec3, end: Vec3, radius: Double = .05): List<List<Vec3>> {
        return sections(points(start,end),radius)
    }

    fun sections(points: List<Vec3>,radius: Double): List<List<Vec3>> {
        require(points.size>=2 && radius.isFinite() && radius>0)
        return points.indices.map { i ->
            val tangent = points[min(i + 1, points.lastIndex)].subtract(points[max(i - 1, 0)]).normalize()
            val horizontal = Vec3(-tangent.z, 0.0, tangent.x)
            val right = if (horizontal.lengthSqr() < 1e-12) Vec3(1.0, 0.0, 0.0) else horizontal.normalize()
            val up = tangent.cross(right).normalize()
            listOf(1.0 to 1.0, -1.0 to 1.0, -1.0 to -1.0, 1.0 to -1.0).map { (x, y) ->
                points[i].add(right.scale(x * radius)).add(up.scale(y * radius))
            }
        }
    }
}
