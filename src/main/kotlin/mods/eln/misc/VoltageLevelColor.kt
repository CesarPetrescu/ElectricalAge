package mods.eln.misc

import mods.eln.Eln

/**
 * The voltage class of an item, shown as a coloured background behind its icon
 * (assets/eln/textures/voltages/<level>.png). Since 1.21 that background is the first layer of
 * the item's generated JSON model (see the data generator) instead of a custom render pass.
 */
enum class VoltageLevelColor(val textureName: String?) {
    None(null),
    Neutral("neutral"),
    SignalVoltage("signal"),
    LowVoltage("low"),
    MediumVoltage("medium"),
    HighVoltage("high"),
    VeryHighVoltage("veryhigh"),
    Grid("grid"),
    Thermal("thermal");

    /** The tint the old fixed-function `setGLColor` applied, as RGB in 0..1 (None/Neutral/Grid/Thermal: untinted). */
    val rgb: FloatArray?
        get() = when (this) {
            SignalVoltage -> floatArrayOf(.80f, .87f, .82f)
            LowVoltage -> floatArrayOf(.55f, .84f, .68f)
            MediumVoltage -> floatArrayOf(.55f, .74f, .85f)
            HighVoltage -> floatArrayOf(.96f, .80f, .56f)
            VeryHighVoltage -> floatArrayOf(.86f, .58f, .55f)
            else -> null
        }

    companion object {
        @JvmStatic
        fun fromVoltage(voltage: Double): VoltageLevelColor {
            return if (voltage < 0) {
                None
            } else if (voltage <= 2 * Eln.LVU) {
                LowVoltage
            } else if (voltage <= 2 * Eln.MVU) {
                MediumVoltage
            } else if (voltage <= 2 * Eln.HVU) {
                HighVoltage
            } else if (voltage <= 2 * Eln.VVU) {
                VeryHighVoltage
            } else if (voltage <= 2 * Eln.CCU) {
                Neutral
            } else {
                None
            }
        }
    }
}
