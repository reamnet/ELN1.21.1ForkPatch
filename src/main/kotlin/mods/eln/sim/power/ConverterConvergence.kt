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
    private val localRestores = !java.lang.Boolean.getBoolean("eln.disableLocalConverterRestores")
    private data class Derivative(val sources: List<mods.eln.sim.mna.component.SwitchableVoltageSource>,
        val scales: DoubleArray, val weights: DoubleArray, val regions: List<String>, val matrix: Array<DoubleArray>)
    private val derivatives = mutableMapOf<List<ConservativePowerProcess>, Derivative>()
    fun clear() = derivatives.clear()

    /** Move stiff source and converter-input guesses together while a source
     * starts. This may also lower a backfed idle renewable winding to its nominal
     * startup guess. Otherwise a valid constant-current / constant-power low
     * branch can latch startup even though a normal-voltage solution exists. */
    fun seedStartupVoltages(processes: List<ConservativePowerProcess>) {
        val requests = processes.flatMap { it.startupVoltageTargets().entries }
            .filter { (source, target) -> source.enabled && source.voltage > 0.0 &&
                target.isFinite() && target > 0.0 }
        if (requests.isEmpty()) return
        val sources = processes.flatMap { it.controlledSources() }.distinct()
        val touched = mutableSetOf<SubSystem>()
        val propagated = mutableSetOf<ConservativePowerProcess>()
        var pending = requests
        while (pending.isNotEmpty()) {
            for ((system, busRequests) in pending.groupBy { it.key.subSystem }) {
                if (system == null || !touched.add(system)) continue
                val request = busRequests.maxWith(compareBy<Map.Entry<mods.eln.sim.mna.component.SwitchableVoltageSource, Double>> { it.value }
                    .thenBy { it.key.voltage })
                val bus = sources.filter { it.enabled && it.subSystem === system }
                val anchor = if (request.key.enabled) request.key else bus.filter { it.voltage > 0.0 && it.voltage.isFinite() }
                    .maxByOrNull { it.voltage }
                if (anchor != null && anchor.voltage > 0.0 && anchor.voltage.isFinite()) {
                    val scale = request.value / anchor.voltage
                    if (!scale.isFinite() || bus.any { !(it.voltage * scale).isFinite() }) continue
                    for (source in bus) source.voltage *= scale
                }
                // A disabled converter output has no electrical effect; initialize
                // its command without enabling it or bypassing its power check.
                request.key.voltage = request.value
            }
            pending = processes.filter { it !in propagated &&
                it.startupInputSources().any { source -> source.subSystem in touched } }
                .flatMap { process ->
                    propagated.add(process)
                    process.startupOutputTargets().entries.filter { it.value.isFinite() && it.value > 0.0 }
                }
        }
    }

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
        val all = processes.flatMap { it.controlledSources() }.distinct()
        val saved = all.map { it.voltage }
        val enabled = all.map { it.enabled }
        val systems = processes.flatMap { it.connectedSystems() }.distinct()
        fun restore() {
            all.indices.forEach { all[it].voltage = saved[it]; all[it].enabled = enabled[it] }
        }
        val hints = processes.flatMap { it.voltageSearchHints().entries }.flatMap { (source, hints) ->
            hints.filter { source.enabled && it.isFinite() && it > 0.0 && source.voltage.isFinite() && source.voltage > 0.0 }
                .map { source to it }
        }.sortedBy { (source, hint) -> abs(kotlin.math.ln(hint / source.voltage)) }
            .distinctBy { (source, hint) -> source.subSystem to hint }
        // Several isolated wind inputs can reach the same limiter together while
        // their converters share an output bus. Restart those input guesses together;
        // trying one at a time leaves the other stiff clamps on the rejected branch.
        val coordinated = hints.groupBy { it.second }.values.filter { it.size > 1 }
        val candidates = coordinated + hints.map { listOf(it) }
        for (candidate in candidates.take(8)) {
            restore()
            for ((source, hint) in candidate) {
                val sourceIndex = all.indexOf(source)
                if (sourceIndex < 0 || !enabled[sourceIndex] || saved[sourceIndex] <= 0.0) continue
                val network = source.subSystem ?: continue
                val scale = hint / saved[sourceIndex]
                for (i in all.indices) if (enabled[i] && all[i].subSystem === network) {
                    all[i].voltage = saved[i] * scale
                }
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
        // Retain the serial correction for asymmetric active sets (for example,
        // parallel regulators with different targets). Use the simultaneous
        // response when bounded renewable clamps stall that correction. Ordinary
        // shaft/pole grids keep the existing first-order serial path.
        val systems=group.flatMap { it.connectedSystems() }.distinct()
        systems.forEach { it.beginPortProjection() }
        try {
            correctGroup(group, simultaneous = false)
            systems.forEach { it.stepCalc() }
            if (group.any { !it.acceptsCandidate() } && group.any { it.trialVoltageBounds().isNotEmpty() })
                correctGroup(group, simultaneous = true)
        } finally {
            systems.forEach { it.endPortProjection() }
        }
    }

    private fun correctGroup(group: List<ConservativePowerProcess>, simultaneous: Boolean) {
        val all = group.flatMap { it.controlledSources() }.distinct()
        val active = group.flatMap { it.trialSources() }.distinct().filter { it.enabled }
        if (active.isEmpty()) return
        val savedVolts = all.map { it.voltage }
        val savedEnabled = all.map { it.enabled }
        val key = group.toList()
        // The two response maps have different derivatives.
        if (simultaneous) derivatives.remove(key)
        var cached = derivatives[key]?.takeIf { it.sources == active && it.regions == group.map { it.iterationRegion() } }
        val scales = cached?.scales ?: DoubleArray(active.size) { max(1.0, abs(active[it].voltage)) }
        var x = DoubleArray(active.size) { active[it].voltage / scales[it] }
        val preciseDerivative = group.any { it.trialVoltageBounds().isNotEmpty() }
        // A microvolt error on a stiff HV port can mean kilowatts of current
        // imbalance. Use a power-scaled merit function so line search does not
        // ignore those ports in favour of larger voltage errors on weak inputs.
        val weights = cached?.weights ?: DoubleArray(active.size) { i ->
            val resistance = if (preciseDerivative) active[i].subSystem?.sourceThevenin(active[i])?.resistance else null
            if (resistance != null && resistance.isFinite() && resistance > 0.0)
                max(1.0, scales[i] * scales[i] / (1000.0 * resistance)).takeIf { it.isFinite() } ?: 1.0
            else 1.0
        }

        val indexes = all.withIndex().associate { it.value to it.index }
        val activeIndexes = active.map { indexes.getValue(it) }
        val owners = group.map { process -> process.controlledSources().map { indexes.getValue(it) } }
        val systems = group.flatMap { it.connectedSystems() }.distinct()
        fun sameRegions(regions: List<String>) = group.indices.all { group[it].iterationRegion() == regions[it] }
        val bounds = group.flatMap { it.trialVoltageBounds().entries }.associate { it.key to it.value }
        fun trialVoltage(i: Int, normalized: Double): Double {
            val volts = normalized * scales[i]
            val range = bounds[active[i]] ?: return volts
            return volts.coerceIn(range.start, range.endInclusive)
        }
        fun evaluate(candidate: DoubleArray): DoubleArray? {
            if (!simultaneous) {
                for (i in all.indices) { all[i].voltage = savedVolts[i]; all[i].enabled = savedEnabled[i] }
                for (i in active.indices) active[i].voltage = trialVoltage(i, candidate[i])
                group.forEach { it.rootSystemPreStepProcess() }
                if (all.indices.any { all[it].enabled != savedEnabled[it] }) return null
                return DoubleArray(active.size) { (active[it].voltage / scales[it] - candidate[it]) * weights[it] }
                    .takeIf { it.all(Double::isFinite) }
            }
            val trialVolts = savedVolts.toDoubleArray()
            for (i in active.indices) trialVolts[activeIndexes[i]] = trialVoltage(i, candidate[i])
            val nextVolts = trialVolts.clone()
            val nextEnabled = savedEnabled.toBooleanArray()
            // Differentiate a simultaneous controller response. Sequential updates
            // let each later controller see an already perturbed/clamped neighbour;
            // near parallel source limits that erases the useful bus derivative.
            if (localRestores) for (i in all.indices) { all[i].voltage = trialVolts[i]; all[i].enabled = savedEnabled[i] }
            for (p in group.indices) {
                if (!localRestores) for (i in all.indices) { all[i].voltage = trialVolts[i]; all[i].enabled = savedEnabled[i] }
                group[p].rootSystemPreStepProcess()
                for (i in owners[p]) { nextVolts[i] = all[i].voltage; nextEnabled[i] = all[i].enabled }
                // A participant only changes its controlledSources. Restore that
                // owner before the next trial, rather than rewriting the entire
                // network once per participant (quadratic on a large source bank).
                if (localRestores) for (i in owners[p]) { all[i].voltage = trialVolts[i]; all[i].enabled = savedEnabled[i] }
            }
            for (i in all.indices) { all[i].voltage = nextVolts[i]; all[i].enabled = nextEnabled[i] }
            if (all.indices.any { all[it].enabled != savedEnabled[it] }) {
                // Simultaneously opening neighbours invalidates an OUTPUT_ALREADY_HIGH
                // decision. Reconcile the new mask before RootSystem can accept it.
                group.forEach { it.rootSystemPreStepProcess() }
                return null
            }
            val residual = DoubleArray(active.size) { (active[it].voltage / scales[it] - candidate[it]) * weights[it] }
            return residual.takeIf { it.all(Double::isFinite) }
        }
        fun norm(r: DoubleArray) = r.maxOf { abs(it) }
        fun accepted(): Boolean {
            systems.forEach { it.stepCalc() }
            return group.all { it.acceptsCandidate() }
        }

        repeat(8) { iteration ->
            val residual = evaluate(x) ?: return // Reconcile an active-set change in the outer iteration.
            if (accepted()) return
            val regions = group.map { it.iterationRegion() }
            // Reuse a previous derivative for nearby operating points (e.g. wind speed or
            // wire temperature changes). Every candidate still passes the real physics checks;
            // a changed limiter/mask or failed improvement rebuilds the derivative normally.
            val reuse = cached?.takeIf { it.regions == regions && iteration < 3 }
            val jacobian = reuse?.matrix?.map { it.clone() }?.toTypedArray()
                ?: Array(x.size) { DoubleArray(x.size) }
            if (reuse == null) for (column in x.indices) {
                if (preciseDerivative) {
                    // A source bus can have a common-mode derivative far smaller
                    // than the entries of its command map. First-order truncation
                    // can swamp that derivative even with a precise MNA solve.
                    var derivative: DoubleArray? = null
                    var h = 1e-5 * max(1.0, abs(x[column]))
                    fun sample(shift: Double): DoubleArray? = evaluate(x.copyOf().apply { this[column] += shift })
                        ?.takeIf { sameRegions(regions) }
                    repeat(5) {
                        if (derivative == null) {
                            val plus = sample(h); val minus = sample(-h)
                            if (plus != null && minus != null) {
                                derivative = DoubleArray(x.size) { (plus[it] - minus[it]) / (2 * h) }
                            } else {
                                val sign = if (plus != null) 1.0 else -1.0
                                val first = plus ?: minus
                                val second = if (first != null) sample(2 * sign * h) else null
                                if (first != null && second != null) derivative = DoubleArray(x.size) {
                                    (-3 * residual[it] + 4 * first[it] - second[it]) / (2 * sign * h)
                                }
                            }
                            h *= .1
                        }
                    }
                    val columnValues = derivative ?: run { evaluate(x); return }
                    for (row in x.indices) jacobian[row][column] = columnValues[row]
                    continue
                }
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
                            if (probe != null && sameRegions(regions)) {
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
                cached = Derivative(active.toList(), scales.clone(), weights.clone(), regions,
                    jacobian.map { it.clone() }.toTypedArray())
                if (!simultaneous) derivatives[key] = cached!!
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
                    if (next == null) {
                        if (!preciseDerivative) return
                        alpha *= .5
                    } else if (norm(next) < norm(residual) || accepted()) { x = candidate; improved = true }
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
