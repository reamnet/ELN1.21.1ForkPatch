package mods.eln.gridnode

import net.minecraft.world.phys.Vec3
import org.junit.Test
import kotlin.test.*

class OverheadCableMeshTest {
    private val spans = listOf(Vec3(32.0, 0.0, 0.0), Vec3(0.0, 4.0, 24.0),
        Vec3(-16.0, -6.0, 31.0), Vec3(0.0, 12.0, 0.0))

    @Test fun cableEndsMatchBothTerminalsAndEverySectionHasFourDistinctCorners() {
        val start = Vec3(3.0, 7.0, -2.0)
        for (span in spans) {
            val end = start.add(span)
            val points = OverheadCableMesh.points(start, end)
            assertEquals(start, points.first()); assertEquals(end, points.last())
            val sections = OverheadCableMesh.sections(start, end)
            for ((index, section) in sections.withIndex()) {
                assertEquals(4, section.toSet().size)
                val center = section.reduce(Vec3::add).scale(.25)
                assertTrue(center.distanceTo(points[index]) < 1e-10)
                assertTrue(section[1].subtract(section[0]).cross(section[2].subtract(section[0])).length() > .009)
            }
            // Both faces of every side have area; no edge-on triangle ribbons.
            for (i in 0 until sections.lastIndex) for (side in 0..3) {
                val a = sections[i][side]; val b = sections[i][(side + 1) % 4]
                val c = sections[i + 1][(side + 1) % 4]; val d = sections[i + 1][side]
                assertTrue(b.subtract(a).cross(c.subtract(a)).length() > 1e-8)
                assertTrue(c.subtract(a).cross(d.subtract(a)).length() > 1e-8)
            }
        }
    }

    @Test fun reversingASpanPreservesItsPathAndSagNeverRisesAboveTheStraightChord() {
        for (end in spans) {
            val forward = OverheadCableMesh.points(Vec3.ZERO, end)
            val reverse = OverheadCableMesh.points(end, Vec3.ZERO).reversed()
            forward.indices.forEach { i ->
                assertTrue(forward[i].distanceTo(reverse[i]) < 1e-8)
                assertTrue(forward[i].y <= end.y * i / forward.lastIndex + 1e-8)
            }
        }
    }
}
