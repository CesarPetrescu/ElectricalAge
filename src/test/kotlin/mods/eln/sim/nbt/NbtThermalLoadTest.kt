package mods.eln.sim.nbt

import kotlin.test.Test
import kotlin.test.assertEquals
import net.minecraft.nbt.CompoundTag
import mods.eln.misc.writeToNBT

class NbtThermalLoadTest {
    @Test
    fun readWritePreservesTemperature() {
        val load = NbtThermalLoad("t")
        load.temperatureCelsius = 42.0

        val nbt = CompoundTag()
        load.writeToNBT(nbt, "pfx")

        val other = NbtThermalLoad("t")
        other.readFromNBT(nbt, "pfx")
        assertEquals(42.0, other.temperatureCelsius)
    }

    @Test
    fun readFromNbtHandlesInvalidValues() {
        val nbt = CompoundTag()
        nbt.putFloat("pfxtTc", Float.NaN)
        val load = NbtThermalLoad("t")
        load.readFromNBT(nbt, "pfx")
        assertEquals(0.0, load.temperatureCelsius)

        nbt.putFloat("pfxtTc", Float.NEGATIVE_INFINITY)
        load.readFromNBT(nbt, "pfx")
        assertEquals(0.0, load.temperatureCelsius)

        nbt.putFloat("pfxtTc", Float.POSITIVE_INFINITY)
        load.readFromNBT(nbt, "pfx")
        assertEquals(0.0, load.temperatureCelsius)
    }
}
