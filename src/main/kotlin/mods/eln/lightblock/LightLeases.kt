package mods.eln.lightblock

/** Equal brightness contributions are interchangeable: only their longest remaining
 * lifetime can affect the maximum emitted light. Keep one deadline per light level,
 * even when ELN refreshes a resident block whose vanilla chunk is not ticking. */
internal class LightLeases {
    private val remaining = IntArray(16)
    var activeLevels = 0
        private set

    fun refresh(light: Int, timeout: Int) {
        val level = light.coerceIn(0, 15)
        if (remaining[level] == 0) activeLevels++
        // A legacy report with a nonpositive timeout still contributes for one tick.
        remaining[level] = maxOf(remaining[level], timeout.coerceAtLeast(1))
    }

    /** Emit before decrementing, preserving the previous last-tick/next-tick removal. */
    fun advance(): Int? {
        if (activeLevels == 0) return null
        var light = 0
        for (level in remaining.indices) {
            if (remaining[level] > 0) {
                light = level
                if (--remaining[level] == 0) activeLevels--
            }
        }
        return light
    }
}
