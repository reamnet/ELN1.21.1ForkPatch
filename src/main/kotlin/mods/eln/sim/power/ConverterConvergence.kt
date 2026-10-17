package mods.eln.sim.power

import mods.eln.sim.mna.SubSystem
import kotlin.math.abs
import kotlin.math.max

/** Bounded Newton correction of the existing controller fixed point, NOT an electrical model.
 * On a parallel current-limited bus, sequential voltage updates have a nearly unit common-mode
 * eigenvalue: each converter sees the other converters as stiff supplies. More iterations alone
 * therefore do not solve startup/overload reliably. Correct all commands in a connected group
 * together. Every trial still runs the real controller and must satisfy its unchanged limits and
 * power balance before RootSystem performs the one physical flush.
 */
class ConverterConvergence {
    private data class Derivative(val sources: List<mods.eln.sim.mna.component.SwitchableVoltageSource>,
        val scales: DoubleArray, val regions: List<String>, val matrix: Array<DoubleArray>)
    private val derivatives = mutableMapOf<List<ConservativePowerProcess>, Derivative>()
    fun clear() = derivatives.clear()

    /** A Newton tangent inside a constant-current plateau can point away from every
     * finite solution. Try the actual source-law breakpoints on that electrical
     * network, then run the unchanged controllers and acceptance checks again.
     */
    fun restartAtSourceBoundary(processes: List<ConservativePowerProcess>): Boolean {
        var accepted = true
        for (group in connectedGroups(processes)) {
            if (group.any { !it.acceptsCandidate() } && !restartGroupAtSourceBoundary(group)) accepted = false
        }
        return accepted
    }

    private fun restartGroupAtSourceBoundary(processes: List<ConservativePowerProcess>): Boolean {
        val all = processes.flatMap { it.trialSources() }.distinct()
        val saved = all.map { it.voltage }
        val enabled = all.map { it.enabled }
        val systems = processes.flatMap { it.connectedSystems() }.distinct()
        fun restore() {
            all.indices.forEach { all[it].voltage = saved[it]; all[it].enabled = enabled[it] }
        }
        val candidates = processes.flatMap { it.voltageSearchHints().entries }.flatMap { (source, hints) ->
            hints.filter { it.isFinite() && it > 0.0 && source.voltage.isFinite() && source.voltage > 0.0 }
                .map { source to it }
        }.distinctBy { (source, hint) -> source.subSystem to hint }
            .sortedBy { (source, hint) -> abs(kotlin.math.ln(hint / source.voltage)) }
        for ((source, hint) in candidates.take(8)) {
            val sourceIndex = all.indexOf(source)
            if (sourceIndex < 0 || !enabled[sourceIndex] || saved[sourceIndex] <= 0.0) continue
            val network = source.subSystem ?: continue
            restore()
            val scale = hint / saved[sourceIndex]
            for (i in all.indices) if (enabled[i] && all[i].subSystem === network) {
                all[i].voltage = saved[i] * scale
            }
            derivatives.remove(processes.toList())
            repeat(2) {
                processes.forEach { it.rootSystemPreStepProcess() }
                systems.forEach { it.stepCalc() }
                if (processes.all { it.acceptsCandidate() }) return true
                correctGroup(processes)
                systems.forEach { it.stepCalc() }
                if (processes.all { it.acceptsCandidate() }) return true
            }
        }
        restore()
        derivatives.remove(processes.toList())
        systems.forEach { it.stepCalc() }
        return false
    }

    fun correct(processes: List<ConservativePowerProcess>) {
        val groups = connectedGroups(processes)
        derivatives.keys.retainAll(groups.map { it.toList() }.toSet())
        for (group in groups) if (group.any { !it.acceptsCandidate() }) correctGroup(group)
    }

    private fun connectedGroups(processes: List<ConservativePowerProcess>): List<List<ConservativePowerProcess>> {
        // Do not combine unrelated networks into one dense numerical correction or restart.
        val groups = mutableListOf<MutableList<ConservativePowerProcess>>()
        val scopes = mutableListOf<MutableSet<SubSystem>>()
        for (process in processes) {
            val scope = process.connectedSystems().toMutableSet()
            val group = mutableListOf(process)
            var i = 0
            while (i < groups.size) {
                if (scopes[i].any { it in scope }) {
                    scope.addAll(scopes.removeAt(i)); group.addAll(groups.removeAt(i)); i = 0
                } else i++
            }
            groups += group; scopes += scope
        }
        return groups
    }

