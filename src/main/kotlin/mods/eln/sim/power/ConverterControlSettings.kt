package mods.eln.sim.power

import net.minecraft.nbt.CompoundTag
import java.io.DataInputStream
import java.io.DataOutputStream

/** Persistence uses names; compact packet codes are explicit, not enum positions. */
enum class ConverterInputMode(val code: Int) {
    EXTERNAL_SIGNAL(0), MANUAL_RATIO(1), MANUAL_VOLTAGE(2)
}

object ConverterPackets {
    const val MODE: Byte = 60
    const val VALUE: Byte = 61
    const val MAPPING: Byte = 62
    const val PROTECTION: Byte = 63
}

class ConverterControlSettings {
    var mapping = MappingVersion.LOGARITHMIC_V2
    var mode = ConverterInputMode.MANUAL_RATIO
    var manualRatio = 1.0
        private set
    var manualVoltage = 800.0
        private set
    var valid = true
        private set

    fun setMode(code: Int, allowVoltage: Boolean): Boolean {
        val next = ConverterInputMode.values().firstOrNull { it.code == code } ?: return false
        if (!allowVoltage && next == ConverterInputMode.MANUAL_VOLTAGE) return false
        mode = next
        valid = true
        return true
    }

    fun setValue(value: Double): Boolean {
        if (!value.isFinite()) return false
        when (mode) {
            ConverterInputMode.MANUAL_RATIO -> {
                if (value !in (1.0 / 256.0)..256.0) return false
                manualRatio = value
            }
            ConverterInputMode.MANUAL_VOLTAGE -> {
                if (value <= 0.0 || value > 120_000.0) return false
                manualVoltage = value
            }
            ConverterInputMode.EXTERNAL_SIGNAL -> return false
        }
        valid = true
        return true
    }

    fun setMapping(name: String): Boolean {
        val next = MappingVersion.values().firstOrNull { it.name == name } ?: return false
        mapping = next
        valid = true
        return true
    }

    fun ratio(kind: ConverterKind, normalized: Double): Double {
        if (!valid) return Double.NaN
        val ratio = when (mode) {
            ConverterInputMode.EXTERNAL_SIGNAL -> {
                if (!normalized.isFinite()) return Double.NaN
                ControlMapping.ratio(kind, normalized, mapping)
            }
            ConverterInputMode.MANUAL_RATIO -> manualRatio
            ConverterInputMode.MANUAL_VOLTAGE -> 1.0 // handled as a voltage target by the regulated solver
        }
        if (kind == ConverterKind.BUCK && ratio > 1.0 || kind == ConverterKind.BOOST && ratio < 1.0)
            return Double.NaN
        return ratio
    }

    fun voltageTarget(): Double? =
        if (valid && mode == ConverterInputMode.MANUAL_VOLTAGE) manualVoltage else null

    fun readNbt(nbt: CompoundTag, allowVoltage: Boolean) {
        // Missing tags mean old external-signal semantics; fresh constructors remain manual 1:1.
        val map = if (nbt.contains("converterMapping")) nbt.getString("converterMapping") else "LEGACY_LINEAR_V1"
        val input = if (nbt.contains("converterInput")) nbt.getString("converterInput") else "EXTERNAL_SIGNAL"
        val nextMap = MappingVersion.values().firstOrNull { it.name == map }
        val nextMode = ConverterInputMode.values().firstOrNull { it.name == input }
        val ratio = if (nbt.contains("converterManualRatio")) nbt.getDouble("converterManualRatio") else 1.0
        val voltage = if (nbt.contains("converterManualVoltage")) nbt.getDouble("converterManualVoltage") else 800.0
        valid = nextMap != null && nextMode != null && (allowVoltage || nextMode != ConverterInputMode.MANUAL_VOLTAGE) &&
            ratio.isFinite() && ratio in (1.0 / 256.0)..256.0 && voltage.isFinite() && voltage > 0 && voltage <= 120_000.0 &&
            (!nbt.contains("converterControlValid") || nbt.getBoolean("converterControlValid"))
        mapping = nextMap ?: MappingVersion.LEGACY_LINEAR_V1
        mode = nextMode ?: ConverterInputMode.EXTERNAL_SIGNAL
        manualRatio = if (ratio.isFinite() && ratio in (1.0 / 256.0)..256.0) ratio else 1.0
        manualVoltage = if (voltage.isFinite() && voltage > 0 && voltage <= 120_000.0) voltage else 800.0
    }

    fun writeNbt(nbt: CompoundTag) {
        nbt.putString("converterMapping", mapping.name)
        nbt.putString("converterInput", mode.name)
        nbt.putDouble("converterManualRatio", manualRatio)
        nbt.putDouble("converterManualVoltage", manualVoltage)
        nbt.putBoolean("converterControlValid", valid)
    }

    fun writeWire(out: DataOutputStream) {
        out.writeUTF(mapping.name); out.writeByte(mode.code)
        out.writeDouble(manualRatio); out.writeDouble(manualVoltage); out.writeBoolean(valid)
    }

    fun readWire(input: DataInputStream, allowVoltage: Boolean) {
        val nbt = CompoundTag()
        nbt.putString("converterMapping", input.readUTF())
        val code = input.readUnsignedByte()
        nbt.putString("converterInput", ConverterInputMode.values().firstOrNull { it.code == code }?.name ?: "UNKNOWN")
        nbt.putDouble("converterManualRatio", input.readDouble())
        nbt.putDouble("converterManualVoltage", input.readDouble())
        nbt.putBoolean("converterControlValid", input.readBoolean())
        readNbt(nbt, allowVoltage)
    }
}
