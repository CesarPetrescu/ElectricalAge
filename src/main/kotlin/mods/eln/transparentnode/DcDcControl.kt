package mods.eln.transparentnode

import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.node.transparent.TransparentNodeElementRender
import mods.eln.sim.power.*
import net.minecraft.nbt.CompoundTag
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** Explicit names in saves; missing version is always the historical signal mapping. */
class DcDcControl {
    var version = 2
    var mode = "SIGNAL"
    var value = 1.0
    var enabled = true

    internal fun validSettings(): Boolean = version in 1..2 && value.isFinite() && when (mode) {
        "SIGNAL" -> true
        "RATIO" -> value in (1.0 / 256)..256.0
        // V1 one-way converters have no voltage-target controller. Do not silently run them at a ratio.
        "VOLTAGE" -> version == 2 && value in 0.0..120_000.0
        else -> false
    }

    fun load(tag: CompoundTag) {
        version = if (tag.contains("converterControlVersion")) tag.getInt("converterControlVersion") else 1
        mode = if (tag.contains("converterControlMode")) tag.getString("converterControlMode") else "SIGNAL"
        value = if (tag.contains("converterControlValue")) tag.getDouble("converterControlValue") else 1.0
        enabled = (!tag.contains("converterEnabled") || tag.getBoolean("converterEnabled")) && validSettings()
    }

    fun save(tag: CompoundTag) {
        tag.putInt("converterControlVersion", version)
        tag.putString("converterControlMode", mode)
        tag.putDouble("converterControlValue", value)
        tag.putBoolean("converterEnabled", enabled)
    }

    fun ratio(kind: ConverterKind, normalized: Double): Double {
        if (!enabled) return 1.0
        require(validSettings()) { "Invalid converter control settings." }
        return when (mode) {
            "RATIO" -> value
            // The process supplies voltageTarget separately. The inactive signal MUST NOT be evaluated.
            "VOLTAGE" -> 1.0
            else -> ControlMapping.ratio(kind, normalized,
                if (version == 1) MappingVersion.LEGACY_LINEAR_V1 else MappingVersion.LOGARITHMIC_V2)
        }
    }

    fun write(stream: DataOutputStream) {
        stream.writeInt(version); stream.writeUTF(mode); stream.writeDouble(value); stream.writeBoolean(enabled)
    }

    fun read(stream: DataInputStream) {
        // Read a complete snapshot before changing state; a truncated packet cannot leave mixed settings.
        val nextVersion = stream.readInt()
        val nextMode = stream.readUTF()
        val nextValue = stream.readDouble()
        val nextEnabled = stream.readBoolean()
        version = nextVersion; mode = nextMode; value = nextValue
        enabled = nextEnabled && validSettings()
    }

    /** Packet validation precedes mutation. IDs deliberately do not overlap base grounding. */
    fun handle(id: Byte, stream: DataInputStream): Boolean {
        if (id.toInt() !in 40..43) return false
        try {
            when (id.toInt()) {
                40 -> {
                    val next = stream.readInt()
                    if (next !in 0..2 || (next == 2 && version != 2)) return true
                    mode = arrayOf("SIGNAL", "RATIO", "VOLTAGE")[next]
                    // Do not reinterpret a voltage as a gain (or a gain as volts) when changing units.
                    value = if (mode == "VOLTAGE") 800.0 else 1.0
                }
                41 -> {
                    val next = stream.readDouble()
                    val valid = next.isFinite() && when (mode) {
                        "RATIO" -> next in (1.0 / 256)..256.0
                        "VOLTAGE" -> version == 2 && next in 0.0..120_000.0
                        else -> false
                    }
                    if (valid) value = next
                }
                42 -> {
                    val next = stream.readBoolean()
                    enabled = next && validSettings()
                }
                43 -> { version = 2; mode = "RATIO"; value = 1.0; enabled = false }
            }
        } catch (_: IOException) {
            // Malformed/truncated client input is rejected without mutating the accepted setting.
        }
        return true
    }
}

