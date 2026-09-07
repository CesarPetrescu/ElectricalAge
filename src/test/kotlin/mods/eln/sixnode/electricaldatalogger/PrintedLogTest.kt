package mods.eln.sixnode.electricaldatalogger

import net.minecraft.nbt.CompoundTag
import kotlin.test.*

class PrintedLogTest {
    @Test fun boundedHistoryKeepsNewestSamples() {
        val logs = DataLogs(3)
        repeat(10) { logs.write(it.toByte()) }
        assertContentEquals(byteArrayOf(9, 8, 7), logs.copyLog())
        val zero = DataLogs(0); zero.write(5); assertEquals(0, zero.size())
    }
    @Test fun loadingReplacesHistoryRatherThanAppending() {
        val logs = DataLogs(4)
        repeat(4) { logs.write(it.toByte()) }
        val tag = CompoundTag().apply { putByteArray("log", byteArrayOf(12, 11)) }
        logs.readFromNBT(tag, ""); logs.readFromNBT(tag, "")
        assertContentEquals(byteArrayOf(12, 11), logs.copyLog())
    }
    @Test fun oversizedSavedHistoryKeepsNewestEnd() {
        val logs = DataLogs(2)
        logs.readFromNBT(CompoundTag().apply { putByteArray("log", byteArrayOf(5, 4, 3, 2, 1)) }, "")
        assertContentEquals(byteArrayOf(5, 4), logs.copyLog())
    }
    @Test fun snapshotRetainsUnitsRangeAndDoesNotChangeWithMonitor() {
        val logs = DataLogs(4)
        logs.samplingPeriod = 2f; logs.maxValue = 12f; logs.minValue = -12f; logs.unitType = DataLogs.voltageType
        logs.write(-128); logs.write(127)
        val tag = CompoundTag(); logs.writeToNBT(tag, "")
        val chart = PrintedLogData(tag)
        logs.reset(); tag.putByteArray("log", byteArrayOf(0))
        assertEquals(2, chart.size); assertEquals(12.0, chart.value(0)); assertEquals(-12.0, chart.value(1))
        assertEquals(2.0, chart.duration); assertEquals(0.0, chart.age(0)); assertEquals(DataLogs.voltageType, chart.unit)
    }
    @Test fun emptyAndSingleSampleChartsHaveZeroDuration() {
        assertEquals(0.0, PrintedLogData(null).duration)
        val one = PrintedLogData(CompoundTag().apply { putByteArray("log", byteArrayOf(0)) })
        assertEquals(1, one.size); assertEquals(0.0, one.duration)
    }
    @Test fun legacyAndMalformedMetadataRemainFiniteAndBounded() {
        val chart = PrintedLogData(CompoundTag().apply {
            putByteArray("log", ByteArray(600)); putFloat("samplingPeriod", Float.NaN)
            putFloat("maxValue", Float.POSITIVE_INFINITY); putFloat("minValue", Float.NaN); putByte("unitType", 120)
        })
        assertEquals(256, chart.size); assertTrue(chart.period > 0); assertTrue(chart.value(0).isFinite())
        assertTrue(chart.zeroLine); assertEquals(DataLogs.noType, chart.unit)
    }
    @Test fun reversedAndFlatDisplayRangesDoNotDivideByZero() {
        val tag = CompoundTag().apply { putByteArray("log", byteArrayOf(-128, 127)); putFloat("minValue", 10f); putFloat("maxValue", -10f) }
        assertEquals(10.0, PrintedLogData(tag).value(0)); assertEquals(-10.0, PrintedLogData(tag).value(1))
        tag.putFloat("maxValue", 10f)
        assertEquals(10.0, PrintedLogData(tag).value(1))
    }
}
