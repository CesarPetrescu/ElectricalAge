package mods.eln.transparentnode

import net.minecraft.nbt.CompoundTag
import org.junit.Test
import kotlin.test.*
import java.io.*
import mods.eln.sim.power.ConverterKind

class DcDcControlTest {
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
        val bytes = ByteArrayOutputStream(); DataOutputStream(bytes).writeDouble(Double.NaN)
        assertTrue(c.handle(41, DataInputStream(ByteArrayInputStream(bytes.toByteArray()))))
        assertEquals(800.0, c.value)
    }
}
