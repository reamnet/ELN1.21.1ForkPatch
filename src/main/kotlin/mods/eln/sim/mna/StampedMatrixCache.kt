package mods.eln.sim.mna

import mods.eln.sim.mna.state.State
import org.apache.commons.numbers.core.DD

/** Small subsystem-local cache of immutable matrix solvers. A hit requires the same
 * state identities/order and every DD coefficient, including its low part. Source
 * commands and physical values are never cached; the caller resets RHS validation
 * history when restoring a solver.
 * Matrices passed to [put] must remain read-only, as SubSystem's stamps already do.
 */
internal class StampedMatrixCache<T : Any> @JvmOverloads constructor(
    private val maxCells: Int = 262_144,
    private val maxEntries: Int = 4
) {
    private class Entry<T>(var matrix: Array<Array<DD>>, val states: Array<State>, val value: T)
    private val entries = ArrayList<Entry<T>>()
    private var cells = 0L

    fun find(matrix: Array<Array<DD>>, states: List<State>): T? {
        for (index in entries.indices) {
            val entry = entries[index]
            if (entry.states.size != states.size || entry.matrix.size != matrix.size) continue
            var equal = true
            for (i in states.indices) if (entry.states[i] !== states[i]) { equal = false; break }
            if (!equal) continue
            for (row in matrix.indices) {
                if (!entry.matrix[row].contentEquals(matrix[row])) { equal = false; break }
            }
            if (equal) {
                // Keep the current stamp rather than retaining a duplicate of it.
                entry.matrix = matrix
                entries.removeAt(index)
                entries.add(entry)
                return entry.value
            }
        }
        return null
    }

    fun put(matrix: Array<Array<DD>>, states: List<State>, value: T) {
        val count = matrix.sumOf { it.size.toLong() }
        if (count > maxCells || maxEntries <= 0) return
        while (entries.isNotEmpty() && (entries.size >= maxEntries || cells + count > maxCells)) {
            val removed = entries.removeAt(0)
            cells -= removed.matrix.sumOf { it.size.toLong() }
        }
        entries.add(Entry(matrix, states.toTypedArray(), value))
        cells += count
    }

    /** Trial-only masks need not retain old LU storage between physical steps. */
    fun retainCurrent(matrix: Array<Array<DD>>) {
        for (index in entries.lastIndex downTo 0) {
            if (entries[index].matrix !== matrix) {
                val removed = entries.removeAt(index)
                cells -= removed.matrix.sumOf { it.size.toLong() }
            }
        }
    }
}
