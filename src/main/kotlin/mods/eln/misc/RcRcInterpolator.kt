package mods.eln.misc

import net.minecraft.nbt.CompoundTag

class RcRcInterpolator(tao1: Float, tao2: Float) : INBTTReady {
    var c1: Float
    var c2: Float
    var target: Float
    var tao1Inv: Float = 1 / tao1
    var tao2Inv: Float = 1 / tao2
    fun step(deltaT: Float) {
        c1 += (target - c1) * tao1Inv * deltaT
        c2 += (c1 - c2) * tao2Inv * deltaT
    }

    fun get(): Float {
        return c2
    }

    fun setValue(value: Float) {
        c2 = value
        c1 = value
    }

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        c1 = nbt.getFloat(str + "c1")
        c2 = nbt.getFloat(str + "c2")
        target = nbt.getFloat(str + "target")
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        nbt.putFloat(str + "c1", c1)
        nbt.putFloat(str + "c2", c2)
        nbt.putFloat(str + "target", target)
    }

    init {
        c1 = 0f
        c2 = 0f
        target = 0f
    }
}