/** Shared UI for variable converters. Existing windings/core/case slot coordinates stay unchanged. */
class DcDcControlWidgets(
    private val gui: GuiContainerEln,
    private val render: TransparentNodeElementRender,
    private val control: DcDcControl,
    private val variable: Boolean = true
) {
    private lateinit var mode: GuiButtonEln
    private lateinit var enable: GuiButtonEln
    private lateinit var upgrade: GuiButtonEln
    private lateinit var value: GuiTextFieldEln

    fun init() {
        mode = gui.newGuiButton(8, 98, 104, "")
        enable = gui.newGuiButton(116, 98, 52, "")
        value = gui.newGuiTextField(8, 124, 90).apply { text = control.value.toString() }
        upgrade = gui.newGuiButton(102, 120, 66, tr("Use V2"))
        refresh()
    }

    fun event(obj: IGuiObject) {
        when (obj) {
            mode -> {
                // Voltage targeting requires the explicit, disabled-by-default V2 upgrade.
                val modes = if (control.version == 1) arrayOf("SIGNAL", "RATIO")
                    else arrayOf("SIGNAL", "RATIO", "VOLTAGE")
                render.clientSendInt(40, (modes.indexOf(control.mode) + 1) % modes.size)
            }
            enable -> render.clientSendBoolean(42, !control.enabled)
            upgrade -> render.clientSendId(43)
        }
    }

    fun text(field: GuiTextFieldEln, text: String) {
        if (field === value && variable && control.mode != "SIGNAL") {
            text.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { render.clientSendDouble(41, it) }
        }
    }

    fun refresh() {
        mode.displayString = when (control.mode) {
            "SIGNAL" -> tr("Signal (0-50 V)")
            "RATIO" -> tr("Ratio (x)")
            "VOLTAGE" -> tr("Target (V)")
            else -> tr("Invalid mode")
        }
        enable.displayString = if (control.enabled) tr("On") else tr("Off")
        mode.visible = variable
        value.visible = variable && control.mode != "SIGNAL"
        value.enabled = value.visible
        value.setComment(0, if (control.mode == "VOLTAGE")
            tr("Internal output target (V); terminal voltage falls with winding and cable losses")
            else tr("Voltage gain x = Vout / Vin; x2 requests twice the input voltage"))
        value.setComment(1, tr("Manual modes ignore the 0-50 V control-signal connector"))
        if (!value.isFocused) value.text = control.value.toString()
        upgrade.visible = control.version == 1
    }
}

internal fun converterStateText(code: String): String = when (code) {
    "IDLE" -> tr("Idle")
    "DISABLED" -> tr("Disabled or incomplete construction")
    "NO_INPUT" -> tr("No input supply")
    "INVALID_NETWORK" -> tr("Invalid or conflicting network")
    "INVALID_CONTROL" -> tr("Invalid control setting or signal")
    "NON_CONVERGENT" -> tr("Network did not converge; reconfigure to reset")
    "INPUT_OVERVOLTAGE" -> tr("Input exceeds winding voltage rating")
    "VOLTAGE_LIMIT" -> tr("Winding voltage rating exceeded")
    "FIXED_RATIO_LIMIT" -> tr("Fixed-ratio current or voltage rating exceeded")
    "TRANSFERRING" -> tr("Transferring power")
    "REGULATING" -> tr("Regulating")
    "INPUT_CURRENT_OR_SAG" -> tr("Input current limit or voltage sag")
    "OUTPUT_CURRENT" -> tr("Output current limit")
    "OUTPUT_POWER" -> tr("Output power limit")
    "MAXIMUM_GAIN" -> tr("Maximum conversion ratio")
    "OUTPUT_VOLTAGE" -> tr("Output voltage limit")
    "OUTPUT_ALREADY_HIGH" -> tr("Output already at target; reverse transfer blocked")
    "GAIN_UNAVAILABLE" -> tr("Requested conversion is outside this topology")
    "INPUT_UNDERVOLTAGE" -> tr("Input undervoltage")
    else -> tr("Converter state: %1$", code)
}
