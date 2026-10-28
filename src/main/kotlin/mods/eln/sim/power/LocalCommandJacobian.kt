package mods.eln.sim.power

import mods.eln.sim.mna.component.SwitchableVoltageSource
import kotlin.math.abs
import kotlin.math.max

/** Chain local controller derivatives instead of replaying the entire grid per column.
 * This differentiates command guesses only. The ordinary evaluator and physical acceptance
 * checks still decide every Newton step. Unknown or changing controller branches fall back.
 */
internal object LocalCommandJacobian {
    fun build(group: List<ConservativePowerProcess>, all: List<SwitchableVoltageSource>,
              active: List<SwitchableVoltageSource>, scales: DoubleArray, weights: DoubleArray,
              x: DoubleArray, simultaneous: Boolean,
              trialVoltage: (Int, Double) -> Double): Array<DoubleArray>? {
        if (group.any { !it.supportsLocalCommandDerivative() }) return null
        val commands = all.toTypedArray()
        val indexes = all.withIndex().associate { it.value to it.index }
        val enabled = BooleanArray(all.size) { all[it].enabled }
        val trial = DoubleArray(all.size) { all[it].voltage }
        for (i in active.indices) trial[indexes.getValue(active[i])] = trialVoltage(i, x[i])
        val initial = Array(all.size) { DoubleArray(active.size) }
        for (i in active.indices) {
            val h = 1e-5 * max(1.0, abs(x[i]))
            val plus = trialVoltage(i, x[i] + h)
            val minus = trialVoltage(i, x[i] - h)
            initial[indexes.getValue(active[i])][i] =
                if (plus == (x[i] + h) * scales[i] && minus == (x[i] - h) * scales[i]) scales[i]
                else (plus - minus) / (2 * h)
        }
        val derivative = initial.map { it.clone() }.toTypedArray()
        val next = trial.clone()
        fun restore(values: DoubleArray) {
            for (i in all.indices) { all[i].voltage = values[i]; all[i].enabled = enabled[i] }
        }
        restore(trial)
        for (process in group) {
            if (simultaneous) restore(trial)
            val incoming = DoubleArray(all.size) { all[it].voltage }
            val owned = process.controlledSources().map { indexes.getValue(it) }
            process.rootSystemPreStepProcess()
            if (all.indices.any { all[it].enabled != enabled[it] }) return null
            val base = DoubleArray(owned.size) { all[owned[it]].voltage }
            val region = process.iterationRegion()
            restore(incoming)
            val local = process.localCommandDerivative(commands) ?: run {
                val rows = Array(owned.size) { DoubleArray(all.size) }
                val scope = process.connectedSystems()
                for (column in all.indices) {
                    // Opted-in controllers only probe their declared electrical systems.
                    if (!enabled[column] || all[column].subSystem !in scope) continue
                    var values: DoubleArray? = null
                    var h = 1e-5 * max(1.0, abs(incoming[column]))
                    fun sample(shift: Double): DoubleArray? {
                        restore(incoming)
                        all[column].voltage = incoming[column] + shift
                        process.rootSystemPreStepProcess()
                        if (all.indices.any { all[it].enabled != enabled[it] } || process.iterationRegion() != region)
                            return null
                        return DoubleArray(owned.size) { all[owned[it]].voltage }.takeIf { it.all(Double::isFinite) }
                    }
                    repeat(5) {
                        if (values == null) {
                            val plus = sample(h); val minus = sample(-h)
                            if (plus != null && minus != null)
                                values = DoubleArray(owned.size) { (plus[it] - minus[it]) / (2 * h) }
                            else {
                                val sign = if (plus != null) 1.0 else -1.0
                                val first = plus ?: minus
                                val second = if (first != null) sample(2 * sign * h) else null
                                if (first != null && second != null) values = DoubleArray(owned.size) {
                                    (-3 * base[it] + 4 * first[it] - second[it]) / (2 * sign * h)
                                }
                            }
                            h *= .1
                        }
                    }
                    val columnValues = values ?: return null
                    for (row in owned.indices) rows[row][column] = columnValues[row]
                }
                rows
            }
            if (local.size != owned.size || local.any { it.size != all.size || it.any { v -> !v.isFinite() } })
                return null
            val upstream = if (simultaneous) initial else derivative
            val composed = Array(owned.size) { DoubleArray(active.size) }
            for (row in owned.indices) for (k in all.indices) {
                val slope = local[row][k]
                if (slope == 0.0) continue
                for (column in active.indices) composed[row][column] += slope * upstream[k][column]
            }
            for (row in owned.indices) {
                derivative[owned[row]] = composed[row]
                next[owned[row]] = base[row]
            }
            // Restore diagnostics and commands through the real controller before proceeding.
            restore(incoming)
            process.rootSystemPreStepProcess()
            if (process.iterationRegion() != region || all.indices.any { all[it].enabled != enabled[it] } ||
                owned.indices.any { all[owned[it]].voltage != base[it] }) return null
        }
        if (simultaneous) restore(next)
        return Array(active.size) { row ->
            val d = derivative[indexes.getValue(active[row])]
            DoubleArray(active.size) { column ->
                (d[column] / scales[row] - if (row == column) 1.0 else 0.0) * weights[row]
            }
        }.takeIf { rows -> rows.all { row -> row.all(Double::isFinite) } }
    }
}
