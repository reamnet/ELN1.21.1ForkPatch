package mods.eln.sim.mna

import org.apache.commons.numbers.core.DD

/** A read-only inverse row for one matrix generation. Reuse its DD dot product
 * when only a few RHS entries change. Deltas always use a fixed full-dot anchor,
 * rather than accumulating roundoff from one speculative probe into the next.
 */
class AffinePortProjection(row: Array<DD>) {
    private val columns = row.indices.filter { !row[it].isZero }.toIntArray()
    private val coefficients = Array(columns.size) { row[columns[it]] }
    private val anchorRhs = DoubleArray(columns.size)
    private val changes = IntArray(4)
    private var anchor: DD? = null
    private var trials = 0
    var shortcuts = 0L
        private set

    fun project(rhs: DoubleArray): DD {
        val saved = anchor
        if (saved != null && trials < 16) {
            var count = 0
            for (i in columns.indices) {
                val value = rhs[columns[i]]
                if (value != anchorRhs[i]) {
                    if (count == changes.size || !value.isFinite()) { count = -1; break }
                    changes[count++] = i
                }
            }
            if (count >= 0) {
                var value: DD = saved
                for (j in 0 until count) {
                    val i = changes[j]
                    val delta = DD.of(rhs[columns[i]]).subtract(anchorRhs[i])
                    value = value.add(coefficients[i].multiply(delta))
                }
                if (value.isFinite) {
                    trials++; shortcuts++
                    return value
                }
            }
        }
        var value = DD.ZERO
        for (i in columns.indices) {
            val entry = rhs[columns[i]]
            anchorRhs[i] = entry
            if (entry != 0.0) value = value.add(coefficients[i].multiply(entry))
        }
        anchor = value.takeIf { it.isFinite }
        trials = 0
        return value
    }
}
