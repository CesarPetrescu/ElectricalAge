package mods.eln.sixnode.electricaldatalogger

import net.minecraft.nbt.CompoundTag

/** Bounded immutable presentation snapshot; also accepts old print NBT without migration. */
class PrintedLogData(tag: CompoundTag?) {
    private val samples = (tag?.getByteArray("log") ?: byteArrayOf()).take(256).toByteArray()
    val size get() = samples.size
    val period = (tag?.getFloat("samplingPeriod") ?: .5f).takeIf { it.isFinite() && it > 0f } ?: .5f
    val maximum = (tag?.getFloat("maxValue") ?: 100f).takeIf { it.isFinite() } ?: 100f
    val minimum = (tag?.getFloat("minValue") ?: 0f).takeIf { it.isFinite() } ?: 0f
    val unit = (tag?.getByte("unitType") ?: DataLogs.percentType).takeIf { it in 0..8 } ?: DataLogs.noType
    val zeroLine = tag == null || !tag.contains("showZeroLine") || tag.getBoolean("showZeroLine")
    val duration get() = (size - 1).coerceAtLeast(0) * period.toDouble()
    fun fraction(index: Int) = (samples[index].toInt() + 128) / 255.0
    fun value(index: Int) = minimum + fraction(index) * (maximum.toDouble() - minimum)
    fun age(index: Int) = index * period.toDouble()
    fun label(fraction: Float) = DataLogs.getYstring(fraction, maximum, minimum, unit)
}
