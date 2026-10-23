package mods.eln.environment

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap

/** Exact block positions; only biome discovery is throttled, never the thermal state itself. */
internal class ClimateSampleCache<B : Any, F : Any>(
    private val capacity: Int = 16_384,
    private val biomeRefreshTicks: Long = 20
) {
    private class Entry<B, F>(var biome: B, var biomeTick: Long, var frame: F, var climate: ClimateState)
    private val entries = Long2ObjectLinkedOpenHashMap<Entry<B, F>>()

    init { require(capacity > 0 && biomeRefreshTicks > 0) }

    @Synchronized
    fun sample(position: Long, tick: Long, frame: F, biome: (B?) -> B, climate: (B) -> ClimateState): ClimateState {
        val cached = entries.getAndMoveToLast(position)
        if (cached == null) {
            val resolved = biome(null)
            val result = climate(resolved)
            entries.put(position, Entry(resolved, tick, frame, result))
            if (entries.size > capacity) entries.removeFirst()
            return result
        }
        val refresh = tick < cached.biomeTick || tick - cached.biomeTick >= biomeRefreshTicks
        if (refresh) {
            cached.biome = biome(cached.biome)
            cached.biomeTick = tick
        }
        if (refresh || cached.frame !== frame) {
            cached.climate = climate(cached.biome)
            cached.frame = frame
        }
        return cached.climate
    }
}
