package mods.eln.client.terrain

import org.junit.Test
import kotlin.test.*

class DistantRetentionTest {
    @Test fun powerlinesSurviveDecorativeCachePressureWithinTheSameHardBudget() {
        val grid=setOf("pole","span");val entries=linkedMapOf("pole" to 512,"span" to 2304)
        repeat(100) { n->
            entries["fixture$n"]=200
            while(entries.values.sum()>4000)entries.remove(DistantRetention.victim(entries.keys,grid))
        }
        assertTrue(grid.all { it in entries });assertTrue(entries.values.sum()<=4000)
        // A network by itself still obeys the budget; protection is not unlimited memory.
        assertEquals("pole",DistantRetention.victim(linkedSetOf("pole","span"),grid))
    }
    @Test fun rendererSlotsGoToPolesAndWiresEvenBehindHundredsOfCloserAppliances() {
        val closer=(0..299).map { "fixture$it" }
        val grid=listOf("pole","span")
        val selected=DistantRetention.renderOrder(closer+grid,grid.toSet()).take(256)
        assertEquals(grid,selected.take(2));assertEquals(closer.take(254),selected.drop(2))
    }
}
