package mods.eln.misc

import net.minecraft.nbt.CompoundTag

class RcInterpolator(preTao: Float) : INBTTReady {
    var ff: Float = 1 / preTao
    var target: Float
    var factorFiltered: Float
    fun step(deltaT: Float) {
        factorFiltered += (target - factorFiltered) * ff * deltaT
    }

    fun get(): Float {
        return factorFiltered
    }

    fun setValue(value: Float) {
        factorFiltered = value
    }

    fun setValueFromTarget() {
        factorFiltered = target
    }

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        target = nbt.getFloat(str + "factor")
        // Reverse compatibility. Leave this please.
        factorFiltered = if (nbt.contains("factorFiltred")) {
            nbt.getFloat(str + "factorFiltred")
        } else {
            nbt.getFloat(str + "factorFiltered")
        }
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        nbt.putFloat(str + "factor", target)
        nbt.putFloat(str + "factorFiltered", factorFiltered)
    }

    init {
        factorFiltered = 0f
        target = 0f
    }
}
