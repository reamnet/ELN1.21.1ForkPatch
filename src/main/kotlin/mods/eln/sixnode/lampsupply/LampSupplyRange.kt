package mods.eln.sixnode.lampsupply

/** Deliberate, bounded power coverage; independent of conductor stack size. */
object LampSupplyRange {
    private val choices = intArrayOf(32, 64, 128, 256, 512)
    @JvmStatic fun isSelectable(range: Int) = range in choices
    @JvmStatic fun next(range: Int) = choices.firstOrNull { it > range } ?: choices.first()
    @JvmStatic fun fromSave(saved: Int?, legacyRange: Int) = (saved ?: legacyRange).coerceIn(1, 512)
}
