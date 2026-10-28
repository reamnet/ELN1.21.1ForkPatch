package mods.eln.lighting

import mods.eln.sixnode.lampsocket.LampLightingQueries
import net.minecraft.core.BlockPos
import org.junit.Test
import kotlin.test.*

class LampLightingQueriesTest {
    @Test fun overlappingBulbBeamsReadEachLoadedCellOnceWithoutLoadingMissingChunks() {
        var chunks=0;var blocks=0
        val wall=BlockPos(8,64,0)
        val query=LampLightingQueries({ chunks++;it.x<16 },{ blocks++;it==wall })
        val ends=List(20) {
            (1..20).map { x->BlockPos(x,64,0) }.takeWhile(query::open).last()
        }
        assertTrue(ends.all { it==wall.west() })
        assertEquals(1,chunks);assertEquals(8,blocks)
        assertFalse(query.open(BlockPos(16,64,0)));assertFalse(query.open(BlockPos(17,64,0)))
        assertEquals(2,chunks);assertEquals(8,blocks,"Unloaded cells must never query block state")
    }

    @Test fun newUpdateSeesChangedObstructionsAndNewlyLoadedChunksImmediately() {
        var loaded=false;var wall=true;val at=BlockPos(-1,64,-1)
        fun update()=LampLightingQueries({ loaded },{ wall })
        assertFalse(update().open(at))
        loaded=true;assertFalse(update().open(at))
        wall=false;assertTrue(update().open(at))
        wall=true;assertFalse(update().open(at))
    }
}
