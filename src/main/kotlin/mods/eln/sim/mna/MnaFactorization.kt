package mods.eln.sim.mna

import org.apache.commons.numbers.core.DD
import java.util.BitSet

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
    private val cachedDivisors = !java.lang.Boolean.getBoolean("eln.disableCachedMnaDivisors")
    private val scaleReciprocals = Array(size) { row ->
        if(rowScale[row]==1.0)null else safeReciprocal(DD.of(rowScale[row]))
    }
    private val diagonalReciprocals: Array<DD?>
    private val inverseRows = arrayOfNulls<Array<DD>>(size)
    private val lower: Array<IntArray>
    private val upper: Array<IntArray>
    private val lowerTranspose: Array<IntArray>
    private val upperTranspose: Array<IntArray>
    val reusedOrdering = previous != null && previous.size == size

    init {
        // Track actual fill while eliminating. Scanning every zero in the dense
        // storage for each Markowitz pivot made sparse feeder startup cubic.
        val entries = Array(size) { BitSet(size) }
        if (reusedOrdering) {
            previous!!.permutation.copyInto(permutation); previous.columns.copyInto(columns)
        }
        for (row in 0 until size) for (column in 0 until size) {
            val value = matrix[permutation[row]][columns[column]]
            lu[row][column] = if (value.isZero) DD.ZERO else value.divide(rowScale[permutation[row]])
            if (!lu[row][column].isZero) entries[row].set(column)
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
                for (r in column until size) {
                    var c = entries[r].nextSetBit(column)
                    while (c >= 0) {
                        rowCount[r]++; columnCount[c]++
                        maximum[c] = maxOf(maximum[c], lu[r][c].abs().toDouble())
                        c = entries[r].nextSetBit(c + 1)
                    }
                }
                var cost = Long.MAX_VALUE; var width = Int.MAX_VALUE
                for (r in column until size) {
                    var c = entries[r].nextSetBit(column)
                    while (c >= 0) {
                        if (lu[r][c].abs().toDouble() >= maximum[c] * .1) {
                            val fill = (rowCount[r] - 1).toLong() * (columnCount[c] - 1)
                            if (fill < cost || (fill == cost && rowCount[r] < width)) {
                                pivot = r; pivotColumn = c; cost = fill; width = rowCount[r]
                            }
                        }
                        c = entries[r].nextSetBit(c + 1)
                    }
                }
            }
            check(pivot >= 0 && lu[pivot][pivotColumn].isFinite) { "Matrix is singular" }
            if (pivot != column) {
                val row = lu[column]; lu[column] = lu[pivot]; lu[pivot] = row
                val pattern = entries[column]; entries[column] = entries[pivot]; entries[pivot] = pattern
                val index = permutation[column]; permutation[column] = permutation[pivot]; permutation[pivot] = index
            }
            if (pivotColumn != column) {
                for (row in 0 until size) {
                    val value = lu[row][column]; lu[row][column] = lu[row][pivotColumn]; lu[row][pivotColumn] = value
                    if (entries[row][column] != entries[row][pivotColumn]) {
                        entries[row].flip(column); entries[row].flip(pivotColumn)
                    }
                }
                val index = columns[column]; columns[column] = columns[pivotColumn]; columns[pivotColumn] = index
            }
            val active = indices(entries[column], column + 1, size)
            for (row in column + 1 until size) {
                if (lu[row][column].isZero) continue
                val factor = lu[row][column].divide(lu[column][column])
                lu[row][column] = factor
                entries[row].set(column, !factor.isZero)
                for (j in active) {
                    lu[row][j] = lu[row][j].subtract(factor.multiply(lu[column][j]))
                    entries[row].set(j, !lu[row][j].isZero)
                }
            }
        }
        lower = Array(size) { row -> indices(entries[row], 0, row) }
        upper = Array(size) { row -> indices(entries[row], row + 1, size) }
        val lowerColumns = Array(size) { ArrayList<Int>() }
        val upperColumns = Array(size) { ArrayList<Int>() }
        for (row in 0 until size) {
            for (column in lower[row]) lowerColumns[column].add(row)
            for (column in upper[row]) upperColumns[column].add(row)
        }
        upperTranspose = Array(size) { upperColumns[it].toIntArray() }
        lowerTranspose = Array(size) { lowerColumns[it].toIntArray() }
        // Factorization arithmetic and ordering are unchanged. Only repeated
        // triangular/RHS solves reuse these fixed divisors in DD precision.
        diagonalReciprocals = Array(size) { row ->
            if(lu[row][row]==DD.ONE)null else safeReciprocal(lu[row][row])
        }
    }

    private fun safeReciprocal(divisor: DD): DD? {
        // At extreme exponents a reciprocal loses its DD low part through
        // underflow, even when division itself has a normal, precise result.
        if(!cachedDivisors || kotlin.math.abs(divisor.toDouble()) !in 1e-200..1e200)return null
        return divisor.reciprocal().takeIf { it.isFinite && !it.isZero }
    }

    private fun quotient(value: DD,divisor: DD,reciprocal: DD?): DD {
        if(reciprocal==null)return value.divide(divisor)
        val result=value.multiply(reciprocal)
        return if(result.isFinite)result else value.divide(divisor)
    }

    private fun scale(value: DD,row: Int): DD {
        val reciprocal=scaleReciprocals[row] ?: return value.divide(rowScale[row])
        val result=value.multiply(reciprocal)
        return if(result.isFinite)result else value.divide(rowScale[row])
    }
    private fun pivot(value: DD,row: Int)=quotient(value,lu[row][row],diagonalReciprocals[row])

    private fun indices(entries: BitSet, start: Int, end: Int): IntArray {
        val result = IntArray(entries.cardinality())
        var count = 0
        var column = entries.nextSetBit(start)
        while (column >= 0 && column < end) {
            result[count++] = column
            column = entries.nextSetBit(column + 1)
        }
        return result.copyOf(count)
    }

    fun solve(rhs: DoubleArray): Array<DD> {
        val value = Array(size) {
            val input = rhs[permutation[it]]
            if (input == 0.0) DD.ZERO else if (rowScale[permutation[it]] == 1.0) DD.of(input)
            else scale(DD.of(input),permutation[it])
        }
        for (row in 0 until size) for (j in lower[row]) if (!value[j].isZero)
            value[row] = value[row].subtract(lu[row][j].multiply(value[j]))
        for (row in size - 1 downTo 0) {
            for (j in upper[row]) if (!value[j].isZero) value[row] = value[row].subtract(lu[row][j].multiply(value[j]))
            if (!value[row].isZero && lu[row][row] != DD.ONE) value[row] = pivot(value[row],row)
        }
        val result = Array(size) { DD.ZERO }
        for (row in 0 until size) result[columns[row]] = value[row]
        return result
    }

    /** Solve A^T*y=e_index, so y is one row of A^-1, including row/column permutations and row scaling. */
    fun inverseRow(index: Int): Array<DD> = inverseRows[index] ?: run {
        val value = Array(size) { if (columns[it] == index) DD.ONE else DD.ZERO }
        for (row in 0 until size) {
            for (j in upperTranspose[row]) if (!value[j].isZero) value[row] = value[row].subtract(lu[j][row].multiply(value[j]))
            if (!value[row].isZero && lu[row][row] != DD.ONE) value[row] = pivot(value[row],row)
        }
        for (row in size - 1 downTo 0) for (j in lowerTranspose[row]) if (!value[j].isZero)
            value[row] = value[row].subtract(lu[j][row].multiply(value[j]))
        val result = Array(size) { DD.ZERO }
        for (row in 0 until size) result[permutation[row]] = if (value[row].isZero || rowScale[permutation[row]] == 1.0) value[row]
            else scale(value[row],permutation[row])
        inverseRows[index] = result
        result
    }
}
