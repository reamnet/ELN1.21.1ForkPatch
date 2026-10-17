package mods.eln.sim.mna

import org.apache.commons.numbers.core.DD

/** Pivoted LU in the same double-double precision as the stamp matrix.
 * Large cable networks are sparse: factor once, solve triangular systems, and cache only
 * inverse rows actually requested by port probes. Never form the full dense inverse.
 */
class MnaFactorization @JvmOverloads constructor(matrix: Array<Array<DD>>, previous: MnaFactorization? = null) {
    private val size = matrix.size
    private val lu = Array(size) { matrix[it].clone() }
    private val permutation = IntArray(size) { it }
    private val columns = IntArray(size) { it }
    private val rowScale = DoubleArray(size) { row -> matrix[row].maxOf { it.abs().toDouble() }.takeIf { it > 0.0 } ?: 1.0 }
    private val inverseRows = arrayOfNulls<Array<DD>>(size)
    private val lower: Array<IntArray>
    private val upper: Array<IntArray>
    private val lowerTranspose: Array<IntArray>
    private val upperTranspose: Array<IntArray>
    val reusedOrdering = previous != null && previous.size == size

    init {
        if (reusedOrdering) {
            previous!!.permutation.copyInto(permutation); previous.columns.copyInto(columns)
        }
        for (row in 0 until size) for (column in 0 until size) {
            val value = matrix[permutation[row]][columns[column]]
            lu[row][column] = if (value.isZero) DD.ZERO else value.divide(rowScale[permutation[row]])
        }
        for (column in 0 until size) {
            // Markowitz ordering eliminates voltage constraints and leaf branches first.
            // Ordinary largest-column pivoting fills a cable tree into a dense current block.
            var pivot = -1; var pivotColumn = column
            if (reusedOrdering) {
                // Temperature/source-mask changes keep the same variable identities. Reuse
                // their sparse column ordering; numerical row pivoting is still performed.
                var magnitude = 0.0
                for (r in column until size) {
                    val candidate = lu[r][column].abs().toDouble()
                    if (candidate > magnitude) { magnitude = candidate; pivot = r }
                }
            } else {
                val rowCount = IntArray(size); val columnCount = IntArray(size); val maximum = DoubleArray(size)
                for (r in column until size) for (c in column until size) if (!lu[r][c].isZero) {
                    rowCount[r]++; columnCount[c]++
                    maximum[c] = maxOf(maximum[c], lu[r][c].abs().toDouble())
                }
                var cost = Long.MAX_VALUE; var width = Int.MAX_VALUE
                for (r in column until size) for (c in column until size) {
                    if (lu[r][c].isZero || lu[r][c].abs().toDouble() < maximum[c] * .1) continue
                    val fill = (rowCount[r] - 1).toLong() * (columnCount[c] - 1)
                    if (fill < cost || (fill == cost && rowCount[r] < width)) {
                        pivot = r; pivotColumn = c; cost = fill; width = rowCount[r]
                    }
                }
            }
            check(pivot >= 0 && lu[pivot][pivotColumn].isFinite) { "Matrix is singular" }
            if (pivot != column) {
                val row = lu[column]; lu[column] = lu[pivot]; lu[pivot] = row
                val index = permutation[column]; permutation[column] = permutation[pivot]; permutation[pivot] = index
            }
            if (pivotColumn != column) {
                for (row in 0 until size) {
                    val value = lu[row][column]; lu[row][column] = lu[row][pivotColumn]; lu[row][pivotColumn] = value
                }
                val index = columns[column]; columns[column] = columns[pivotColumn]; columns[pivotColumn] = index
            }
            val active = (column + 1 until size).filter { !lu[column][it].isZero }.toIntArray()
            for (row in column + 1 until size) {
                if (lu[row][column].isZero) continue
                val factor = lu[row][column].divide(lu[column][column])
                lu[row][column] = factor
                for (j in active) lu[row][j] = lu[row][j].subtract(factor.multiply(lu[column][j]))
            }
        }
        lower = Array(size) { row -> (0 until row).filter { !lu[row][it].isZero }.toIntArray() }
        upper = Array(size) { row -> (row + 1 until size).filter { !lu[row][it].isZero }.toIntArray() }
        upperTranspose = Array(size) { column -> (0 until column).filter { !lu[it][column].isZero }.toIntArray() }
        lowerTranspose = Array(size) { column -> (column + 1 until size).filter { !lu[it][column].isZero }.toIntArray() }
    }

    fun solve(rhs: DoubleArray): Array<DD> {
        val value = Array(size) { DD.of(rhs[permutation[it]]).divide(rowScale[permutation[it]]) }
        for (row in 0 until size) for (j in lower[row])
            value[row] = value[row].subtract(lu[row][j].multiply(value[j]))
        for (row in size - 1 downTo 0) {
            for (j in upper[row]) value[row] = value[row].subtract(lu[row][j].multiply(value[j]))
            value[row] = value[row].divide(lu[row][row])
        }
        val result = Array(size) { DD.ZERO }
        for (row in 0 until size) result[columns[row]] = value[row]
        return result
    }

    /** Solve A^T*y=e_index, so y is one row of A^-1, including row/column permutations and row scaling. */
    fun inverseRow(index: Int): Array<DD> = inverseRows[index] ?: run {
        val value = Array(size) { if (columns[it] == index) DD.ONE else DD.ZERO }
        for (row in 0 until size) {
            for (j in upperTranspose[row]) value[row] = value[row].subtract(lu[j][row].multiply(value[j]))
            value[row] = value[row].divide(lu[row][row])
        }
        for (row in size - 1 downTo 0) for (j in lowerTranspose[row])
            value[row] = value[row].subtract(lu[j][row].multiply(value[j]))
        val result = Array(size) { DD.ZERO }
        for (row in 0 until size) result[permutation[row]] = value[row].divide(rowScale[permutation[row]])
        inverseRows[index] = result
        result
    }
}
