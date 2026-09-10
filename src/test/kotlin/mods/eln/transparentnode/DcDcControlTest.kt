package mods.eln.transparentnode

import net.minecraft.nbt.CompoundTag
import org.junit.Test
import kotlin.test.*
import java.io.*
import mods.eln.sim.power.ConverterKind

class DcDcControlTest {
    private fun packet(write: DataOutputStream.() -> Unit): DataInputStream {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { it.write() }
        return DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
    }

    @Test fun oldSaveKeepsExactHistoricalSignalMapping() {
        val c = DcDcControl(); c.load(CompoundTag())
        assertEquals(1, c.version)
        assertEquals(128.001953125, c.ratio(ConverterKind.VARIABLE, .5), 1e-10)
    }

    @Test fun newSaveRoundTripsManualSetpointAndEnableState() {
        val c = DcDcControl().apply { mode = "VOLTAGE"; value = 3200.0; enabled = false }
        val tag = CompoundTag(); c.save(tag)
        val restored = DcDcControl().apply { load(tag) }
        assertEquals(2, restored.version); assertEquals("VOLTAGE", restored.mode)
        assertEquals(3200.0, restored.value); assertFalse(restored.enabled)
    }

    @Test fun invalidFutureOrNonfiniteSettingsFailClosed() {
        val tag = CompoundTag().apply { putInt("converterControlVersion", 999) }
        assertFalse(DcDcControl().apply { load(tag) }.enabled)
        tag.putInt("converterControlVersion", 2); tag.putDouble("converterControlValue", Double.NaN)
        assertFalse(DcDcControl().apply { load(tag) }.enabled)
    }

    @Test fun nonfiniteNetworkSetpointCannotMutateValidSetting() {
        val c = DcDcControl().apply { mode = "VOLTAGE"; value = 800.0 }
        assertTrue(c.handle(41, packet { writeDouble(Double.NaN) }))
        assertEquals(800.0, c.value)
    }

    @Test fun voltageModeNeverEvaluatesInactiveSignalForAnyConverterKind() {
        val c = DcDcControl().apply { mode = "VOLTAGE"; value = 800.0 }
        for (kind in ConverterKind.values()) {
            for (signal in listOf(0.0, .5, 1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
                assertEquals(1.0, c.ratio(kind, signal), "$kind voltage target must not read $signal")
                assertEquals(800.0, c.value)
                assertTrue(c.enabled)
            }
        }
    }

    @Test fun manualRatioIsExactAndNeverEvaluatesInactiveSignal() {
        val c = DcDcControl().apply { mode = "RATIO" }
        for (gain in listOf(1.0 / 256, .5, 1.0, 2.0, 256.0)) {
            c.value = gain
            for (kind in ConverterKind.values()) assertEquals(gain, c.ratio(kind, Double.NaN))
        }
    }

    @Test fun activeNonfiniteSignalRemainsAFaultNotAnImplicitUnityRatio() {
        val c = DcDcControl()
        for (kind in ConverterKind.values()) {
            for (signal in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
                assertFailsWith<IllegalArgumentException> { c.ratio(kind, signal) }
            }
        }
    }

    @Test fun protectedSignalMappingsKeepTheirExistingEndpoints() {
        val c = DcDcControl()
        val endpoints = mapOf(
            ConverterKind.VARIABLE to (1.0 / 256 to 256.0),
            ConverterKind.BOOST to (1.0 to 256.0),
            ConverterKind.BUCK to (1.0 / 256 to 1.0),
            ConverterKind.BUCK_BOOST to (1.0 / 256 to 256.0),
            ConverterKind.ISOLATION to (1.0 to 1.0)
        )
        for ((kind, values) in endpoints) {
            assertEquals(values.first, c.ratio(kind, 0.0), 1e-12)
            assertEquals(values.second, c.ratio(kind, 1.0), 1e-12)
        }
        assertEquals(1.0, c.ratio(ConverterKind.BUCK_BOOST, .5), 1e-12)
    }

    @Test fun signalModeRejectsNumericFieldPackets() {
        val c = DcDcControl()
        c.handle(41, packet { writeDouble(10.0) })
        assertEquals(1.0, c.value)
    }

    @Test fun manualSetpointBoundsAreValidatedBeforeMutation() {
        for (mode in listOf("RATIO", "VOLTAGE")) {
            val c = DcDcControl().apply { this.mode = mode; value = 1.0 }
            val invalid = listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY) +
                if (mode == "RATIO") listOf(0.0, 1.0 / 512, 257.0) else listOf(120_001.0)
            for (next in invalid) {
                c.handle(41, packet { writeDouble(next) })
                assertEquals(1.0, c.value, "$mode must reject $next")
            }
        }
    }

