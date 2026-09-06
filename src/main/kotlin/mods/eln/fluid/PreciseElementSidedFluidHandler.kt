package mods.eln.fluid

import net.minecraft.nbt.CompoundTag
import net.minecraft.core.Direction
import mods.eln.misc.writeToNBT

class PreciseElementSidedFluidHandler: ElementSidedFluidHandler {
    /**
     * This method allows you to create tank references for each side of a block. You can use the same reference of tank
     * to create a block that allows access from all sides, or just a tank per Direction for machinery.
     *
     * @param tankData a mutable map of TankData, accessed by the Direction.
     */
    constructor(tankData: Map<Direction, TankData>): super(tankData)

    /**
     * This method makes a single tank that can be accessed from all sides. Commonly used in Eln.
     * @param tankSizeMb size of the tank in mB (millibuckets)
     */
    constructor(tankSizeMb: Int): super(tankSizeMb)

    private var fixup = Direction.VALUES.map {Pair(it, 0.0)}.toMap().toMutableMap()

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        super.readFromNBT(nbt, str)
        Direction.VALUES.forEach {
            fixup[it] = nbt.getDouble(str + "fixup" + it.name)
        }
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        super.writeToNBT(nbt, str)
        Direction.VALUES.forEach {
            nbt.putDouble(str + "fixup" + it.name, fixup[it]?: 0.0)
        }
    }

    fun drain(direction: Direction, demand: Double): Double {
        val drain = Math.ceil(demand - (fixup[direction]?: 0.0))
        val drained = drain(direction, drain.toInt(), true)?.amount?.toDouble() ?: 0.0
        val available = (fixup[direction]?: 0.0) + drained
        val actual = Math.min(demand, available)
        fixup[direction] = Math.max(0.0, available - demand)
        return actual
    }

    fun drainEnergy(direction: Direction, energy: Double): Double {
        val heatValue = FuelRegistry.heatEnergyPerMilliBucket(tanks[direction]!!.tank.fluid?.getFluid())
        return if (heatValue > 0)
            heatValue * drain(direction, energy / heatValue)
        else
            0.0
    }
}
