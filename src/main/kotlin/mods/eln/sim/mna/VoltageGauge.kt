package mods.eln.sim.mna

import org.apache.commons.numbers.core.DD
import mods.eln.sim.mna.state.State
import mods.eln.sim.mna.state.VoltageState
import kotlin.math.abs
import kotlin.math.max
import java.util.function.Function
import java.util.function.IntFunction

/** Removes only an additive voltage reference freedom. No leakage current is invented.
 * Each discarded KCL equation is checked after solving; an unbalanced current source into
 * a floating island is therefore rejected rather than absorbed by a fictitious ground.
 */
class VoltageGauge @JvmOverloads constructor(matrix: Array<DoubleArray>, states: List<State>,
    preservePhysicalMatrix: Boolean = false) {
    // The legacy in-place constructor takes a full snapshot for comparisons.
    // A read-only physical matrix needs only the discarded KCL rows saved below.
    private val discardedRows = ArrayList<DoubleArray>()
    private val rows: IntArray
    private val referenceRows = BooleanArray(states.size)
    private val savedRhs: DoubleArray
    private var lastProbeRhs: DoubleArray? = null
    private var lastProbeValid = false
    var probeRhsChanges=0
        private set
    private val supportedExcitations = HashMap<Long, Boolean>()

    init {
        val original = if (preservePhysicalMatrix) null else matrix.map { it.copyOf() }
        val references = mutableListOf<Int>()
        val voltages = states.indices.filter { states[it] is VoltageState }
        val parent = IntArray(states.size) { it }
        fun root(i: Int): Int {
            var p = i
            while (parent[p] != p) p = parent[p]
            return p
        }
        // Voltage constraints, not just resistors, determine reference connectivity.
        for (row in matrix) {
            val pins = voltages.filter { row[it] != 0.0 }
            if (pins.size > 1) for (pin in pins.drop(1)) parent[root(pin)] = root(pins[0])
        }
        for (group in voltages.groupBy { root(it) }.values) {
            val floating = matrix.indices.all { index ->
                // Earlier reference rows are unit rows on disjoint, already
                // processed groups. Their contribution to this group is zero,
                // including when inspecting an unmodified physical matrix.
                if (referenceRows[index]) true else {
                    val row = matrix[index]
                    val sum = group.sumOf { row[it] }
                    val scale = group.sumOf { abs(row[it]) }
                    abs(sum) <= scale * 1e-12
                }
            }
            if (floating) {
                val pin = group.first()
                references += pin
                referenceRows[pin] = true
                discardedRows += original?.get(pin) ?: matrix[pin].copyOf()
                if (!preservePhysicalMatrix) {
                    matrix[pin].fill(0.0)
                    matrix[pin][pin] = 1.0
                }
            }
        }
        rows = references.toIntArray()
        savedRhs = DoubleArray(rows.size)
    }

    val active: Boolean get() = rows.isNotEmpty()
    fun isReference(index: Int)=referenceRows[index]

    /** Apply the same reference choice to the precision-preserving solve matrix. */
    fun applyMatrix(matrix: Array<Array<DD>>) {
        for (row in rows) {
            matrix[row].fill(DD.ZERO)
            matrix[row][row] = DD.ONE
        }
    }

    /** Unchanged precision rows are shared read-only for this matrix generation.
     * Only discarded KCL rows are replaced. Neither input nor shared rows may be
     * mutated by a solve; stamping the next generation uses new physical arrays.
     */
    fun referenceCopy(matrix: Array<Array<DD>>): Array<Array<DD>> {
        val result = matrix.copyOf()
        for (row in rows) {
            result[row] = Array(matrix.size) { DD.ZERO }
            result[row][row] = DD.ONE
        }
        return result
    }

    fun applyRhs(rhs: DoubleArray) {
        for (i in rows.indices) {
            val row = rows[i]
            savedRhs[i] = rhs[row]
            rhs[row] = 0.0
        }
    }

    /** The gauge belongs to one stamped matrix. Repeated probes may reuse validation
     * only for an identical physical RHS, including equations discarded by the gauge.
     * A source voltage/current change therefore cannot reuse an obsolete result.
     */
    fun validProbe(physicalRhs: DoubleArray, solve: Function<DoubleArray, DoubleArray>): Boolean {
        for (i in rows.indices) savedRhs[i] = physicalRhs[rows[i]]
        if (lastProbeRhs?.contentEquals(physicalRhs) == true) return lastProbeValid
        if(probeRhsChanges<Int.MAX_VALUE)probeRhsChanges++
        val rhs = physicalRhs.copyOf()
        applyRhs(rhs)
        val accepted = valid(solve.apply(rhs))
        lastProbeRhs = physicalRhs.copyOf()
        lastProbeValid = accepted
        return accepted
    }

    private class ProbeRows(val columns: IntArray,val coefficients: Array<DoubleArray>,val solution: DoubleArray)
    private var probeRows: ProbeRows?=null
    // The subsystem is server-thread confined. Defer setup until repeated probes need it,
    // then use a plain local reference instead of a synchronized lazy getter in inner loops.
    private fun rowsForProbe(): ProbeRows {
        probeRows?.let { return it }
        val columns=discardedRows.flatMap { row->row.indices.filter { row[it]!=0.0 } }.distinct().sorted().toIntArray()
        val coefficients=if(columns.size<=8)Array(discardedRows.size) { row->DoubleArray(columns.size) { discardedRows[row][columns[it]] } } else emptyArray()
        return ProbeRows(columns,coefficients,DoubleArray(if(columns.size<=8)columns.size else 0)).also { probeRows=it }
    }
    private var compactColumns=IntArray(0)
    private var compactValues=DoubleArray(0)
    private var compactCount=-1
    private var compactValid=false

    val sparseProbeEligible get()=rowsForProbe().columns.size<=8

    /** Validate exactly the discarded KCL equations, using only their referenced
     * solution variables. The caller must first establish a finite bound for ALL
     * variables/intermediates; otherwise the full validation remains mandatory.
     * Dense discarded rows fall back instead of forming many inverse rows.
     */
    fun validProbeRows(physicalRhs: DoubleArray,response: IntFunction<Array<DD>>): Boolean? {
        val probeColumns=rowsForProbe().columns
        if(probeColumns.size>8)return null
        for(i in rows.indices)savedRhs[i]=physicalRhs[rows[i]]
        if(lastProbeRhs?.contentEquals(physicalRhs)==true)return lastProbeValid
        if(probeRhsChanges<Int.MAX_VALUE)probeRhsChanges++
        val solution=DoubleArray(physicalRhs.size)
        for(column in probeColumns) {
            val inverse=response.apply(column);var sum=DD.ZERO
            for(i in physicalRhs.indices)if(!referenceRows[i] && physicalRhs[i]!=0.0 && !inverse[i].isZero)
                sum=sum.add(inverse[i].multiply(physicalRhs[i]))
            val value=sum.toDouble()
            if(!value.isFinite())return null
            solution[column]=value
        }
        val accepted=valid(solution)
        lastProbeRhs=physicalRhs.copyOf();lastProbeValid=accepted
        return accepted
    }

    /** The caller collected ALL nonzero physical RHS entries while checking its finite
     * solve bound. Keep reference-row currents in this key too: an unbalanced floating
     * island must still fail. No dense solution vector or full RHS snapshot is needed.
     * This cache is independent of full-solve validation and belongs to this matrix only. */
    fun validProbeRowsCompact(physicalRhs: DoubleArray,nonzero: IntArray,count: Int,response: IntFunction<Array<DD>>): Boolean? {
        val data=rowsForProbe();val probeColumns=data.columns;val compactSolution=data.solution
        if(probeColumns.size>8)return null
        for(i in rows.indices)savedRhs[i]=physicalRhs[rows[i]]
        var identical=compactCount==count
        if(identical)for(i in 0 until count) {
            if(compactColumns[i]!=nonzero[i] || compactValues[i]!=physicalRhs[nonzero[i]]) { identical=false;break }
        }
        if(identical)return compactValid
        if(probeRhsChanges<Int.MAX_VALUE)probeRhsChanges++
        for(column in probeColumns.indices) {
            val inverse=response.apply(probeColumns[column]);var sum=DD.ZERO
            for(i in 0 until count) {
                val at=nonzero[i]
                if(!referenceRows[at] && !inverse[at].isZero)sum=sum.add(inverse[at].multiply(physicalRhs[at]))
            }
            val value=sum.toDouble()
            if(!value.isFinite())return null
            compactSolution[column]=value
        }
        var accepted=true
        for(i in rows.indices) {
            val coefficients=data.coefficients[i];var sum=0.0;var magnitude=0.0
            for(column in probeColumns.indices) {
                val term=coefficients[column]*compactSolution[column]
                sum+=term;magnitude+=abs(term)
            }
            val rhs=savedRhs[i]
            if(!(abs(sum-rhs)<=1e-10+1e-8*max(abs(rhs),magnitude))) { accepted=false;break }
        }
        if(compactColumns.size<count) { compactColumns=IntArray(count);compactValues=DoubleArray(count) }
        for(i in 0 until count) { compactColumns[i]=nonzero[i];compactValues[i]=physicalRhs[nonzero[i]] }
        compactCount=count;compactValid=accepted
        return accepted
    }

    /** Unit-current support depends on the matrix and port, never source commands.
     * Keep it separate from the changing physical-RHS validation cache. A new
     * matrix creates a new gauge, so opening a contact or changing a resistor
     * discards every cached result before another probe can use it.
     */
    fun supportsProbe(positive: Int, negative: Int, physicalRhs: DoubleArray,
                      solve: Function<DoubleArray, DoubleArray>): Boolean {
        val key = ((positive + 1).toLong() shl 32) or (negative + 1).toLong()
        try {
            return supportedExcitations.getOrPut(key) {
                val rhs = DoubleArray(physicalRhs.size)
                if (positive >= 0) rhs[positive] += 1.0
                if (negative >= 0) rhs[negative] -= 1.0
                applyRhs(rhs)
                valid(solve.apply(rhs))
            }
        } finally {
            for (i in rows.indices) savedRhs[i] = physicalRhs[rows[i]]
        }
    }

    fun valid(solution: DoubleArray): Boolean {
        if (solution.any { !it.isFinite() }) return false
        for (i in rows.indices) {
            val rhs = savedRhs[i]
            val coefficients = discardedRows[i]
            var sum = 0.0
            var magnitude = 0.0
            for (column in coefficients.indices) {
                val term = coefficients[column] * solution[column]
                sum += term
                magnitude += abs(term)
            }
            if (!(abs(sum - rhs) <= 1e-10 + 1e-8 * max(abs(rhs), magnitude))) return false
        }
        return true
    }
}
