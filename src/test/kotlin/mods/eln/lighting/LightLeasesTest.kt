package mods.eln.lighting

import mods.eln.lightblock.LightLeases
import org.junit.Test
import kotlin.random.Random
import kotlin.test.*

class LightLeasesTest {
    @Test fun pausedChunksCannotAccumulateOneEntryForEveryRefresh() {
        val leases = LightLeases()
        repeat(1_000_000) { leases.refresh(15, 1) }
        assertEquals(1, leases.activeLevels)
        assertEquals(15, leases.advance())
        assertNull(leases.advance())
        repeat(100_000) { leases.refresh(it % 16, 5) }
        assertEquals(16, leases.activeLevels)
        repeat(5) { assertEquals(15, leases.advance()) }
        assertNull(leases.advance())
    }

    @Test fun aDimmerLongLivedSourceSurvivesTheBrighterSourceExpiring() {
        val leases = LightLeases()
        leases.refresh(15, 1)
        leases.refresh(8, 3)
        assertEquals(listOf(15, 8, 8, null), List(4) { leases.advance() })
        leases.refresh(10, 3)
        assertEquals(10, leases.advance())
        leases.refresh(10, 1) // A shorter refresh must not shorten another source.
        assertEquals(listOf(10, 10, null), List(3) { leases.advance() })
    }

    @Test fun overlappingReportsMatchIndividualLifetimesAcrossPausesAndResumes() {
        val random = Random(71071)
        val leases = LightLeases()
        var reports = mutableListOf<Pair<Int, Int>>()
        repeat(5_000) {
            repeat(random.nextInt(9)) {
                val light = random.nextInt(-3, 20)
                val timeout = random.nextInt(-2, 15)
                leases.refresh(light, timeout)
                reports += light.coerceIn(0, 15) to timeout
            }
            if (random.nextInt(5) != 0) { // Updates can pause while refreshes continue.
                assertEquals(reports.maxOfOrNull { it.first }, leases.advance())
                reports = reports.mapNotNull { (light, timeout) ->
                    if (timeout > 1) light to timeout - 1 else null
                }.toMutableList()
            }
            assertTrue(leases.activeLevels <= 16)
        }
        repeat(16) {
            assertEquals(reports.maxOfOrNull { it.first }, leases.advance())
            reports = reports.mapNotNull { (light, timeout) ->
                if (timeout > 1) light to timeout - 1 else null
            }.toMutableList()
        }
        assertNull(leases.advance())
    }

    @Test fun clampingAndNonpositiveTimeoutsRetainTheirSingleTickContribution() {
        val leases = LightLeases()
        leases.refresh(-10, -1)
        assertEquals(0, leases.advance())
        assertNull(leases.advance())
        leases.refresh(100, 0)
        assertEquals(15, leases.advance())
        assertNull(leases.advance())
        leases.refresh(12, Int.MAX_VALUE)
        repeat(100) { assertEquals(12, leases.advance()) }
        assertEquals(1, leases.activeLevels)
    }
}