    private fun correctGroup(group: List<ConservativePowerProcess>) {
        val all = group.flatMap { it.trialSources() }.distinct()
        val active = all.filter { it.enabled }
        if (active.isEmpty()) return
        val savedVolts = all.map { it.voltage }
        val savedEnabled = all.map { it.enabled }
        val key = group.toList()
        var cached = derivatives[key]?.takeIf { it.sources == active && it.regions == group.map { it.iterationRegion() } }
        val scales = cached?.scales ?: DoubleArray(active.size) { max(1.0, abs(active[it].voltage)) }
        var x = DoubleArray(active.size) { active[it].voltage / scales[it] }

        fun evaluate(candidate: DoubleArray): DoubleArray? {
            for (i in all.indices) { all[i].voltage = savedVolts[i]; all[i].enabled = savedEnabled[i] }
            for (i in active.indices) active[i].voltage = candidate[i] * scales[i]
            group.forEach { it.rootSystemPreStepProcess() }
            if (all.indices.any { all[it].enabled != savedEnabled[it] }) return null
            val residual = DoubleArray(active.size) { active[it].voltage / scales[it] - candidate[it] }
            return residual.takeIf { it.all(Double::isFinite) }
        }
        fun norm(r: DoubleArray) = r.maxOf { abs(it) }
        fun accepted(): Boolean {
            group.flatMap { it.connectedSystems() }.distinct().forEach { it.stepCalc() }
            return group.all { it.acceptsCandidate() }
        }

        repeat(8) { iteration ->
            val residual = evaluate(x) ?: return // Let ordinary iteration handle an active-set change.
            if (accepted()) return
            val regions = group.map { it.iterationRegion() }
            // Reuse a previous derivative for nearby operating points (e.g. wind speed or
            // wire temperature changes). Every candidate still passes the real physics checks;
            // a changed limiter/mask or failed improvement rebuilds the derivative normally.
            val reuse = cached?.takeIf { it.regions == regions && iteration < 3 }
            val jacobian = reuse?.matrix?.map { it.clone() }?.toTypedArray()
                ?: Array(x.size) { DoubleArray(x.size) }
            if (reuse == null) for (column in x.indices) {
                // Stay on the same limiter branch when differentiating. Near the
                // transition from voltage regulation to current limit, a forward
                // stencil may cross the target clamp and erase the bus's common-mode
                // derivative. Try both directions, then shrink the stencil.
                var difference = 0.0
                var response: DoubleArray? = null
                var h = 1e-5 * max(1.0, abs(x[column]))
                repeat(5) {
                    if (response == null) {
                        for (direction in doubleArrayOf(1.0, -1.0)) {
                            val signed = direction * h
                            val probe = evaluate(x.copyOf().apply { this[column] += signed })
                            if (probe != null && group.map { it.iterationRegion() } == regions) {
                                response = probe; difference = signed; break
                            }
                        }
                        h *= .1
                    }
                }
                if (response == null) { evaluate(x); return }

                for (row in x.indices) jacobian[row][column] = (response!![row] - residual[row]) / difference
            }
            if (reuse == null) {
                cached = Derivative(active.toList(), scales.clone(), regions,
                    jacobian.map { it.clone() }.toTypedArray())
                derivatives[key] = cached!!
            }
            val delta = solve(jacobian, DoubleArray(x.size) { -residual[it] })
            if (delta == null) { evaluate(x); return }
            // Trust region and backtracking prevent an ill-conditioned Jacobian from publishing
            // enormous speculative commands. This bound changes no accepted device rating.
            var alpha = minOf(1.0, 4.0 / max(4.0, delta.maxOf { abs(it) }))
            var improved = false
            repeat(9) {
                if (!improved) {
                    val candidate = DoubleArray(x.size) { x[it] + alpha * delta[it] }
                    val next = evaluate(candidate)
                    if (next == null) return // Preserve the new active set for the outer iteration.
                    if (norm(next) < norm(residual) || accepted()) { x = candidate; improved = true }
                    else alpha *= .5
                }
            }
            if (!improved) {
                evaluate(x)
                if (reuse == null) return
                cached = null; derivatives.remove(key)
            }
        }
        evaluate(x)
    }

    /** Small dense command-space solve with pivoting. The electrical matrix is not modified. */
    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        for (column in 0 until n) {
            val pivot = (column until n).maxByOrNull { abs(a[it][column]) } ?: return null
            if (!a[pivot][column].isFinite() || abs(a[pivot][column]) < 1e-13) return null
            val row = a[column]; a[column] = a[pivot]; a[pivot] = row
            val rhs = b[column]; b[column] = b[pivot]; b[pivot] = rhs
            for (i in column + 1 until n) {
                val factor = a[i][column] / a[column][column]
                for (j in column + 1 until n) a[i][j] -= factor * a[column][j]
                b[i] -= factor * b[column]
            }
        }
        val x = DoubleArray(n)
        for (i in n - 1 downTo 0) {
            var sum = b[i]
            for (j in i + 1 until n) sum -= a[i][j] * x[j]
            x[i] = sum / a[i][i]
        }
        return x.takeIf { it.all(Double::isFinite) }
    }
}
