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
object ConverterConvergence {
    @JvmStatic fun correct(processes: List<ConservativePowerProcess>) {
        // Do not combine unrelated networks into one dense numerical correction.
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
        for (group in groups) if (group.any { !it.acceptsCandidate() }) correctGroup(group)
    }

    private fun correctGroup(group: List<ConservativePowerProcess>) {
        val all = group.flatMap { it.trialSources() }.distinct()
        val active = all.filter { it.enabled }
        if (active.isEmpty()) return
        val savedVolts = all.map { it.voltage }
        val savedEnabled = all.map { it.enabled }
        val scales = DoubleArray(active.size) { max(1.0, abs(active[it].voltage)) }
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

        repeat(8) {
            val residual = evaluate(x) ?: return // Let ordinary iteration handle an active-set change.
            if (accepted()) return
            val jacobian = Array(x.size) { DoubleArray(x.size) }
            for (column in x.indices) {
                val h = 1e-5 * max(1.0, abs(x[column]))
                val perturbed = x.copyOf().apply { this[column] += h }
                var difference = h
                var response = evaluate(perturbed)
                if (response == null) {
                    perturbed[column] = x[column] - h
                    difference = -h
                    response = evaluate(perturbed)
                }
                if (response == null) { evaluate(x); return }
                for (row in x.indices) jacobian[row][column] = (response[row] - residual[row]) / difference
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
            if (!improved) { evaluate(x); return }
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
