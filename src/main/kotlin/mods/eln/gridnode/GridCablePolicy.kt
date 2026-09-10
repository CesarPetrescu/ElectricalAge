package mods.eln.gridnode

import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor

/** Compatibility of NEW overhead links, not an upgrade of a cable or a device's voltage rating.
 * Insulated utility cables use their jacket rating. Bare/legacy power cables use their nominal
 * gameplay rating; bare wire does not acquire insulation. Signal and damaged wire never qualify.
 * Do not use the historical poleEligible flag: it is also used for acquisition/tab categorisation
 * and omits the save-stable HV cable additions. Existing saved links are not deleted by this rule.
 */
object GridCablePolicy {
    const val MINIMUM_VOLTAGE = 1_000.0

    fun ratedVoltage(cable: ElectricalCableDescriptor): Double =
        if (cable is UtilityCableDescriptor && cable.insulated) cable.insulationVoltageRating
        else cable.electricalNominalVoltage

    fun accepts(cable: ElectricalCableDescriptor): Boolean = accepts(
        ratedVoltage(cable), cable.signalWire, cable is UtilityCableDescriptor && cable.melted
    )

    internal fun accepts(ratedVoltage: Double, signal: Boolean, damaged: Boolean): Boolean =
        !signal && !damaged && ratedVoltage.isFinite() && ratedVoltage >= MINIMUM_VOLTAGE
}
