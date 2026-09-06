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

    /**
     * The voltage background behind a 3D-drawn item icon (render code only). Flat icons get it
     * from their JSON model instead (see ElnDataGenerators).
     */
    fun drawIconBackground(type: mods.eln.client.itemrender.IItemRenderer.ItemRenderType) {
        if (!mods.eln.Eln.config.getBooleanOrElse("ui.icons.noVoltageBackground", false) && textureName != null &&
            (type == mods.eln.client.itemrender.IItemRenderer.ItemRenderType.INVENTORY || type == mods.eln.client.itemrender.IItemRenderer.ItemRenderType.FIRST_PERSON_MAP)) {
            UtilsClient.drawIcon(type, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("eln", "textures/voltages/$textureName.png"))
        }
    }

    /** The 1.7.10 fixed-function tint (render code only). */
    fun setGLColor() {
        val c = rgb ?: return
        mods.eln.client.gl.GL11.glColor3f(c[0], c[1], c[2])
    }

    companion object {
        @JvmStatic
        fun fromCable(descriptor: mods.eln.sixnode.electricalcable.ElectricalCableDescriptor?): VoltageLevelColor {
            return if (descriptor != null) {
                if (descriptor.signalWire) {
                    SignalVoltage
                } else {
                    fromVoltage(descriptor.electricalNominalVoltage)
                }
            } else {
                None
            }
        }

        @JvmStatic
        fun fromCable(descriptor: mods.eln.sixnode.currentcable.CurrentCableDescriptor?): VoltageLevelColor {
            return if (descriptor != null) {
                Neutral
            } else {
                None
            }
        }

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
