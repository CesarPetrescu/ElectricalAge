package mods.eln.sim.power

import kotlin.math.pow

enum class ConverterKind { VARIABLE, BOOST, BUCK, BUCK_BOOST, ISOLATION }
enum class MappingVersion { LEGACY_LINEAR_V1, LOGARITHMIC_V2 }

/** New installations opt into V2. Missing saved mapping MUST load as LEGACY_LINEAR_V1. */
object ControlMapping {
    fun ratio(kind: ConverterKind, normalized: Double, version: MappingVersion, span: Double = 256.0): Double {
        require(normalized.isFinite()) { "A non-finite control signal is a fault, not a normal ratio." }
        require(span.isFinite() && span > 1)
        val x = normalized.coerceIn(0.0, 1.0)
        if (version == MappingVersion.LEGACY_LINEAR_V1) return when (kind) {
            ConverterKind.VARIABLE -> 1.0 / 256 + x * (256 - 1.0 / 256)
            ConverterKind.BOOST -> 1 + 49 * x
            ConverterKind.BUCK -> 1.0 / 50 + x * (1 - 1.0 / 50)
            ConverterKind.BUCK_BOOST -> if (x < .5) 1.0 / 50 + 2 * x * (1 - 1.0 / 50)
                else 1 + 2 * (x - .5) * 49
            ConverterKind.ISOLATION -> 1.0
        }
        return when (kind) {
            ConverterKind.VARIABLE, ConverterKind.BUCK_BOOST -> span.pow(2 * x - 1)
            ConverterKind.BOOST -> span.pow(x)
            ConverterKind.BUCK -> span.pow(x - 1)
            ConverterKind.ISOLATION -> 1.0
        }
    }
}

/** Explicit serialized names, never enum ordinals. Unknown future versions fail closed. */
fun loadMapping(savedName: String?): MappingVersion = when (savedName) {
    null -> MappingVersion.LEGACY_LINEAR_V1
    "LEGACY_LINEAR_V1" -> MappingVersion.LEGACY_LINEAR_V1
    "LOGARITHMIC_V2" -> MappingVersion.LOGARITHMIC_V2
    else -> error("Unsupported converter mapping: $savedName")
}
