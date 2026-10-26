package mods.eln.client.terrain

/** Keep power-line snapshots ahead of decorative devices within the same fixed
 * memory and render budgets. Both tiers retain their existing distance/order. */
internal object DistantRetention {
    fun <T> victim(keys: Collection<T>,grid: Set<T>): T=keys.firstOrNull { it !in grid } ?: keys.first()
    fun <T> renderOrder(nearby: List<T>,grid: Set<T>): List<T> = nearby.filter { it in grid }+nearby.filter { it !in grid }
}