    @Test fun zeroVoltageRequestIsRepresentableWithoutReadingSignal() {
        val c = DcDcControl().apply { mode = "VOLTAGE" }
        c.handle(41, packet { writeDouble(0.0) })
        assertEquals(0.0, c.value)
        assertTrue(c.validSettings())
        assertEquals(1.0, c.ratio(ConverterKind.VARIABLE, Double.NaN))
    }

    @Test fun changingModeDoesNotReinterpretUnits() {
        val c = DcDcControl().apply { mode = "VOLTAGE"; value = 3200.0 }
        c.handle(40, packet { writeInt(1) })
        assertEquals("RATIO", c.mode); assertEquals(1.0, c.value)
        c.handle(40, packet { writeInt(2) })
        assertEquals("VOLTAGE", c.mode); assertEquals(800.0, c.value)
    }

    @Test fun legacyVoltageTargetRequiresExplicitSafeUpgrade() {
        val c = DcDcControl().apply { load(CompoundTag()) }
        c.handle(40, packet { writeInt(2) })
        assertEquals("SIGNAL", c.mode); assertEquals(1, c.version)
        c.handle(43, packet {})
        assertEquals(2, c.version); assertFalse(c.enabled)
        assertEquals("RATIO", c.mode); assertEquals(1.0, c.value)
        c.handle(40, packet { writeInt(2) })
        assertEquals("VOLTAGE", c.mode); assertFalse(c.enabled)
    }

    @Test fun invalidPersistedControlsCannotBeReenabledByAnEnablePacket() {
        for ((version, mode, value) in listOf(
            Triple(999, "RATIO", 1.0), Triple(2, "FUTURE", 1.0),
            Triple(2, "RATIO", 0.0), Triple(2, "RATIO", 257.0),
            Triple(2, "VOLTAGE", -1.0), Triple(2, "VOLTAGE", 120_001.0),
            Triple(1, "VOLTAGE", 800.0)
        )) {
            val tag = CompoundTag().apply {
                putInt("converterControlVersion", version); putString("converterControlMode", mode)
                putDouble("converterControlValue", value); putBoolean("converterEnabled", true)
            }
            val c = DcDcControl().apply { load(tag) }
            assertFalse(c.enabled)
            c.handle(42, packet { writeBoolean(true) })
            assertFalse(c.enabled, "$version $mode $value must remain disabled")
        }
    }

    @Test fun synchronizedSettingsAreValidatedAndRoundTrip() {
        val original = DcDcControl().apply { mode = "RATIO"; value = .5 }
        val restored = DcDcControl().apply { read(packet { original.write(this) }) }
        assertTrue(restored.enabled); assertEquals(.5, restored.ratio(ConverterKind.BUCK, Double.NaN))
        original.version = 999
        restored.read(packet { original.write(this) })
        assertFalse(restored.enabled)
    }

    @Test fun truncatedCommandPacketsDoNotPartiallyMutateSettings() {
        for ((id, size) in listOf(40 to 4, 41 to 8, 42 to 1)) {
            for (length in 0 until size) {
                val c = DcDcControl().apply { mode = "RATIO"; value = 2.0 }
                assertTrue(c.handle(id.toByte(), DataInputStream(ByteArrayInputStream(ByteArray(length)))))
                assertEquals(2, c.version); assertEquals("RATIO", c.mode)
                assertEquals(2.0, c.value); assertTrue(c.enabled)
            }
        }
    }

    @Test fun truncatedSnapshotDoesNotPartiallyMutateSettings() {
        val c = DcDcControl().apply { mode = "RATIO"; value = 2.0 }
        assertFailsWith<EOFException> { c.read(packet { writeInt(1); writeUTF("SIGNAL") }) }
        assertEquals(2, c.version); assertEquals("RATIO", c.mode)
        assertEquals(2.0, c.value); assertTrue(c.enabled)
    }

    @Test fun unknownPacketsRemainAvailableToOtherHandlers() {
        val input = packet { writeInt(123) }
        assertFalse(DcDcControl().handle(39, input))
        assertEquals(123, input.readInt())
    }
}
