package mods.eln.sixnode.electricalcable

/** Explicit append-only six-node identities. These are GAME insulation classes, not certifications.
 * Group 37 was reserved by registerUtilityCables; the legacy catalogue uses only through 2327.
 * Never derive an item id from list position or insert these into the legacy allocation loops.
 */
data class HvCableSpec(
    val descriptorId: Int,
    val gauge: String,
    val areaMm2: Double,
    val amps: Double,
    val volts: Double,
    val rubberMultiplier: Double
)

object HvCableSpecifications {
    val entries = listOf(
        HvCableSpec(2368, "12 AWG", 3.309, 20.0, 1_000.0, 2.0),
        HvCableSpec(2369, "12 AWG", 3.309, 20.0, 5_000.0, 4.0),
        HvCableSpec(2370, "12 AWG", 3.309, 20.0, 20_000.0, 8.0),
        HvCableSpec(2371, "12 AWG", 3.309, 20.0, 40_000.0, 12.0),
        HvCableSpec(2372, "12 AWG", 3.309, 20.0, 150_000.0, 24.0),
        HvCableSpec(2373, "8 AWG", 8.366, 40.0, 1_000.0, 2.0),
        HvCableSpec(2374, "8 AWG", 8.366, 40.0, 5_000.0, 4.0),
        HvCableSpec(2375, "8 AWG", 8.366, 40.0, 20_000.0, 8.0),
        HvCableSpec(2376, "8 AWG", 8.366, 40.0, 40_000.0, 12.0),
        HvCableSpec(2377, "8 AWG", 8.366, 40.0, 150_000.0, 24.0),
        HvCableSpec(2378, "2 AWG", 33.631, 100.0, 1_000.0, 2.0),
        HvCableSpec(2379, "2 AWG", 33.631, 100.0, 5_000.0, 4.0),
        HvCableSpec(2380, "2 AWG", 33.631, 100.0, 20_000.0, 8.0),
        HvCableSpec(2381, "2 AWG", 33.631, 100.0, 40_000.0, 12.0),
        HvCableSpec(2382, "2 AWG", 33.631, 100.0, 150_000.0, 24.0)
    )
    init {
        check(entries.map { it.descriptorId }.distinct().size == entries.size)
        check(entries.map { it.gauge to it.volts }.distinct().size == entries.size)
        check(entries.all { it.areaMm2 > 0 && it.amps > 0 && it.volts > 0 && it.rubberMultiplier >= 1 })
    }
}
