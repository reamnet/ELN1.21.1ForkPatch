package mods.eln.sixnode.lampsupply

import kotlin.test.*

class LampSupplyRangeTest {
    @Test fun cyclesThroughBoundedRangesAndWraps() {
        var range = 32
        for (next in listOf(64, 128, 256, 512, 32)) {
            range = LampSupplyRange.next(range)
            assertEquals(next, range)
            assertTrue(LampSupplyRange.isSelectable(range))
        }
        for (invalid in listOf(Int.MIN_VALUE, -1, 0, 33, 513, Int.MAX_VALUE))
            assertFalse(LampSupplyRange.isSelectable(invalid))
    }

    @Test fun migratesExistingCoverageAndClampsDamagedSaves() {
        assertEquals(96, LampSupplyRange.fromSave(null, 96))
        assertEquals(128, LampSupplyRange.fromSave(128, 96))
        assertEquals(512, LampSupplyRange.fromSave(Int.MAX_VALUE, 96))
        assertEquals(1, LampSupplyRange.fromSave(-1, 96))
        assertEquals(64, LampSupplyRange.next(33))
    }
}
