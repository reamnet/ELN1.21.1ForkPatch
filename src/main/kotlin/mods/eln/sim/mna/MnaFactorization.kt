package mods.eln.sim.mna

import org.apache.commons.numbers.core.DD
import java.util.BitSet

/** Pivoted LU in the same double-double precision as the stamp matrix.
 * Large cable networks are sparse: factor once, solve triangular systems, and cache only
 * inverse rows actually requested by port probes. Never form the full dense inverse.
 */
class MnaFactorization @JvmOverloads constructor(matrix: Array<Array<DD>>, previous: MnaFactorization? = null,
    private val sparseWorkspace: Boolean = !java.lang.Boolean.getBoolean("eln.disableSparseMnaRebuild"),
    private val columnWorksets: Boolean = !java.lang.Boolean.getBoolean("eln.disableMnaColumnWorksets")) {
    private val size = matrix.size
    // Null is an unstored zero. Lower/upper patterns still track every actual fill entry.
    private val lu: Array<Array<DD?>>
    private val permutation = IntArray(size) { it }
    private val columns = IntArray(size) { it }
    private val rowScale = DoubleArray(size)
    private val cachedDivisors = !java.lang.Boolean.getBoolean("eln.disableCachedMnaDivisors")
    private val scaleReciprocals: Array<DD?>
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
        // Discover row scale and input sparsity in one pass. Only nonzero entries
        // need normalization/stores; cloning the dense input first was discarded work.
        val inputColumns=if(sparseWorkspace)Array(size) { row ->
            var indices=IntArray(minOf(8,size));var count=0;var maximum=0.0
            for(column in 0 until size) {
                val value=matrix[row][column]
                if(!value.isZero) {
                    if(count==indices.size)indices=indices.copyOf(maxOf(8,count*2))
                    indices[count++]=column
                    maximum=maxOf(maximum,value.abs().toDouble())
                }
            }
            rowScale[row]=maximum.takeIf { it>0.0 } ?: 1.0
            indices.copyOf(count)
        } else {
            for(row in 0 until size)rowScale[row]=matrix[row].maxOf { it.abs().toDouble() }.takeIf { it>0.0 } ?: 1.0
            null
        }
        @Suppress("UNCHECKED_CAST")
        lu=Array(size) { row -> if(sparseWorkspace)arrayOfNulls(size) else matrix[row].clone() as Array<DD?> }
        scaleReciprocals=Array(size) { row -> if(rowScale[row]==1.0)null else safeReciprocal(DD.of(rowScale[row])) }
        if(sparseWorkspace) {
            val inverseColumns=IntArray(size)
            for(column in 0 until size)inverseColumns[columns[column]]=column
            for(row in 0 until size) {
                val originalRow=permutation[row]
                for(originalColumn in inputColumns!![originalRow]) {
                    val column=inverseColumns[originalColumn]
                    val value=matrix[originalRow][originalColumn].divide(rowScale[originalRow])
                    if(!value.isZero) { lu[row][column]=value;entries[row].set(column) }
                }
            }
        } else for(row in 0 until size)for(column in 0 until size) {
            val value=matrix[permutation[row]][columns[column]]
            val scaled=if(value.isZero)DD.ZERO else value.divide(rowScale[permutation[row]])
            lu[row][column]=scaled
            if(!scaled.isZero)entries[row].set(column)
        }
        // A column workset tracks the same nonzeros as the row patterns, including
        // fill and cancellation. Ascending iteration preserves pivot ties and the
        // elimination order while avoiding scans through unrelated cable branches.
        val rowsByColumn = if (columnWorksets) Array(size) { BitSet(size) } else null
        if (rowsByColumn != null) for (row in 0 until size) {
            var column = entries[row].nextSetBit(0)
            while (column >= 0) {
                rowsByColumn[column].set(row)
                column = entries[row].nextSetBit(column + 1)
            }
        }
        for (column in 0 until size) {
            // Markowitz ordering eliminates voltage constraints and leaf branches first.
            // Ordinary largest-column pivoting fills a cable tree into a dense current block.
            var pivot = -1; var pivotColumn = column
            if (reusedOrdering) {
                // Temperature/source-mask changes keep the same variable identities. Reuse
                // their sparse column ordering; numerical row pivoting is still performed.
                var magnitude = 0.0
                var r = rowsByColumn?.get(column)?.nextSetBit(column) ?: column
                while (r >= 0 && r < size) {
                    val candidate = lu[r][column]?.abs()?.toDouble() ?: 0.0
                    if (candidate > magnitude) { magnitude = candidate; pivot = r }
                    r = if (rowsByColumn != null) rowsByColumn[column].nextSetBit(r + 1) else r + 1
                }
            } else {
                val rowCount = IntArray(size); val columnCount = IntArray(size); val maximum = DoubleArray(size)
                for (r in column until size) {
                    var c = entries[r].nextSetBit(column)
                    while (c >= 0) {
                        rowCount[r]++; columnCount[c]++
                        maximum[c] = maxOf(maximum[c], lu[r][c]!!.abs().toDouble())
                        c = entries[r].nextSetBit(c + 1)
                    }
                }
                var cost = Long.MAX_VALUE; var width = Int.MAX_VALUE
                for (r in column until size) {
                    var c = entries[r].nextSetBit(column)
                    while (c >= 0) {
                        if (lu[r][c]!!.abs().toDouble() >= maximum[c] * .1) {
                            val fill = (rowCount[r] - 1).toLong() * (columnCount[c] - 1)
                            if (fill < cost || (fill == cost && rowCount[r] < width)) {
                                pivot = r; pivotColumn = c; cost = fill; width = rowCount[r]
                            }
                        }
                        c = entries[r].nextSetBit(c + 1)
                    }
                }
            }
            check(pivot >= 0 && lu[pivot][pivotColumn]?.isFinite==true) { "Matrix is singular" }
            if (pivot != column) {
                if (rowsByColumn != null) {
                    // Only differing memberships change when two rows trade places.
                    val changed = entries[column].clone() as BitSet
                    changed.xor(entries[pivot])
                    var c = changed.nextSetBit(0)
                    while (c >= 0) {
                        rowsByColumn[c].flip(column); rowsByColumn[c].flip(pivot)
                        c = changed.nextSetBit(c + 1)
                    }
                }
                val row = lu[column]; lu[column] = lu[pivot]; lu[pivot] = row
                val pattern = entries[column]; entries[column] = entries[pivot]; entries[pivot] = pattern
                val index = permutation[column]; permutation[column] = permutation[pivot]; permutation[pivot] = index
            }
            if (pivotColumn != column) {
                if (rowsByColumn != null) {
                    val pattern = rowsByColumn[column]
                    rowsByColumn[column] = rowsByColumn[pivotColumn]
                    rowsByColumn[pivotColumn] = pattern
                }
                for (row in 0 until size) {
                    val value = lu[row][column]; lu[row][column] = lu[row][pivotColumn]; lu[row][pivotColumn] = value
                    if (entries[row][column] != entries[row][pivotColumn]) {
                        entries[row].flip(column); entries[row].flip(pivotColumn)
                    }
                }
                val index = columns[column]; columns[column] = columns[pivotColumn]; columns[pivotColumn] = index
            }
            val active = indices(entries[column], column + 1, size)
            var row = rowsByColumn?.get(column)?.nextSetBit(column + 1) ?: (column + 1)
            while (row >= 0 && row < size) {
                val entry = lu[row][column]
                if (entry != null && !entry.isZero) {
                    val factor = entry.divide(lu[column][column]!!)
                    lu[row][column] = if(sparseWorkspace && factor.isZero)null else factor
                    entries[row].set(column, !factor.isZero)
                    rowsByColumn?.get(column)?.set(row, !factor.isZero)
                    for (j in active) {
                        val value=(lu[row][j] ?: DD.ZERO).subtract(factor.multiply(lu[column][j]!!))
                        lu[row][j] = if(sparseWorkspace && value.isZero)null else value
                        entries[row].set(j, !value.isZero)
                        rowsByColumn?.get(j)?.set(row, !value.isZero)
                    }
                }
                row = if (rowsByColumn != null) rowsByColumn[column].nextSetBit(row + 1) else row + 1
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
            if(lu[row][row]==DD.ONE)null else safeReciprocal(lu[row][row]!!)
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
    private fun pivot(value: DD,row: Int)=quotient(value,lu[row][row]!!,diagonalReciprocals[row])

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
            value[row] = value[row].subtract(lu[row][j]!!.multiply(value[j]))
        for (row in size - 1 downTo 0) {
            for (j in upper[row]) if (!value[j].isZero) value[row] = value[row].subtract(lu[row][j]!!.multiply(value[j]))
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
            for (j in upperTranspose[row]) if (!value[j].isZero) value[row] = value[row].subtract(lu[j][row]!!.multiply(value[j]))
            if (!value[row].isZero && lu[row][row] != DD.ONE) value[row] = pivot(value[row],row)
        }
        for (row in size - 1 downTo 0) for (j in lowerTranspose[row]) if (!value[j].isZero)
            value[row] = value[row].subtract(lu[j][row]!!.multiply(value[j]))
        val result = Array(size) { DD.ZERO }
        for (row in 0 until size) result[permutation[row]] = if (value[row].isZero || rowScale[permutation[row]] == 1.0) value[row]
            else scale(value[row],permutation[row])
        inverseRows[index] = result
        result
    }
}
