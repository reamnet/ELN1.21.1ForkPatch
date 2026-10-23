package mods.eln.environment

import org.junit.Test
import kotlin.test.*

class ClimateSampleCacheTest {
    private fun state(temp: Double) = ClimateState(temp, 50.0, .5, false, "none")

    @Test fun repeatedWatchdogsShareAnExactLocationButClimateUpdatesEveryFrame() {
        val cache = ClimateSampleCache<String, Any>()
        var discoveries = 0
        var calculations = 0
        var ambient = 20.0
        fun sample(tick: Long, frame: Any) = cache.sample(1L, tick, frame,
            { discoveries++; "Plains" }, { calculations++; state(ambient) })
        val first = Any()
        val initial = sample(0, first)
        repeat(20) { assertSame(initial, sample(0, first)) }
        ambient = 25.0
        val next = sample(1, Any())
        assertEquals(25.0, next.temperatureCelsius)
        assertEquals(1, discoveries)
        assertEquals(2, calculations)
    }

    @Test fun biomeEditsRefreshAfterOneSecondAndClockRollbackExpiresThem() {
        val cache = ClimateSampleCache<String, Any>()
        var biome = "Plains"
        var discoveries = 0
        fun sample(tick: Long) = cache.sample(1, tick, Any(), { discoveries++; biome },
            { state(if (it == "Plains") 20.0 else 40.0) })
        assertEquals(20.0, sample(100).temperatureCelsius)
        biome = "Desert"
        assertEquals(20.0, sample(119).temperatureCelsius)
        assertEquals(40.0, sample(120).temperatureCelsius)
        biome = "Plains"
        assertEquals(20.0, sample(0).temperatureCelsius)
        assertEquals(3, discoveries)
    }

    @Test fun exactPositionsDoNotMergeAltitudeOrBiomeBoundarySamples() {
        val cache = ClimateSampleCache<String, Any>()
        val frame = Any()
        for (key in listOf(-1L, 0L, 1L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            assertEquals(key.toDouble(), cache.sample(key, 0, frame, { key.toString() },
                { state(it.toDouble()) }).temperatureCelsius)
        }
    }

    @Test fun boundedCacheEvictsLeastRecentlyUsedAndRetainsRecentDevices() {
        val cache = ClimateSampleCache<String, Any>(capacity = 2)
        val reads = mutableListOf<Long>()
        val frame = Any()
        fun sample(pos: Long) = cache.sample(pos, 0, frame, { reads += pos; "$pos" }, { state(20.0) })
        sample(1); sample(2); sample(1); sample(3); sample(1); sample(2)
        assertEquals(listOf(1L, 2L, 3L, 2L), reads)
    }

    @Test fun separateWorldCachesNeverShareClimateOrBiomes() {
        val a = ClimateSampleCache<String, Any>()
        val b = ClimateSampleCache<String, Any>()
        val frame = Any()
        assertEquals(20.0, a.sample(1, 0, frame, { "warm" }, { state(20.0) }).temperatureCelsius)
        assertEquals(-10.0, b.sample(1, 0, frame, { "cold" }, { state(-10.0) }).temperatureCelsius)
    }

    @Test fun cachedAmbientDoesNotCacheTheLiveThermalLoadOrBypassProtection() {
        val cache = ClimateSampleCache<String, Any>()
        val frame = Any()
        val load = mods.eln.sim.ThermalLoad().apply { temperatureCelsius = 20.0 }
        val watchdog = mods.eln.sim.process.destruct.ThermalLoadWatchDog(load)
            .setAmbientTemperatureProvider { cache.sample(1, 0, frame, { "Plains" }, { state(25.0) }).temperatureCelsius }
            .setTemperatureLimits(100.0, -40.0)
        assertEquals(45.0, watchdog.getValue())
        load.temperatureCelsius = 80.0
        assertEquals(105.0, watchdog.getValue())
        assertTrue(watchdog.getValue() > watchdog.max)
    }
}
