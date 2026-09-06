package mods.eln.sim.nbt

import kotlin.test.Test
import kotlin.test.assertEquals
import net.minecraft.nbt.CompoundTag
import mods.eln.misc.writeToNBT

class NbtElectricalLoadTest {
    @Test
    fun readWritePreservesVoltage() {
        val load = NbtElectricalLoad("n")
        load.voltage = 3.5

        val nbt = CompoundTag()
        load.writeToNBT(nbt, "pfx")

        val other = NbtElectricalLoad("n")
        other.readFromNBT(nbt, "pfx")
        assertEquals(3.5, other.voltage)
    }

    @Test
    fun readFromNbtHandlesInvalidValues() {
        val nbt = CompoundTag()
        nbt.putFloat("pfxnUc", Float.NaN)
        val load = NbtElectricalLoad("n")
        load.readFromNBT(nbt, "pfx")
        assertEquals(0.0, load.voltage)

        nbt.putFloat("pfxnUc", Float.NEGATIVE_INFINITY)
        load.readFromNBT(nbt, "pfx")
        assertEquals(0.0, load.voltage)

        nbt.putFloat("pfxnUc", Float.POSITIVE_INFINITY)
        load.readFromNBT(nbt, "pfx")
        assertEquals(0.0, load.voltage)
    }
}
