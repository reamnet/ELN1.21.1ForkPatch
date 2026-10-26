package mods.eln.client.terrain

import org.junit.Test
import kotlin.test.*

class DistantRenderBindingTest {
    @Test fun registryReplacementReleasesAgainstOldOwnerAndRetainsStableBindings() {
        val b=DistantRenderBinding();val first=Any();val replacement=Any();var releases=0
        assertTrue(b.bind(first) { assertNull(b.register);releases++ })
        assertEquals(1,b.generation)
        repeat(20) { assertFalse(b.bind(first) { error("Stable registry must not rebuild groups") }) }
        assertTrue(b.bind(replacement) { assertSame(first,b.register);releases++ })
        assertSame(replacement,b.register);assertEquals(2,b.generation);assertEquals(2,releases)
        b.clear { assertSame(replacement,b.register) };assertNull(b.register)
        assertTrue(b.bind(replacement) { assertNull(b.register) });assertEquals(3,b.generation)
    }
}
