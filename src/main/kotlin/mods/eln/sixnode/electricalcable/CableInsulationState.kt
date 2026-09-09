package mods.eln.sixnode.electricalcable

import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.state.State
import mods.eln.sim.power.*
import net.minecraft.nbt.CompoundTag

/** A latched, finite-resistance fault between the actually overstressed pair, or to earth.
 * Ten ohms is an explicit GAME arc approximation, not an electric-arc simulation.
 * The owner registers the returned branch once and includes its measured heat in its wire load.
 */
class CableInsulationState {
    companion object { const val FAULT_OHMS = 10.0 }
    private var damage = InsulationDamage()
    private var branch: Resistor? = null
    val fault get() = damage.fault
    val exposure get() = damage.exposure
    val faultPower get() = branch?.power ?: 0.0

    fun commit(voltages: DoubleArray, rating: Double, seconds: Double): Boolean {
        if (damage.fault != null || !rating.isFinite() || rating <= 0 || voltages.any { !it.isFinite() }) return false
        val stress = worstInsulationStress(voltages, InsulationClass("installed", rating, rating))
        damage.commit(stress, seconds)
        return damage.fault != null
    }

    fun connection(cores: Array<out State>): Resistor? {
        val f = fault ?: return null
        if (branch == null) {
            require(f.fromCore in cores.indices && (f.toCore == null || f.toCore in cores.indices))
            branch = Resistor(cores[f.fromCore], f.toCore?.let { cores[it] }).setResistance(FAULT_OHMS)
        }
        return branch
    }

    fun writeNbt(nbt: CompoundTag) {
        nbt.putDouble("insulationExposure", exposure)
        val f = fault
        nbt.putBoolean("insulationFailed", f != null)
        if (f != null) {
            nbt.putInt("insulationFrom", f.fromCore)
            nbt.putInt("insulationTo", f.toCore ?: -1)
            nbt.putDouble("insulationFaultVolts", f.volts)
            nbt.putDouble("insulationFaultRating", f.rating)
        }
    }

    /** Called on a newly loaded element, before it owns live MNA components. */
    fun readNbt(nbt: CompoundTag, coreCount: Int, rating: Double) {
        require(coreCount > 0)
        check(branch == null) { "Cannot replace a live insulation fault" }
        val exposure = nbt.getDouble("insulationExposure").takeIf { it.isFinite() && it >= 0 } ?: 0.0
        val f = if (nbt.getBoolean("insulationFailed")) {
            val from = nbt.getInt("insulationFrom").coerceIn(0, coreCount - 1)
            val rawTo = nbt.getInt("insulationTo")
            val to = rawTo.takeIf { it in 0 until coreCount && it != from }
            val volts = nbt.getDouble("insulationFaultVolts").takeIf { it.isFinite() && it >= 0 } ?: 0.0
            val voltsRating = nbt.getDouble("insulationFaultRating").takeIf { it.isFinite() && it > 0 }
                ?: rating.takeIf { it.isFinite() && it > 0 } ?: 1.0
            // Invalid saved endpoints are conservatively an earth fault, never silently repaired.
            InsulationStress(from, to, volts, voltsRating)
        } else null
        damage = InsulationDamage(exposure, f)
    }
}
