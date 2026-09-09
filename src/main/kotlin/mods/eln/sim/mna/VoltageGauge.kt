package mods.eln.sim.mna

import org.apache.commons.numbers.core.DD
import mods.eln.sim.mna.state.State
import mods.eln.sim.mna.state.VoltageState
import kotlin.math.abs
import kotlin.math.max

/** Removes only an additive voltage reference freedom. No leakage current is invented.
 * Each discarded KCL equation is checked after solving; an unbalanced current source into
 * a floating island is therefore rejected rather than absorbed by a fictitious ground.
 */
class VoltageGauge(matrix: Array<DoubleArray>, states: List<State>) {
    private val original = matrix.map { it.copyOf() }
    private val rows = mutableListOf<Int>()
    private val savedRhs = mutableMapOf<Int, Double>()

    init {
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
            val floating = matrix.all { row ->
                val sum = group.sumOf { row[it] }
                val scale = group.sumOf { abs(row[it]) }
                abs(sum) <= scale * 1e-12
            }
            if (floating) {
                val pin = group.first()
                rows += pin
                matrix[pin].fill(0.0)
                matrix[pin][pin] = 1.0
            }
        }
    }

    val active: Boolean get() = rows.isNotEmpty()

    /** Apply the same reference choice to the precision-preserving solve matrix. */
    fun applyMatrix(matrix: Array<Array<DD>>) {
        for (row in rows) {
            matrix[row].fill(DD.ZERO)
            matrix[row][row] = DD.ONE
        }
    }

    fun applyRhs(rhs: DoubleArray) {
        savedRhs.clear()
        for (row in rows) {
            savedRhs[row] = rhs[row]
            rhs[row] = 0.0
        }
    }

    fun valid(solution: DoubleArray): Boolean {
        if (solution.any { !it.isFinite() }) return false
        return rows.all { row ->
            val rhs = savedRhs[row] ?: 0.0
            val terms = original[row].indices.map { original[row][it] * solution[it] }
            abs(terms.sum() - rhs) <= 1e-10 + 1e-8 * max(abs(rhs), terms.sumOf { abs(it) })
        }
    }
}
