package mods.eln.fluid

import mods.eln.misc.INBTTReady
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.fluids.*

open class ElementSidedFluidHandler: IFluidHandler, INBTTReady {

    protected val tanks = mutableMapOf<EnumFacing, TankData>()

    /**
     * This method allows you to create tank references for each side of a block. You can use the same reference of tank
     * to create a block that allows access from all sides, or just a tank per EnumFacing for machinery.
     *
     * @param tankData a mutable map of TankData, accessed by the EnumFacing.
     */
    constructor(tankData: Map<EnumFacing, TankData>) {
        for (entry in tankData) {
            tanks[entry.key] = entry.value
        }
    }

    /**
     * This method makes a single tank that can be accessed from all sides. Commonly used in Eln.
     * @param tankSizeMb size of the tank in mB (millibuckets)
     */
    constructor(tankSizeMb: Int) {
        val tank = TankData(FluidTank(tankSizeMb), mutableListOf())
        EnumFacing.VALUES.forEach {
            tanks[it] = tank
        }
    }

    fun setFluidWhitelist(direction: EnumFacing, fluidWhitelist: List<Fluid>) {
        val tank = tanks[direction]
        if (tank != null) {
            // Note: The tank reference may be used in more than one side; this affects the tank more so than the side
            tank.fluidWhitelist.clear()
            tank.fluidWhitelist.addAll(fluidWhitelist)
        }
    }

    fun addFluidWhitelist(direction: EnumFacing, fluidWhitelist: Fluid) {
        val tank = tanks[direction]
        tank?.fluidWhitelist?.add(fluidWhitelist)
    }

    fun getFluidType(direction: EnumFacing): Fluid? {
        return try {
            tanks[direction]?.tank?.fluid?.getFluid()
        } catch (e: Exception) {
            null
        }
    }

    fun getCapacity(direction: EnumFacing): Int {
        return try {
            val tank = tanks[direction]
            return tank?.tank?.capacity?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun getFluidAmount(direction: EnumFacing): Int {
        return try {
            val tank = tanks[direction]
            return tank?.tank?.fluidAmount?: 0
        } catch (e: Exception) {
            0
        }
    }

    override fun fill(from: EnumFacing?, resource: FluidStack?, doFill: Boolean): Int {
        if (from == null || resource == null) return 0
        val tank = tanks[from] ?: return 0
        return if (tank.tank.fluidAmount > 0 || tank.fluidWhitelist.isEmpty()) {
            // The fluid type won't change (or there is no whitelist) so we don't need to worry about the whitelist check
            tank.tank.fill(resource, doFill)
        } else {
            // We need to make sure the new fluid is meeting the whitelist
            val resourceId = resource.fluidID
            tank.fluidWhitelist.forEach {
                if (it.id == resourceId) {
                    return tank.tank.fill(resource, doFill)
                }
            }
            return 0
        }
    }

    override fun canFill(from: EnumFacing?, fluid: Fluid?): Boolean {
        if (from == null || fluid == null) return false
        val tank = tanks[from]?: return false
        // Check if the fluid in there is the same fluid
        if (tank.tank.fluidAmount > 0) return tank.tank.fluid.getFluid().id == fluid.id
        return if (tank.fluidWhitelist.size > 0) {
            // if the fluid whitelist has elements, check the list for a compatible fluid type
            fluid.id in tank.fluidWhitelist.map { it.id }
        } else {
            // there's no fluid in the tank, nor a whitelist. Accept anything.
            true
        }
    }

    override fun getTankInfo(from: EnumFacing?): Array<FluidTankInfo> {
        if (from == null) return arrayOf()
        val tank = tanks[from]?: return arrayOf()
        return arrayOf(tank.tank.info)
    }

    override fun drain(from: EnumFacing?, resource: FluidStack?, doDrain: Boolean): FluidStack? {
        if (from == null || resource == null) return null
        val tank = tanks[from]?: return null
        return tank.tank.drain(resource.amount, doDrain)
    }

    override fun drain(from: EnumFacing?, maxDrain: Int, doDrain: Boolean): FluidStack? {
        if (from == null) return null
        val tank = tanks[from]?: return null
        return tank.tank.drain(maxDrain, doDrain)
    }

    @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
    fun fractionalDrain(from: EnumFacing, demand: Double): Double {
        val tank = tanks[from]?: return 0.0
        val drain = Math.ceil(demand - tank.fractionalDemandMb)
        val drained = drain(from, drain.toInt(), true)?.amount?.toDouble() ?: 0.0
        val available = tank.fractionalDemandMb + drained
        val actual = Math.min(demand, available)
        tank.fractionalDemandMb = Math.max(0.0, available - demand)
        return actual
    }

    override fun canDrain(from: EnumFacing?, fluid: Fluid?): Boolean {
        if (from == null || fluid == null) return false
        val tank = tanks[from]?: return false
        val currentFluid = tank.tank.fluid?.getFluid() ?: return false
        return currentFluid.id == fluid.id
    }

    override fun readFromNBT(nbt: NBTTagCompound, str: String) {
        val tankList = mutableListOf<TankData>()
        val numTanks = nbt.getInteger("${str}numTanks")
        for (idx in 0 .. numTanks) {
            val tank = TankData(FluidTank(0), mutableListOf())
            tank.readFromNBT(nbt, "${str}tank$idx")
            tankList.add(tank)
        }
        //println("numTanks: $numTanks")
        //println("tankList $tankList")
        tanks.clear()
        EnumFacing.VALUES.forEach {
            val tankRef = nbt.getInteger("${str}${it.name}tankRef")
            if (tankRef != -1 && numTanks != 0) {
                //println("$it: $tankRef")
                tanks[it] = tankList[tankRef]
            }
        }
        //println("tanks: $tanks")
    }

    override fun writeToNBT(nbt: NBTTagCompound, str: String) {
        val tanksList = mutableListOf<TankData>()
        tanks.forEach {
            if (it.value !in tanksList) {
                tanksList.add(it.value)
            }
        }
        nbt.setInteger("${str}numTanks", tanksList.size)
        tanksList.forEachIndexed {
            idx: Int, tank: TankData ->
            tank.writeToNBT(nbt, "${str}tank$idx")
        }
        EnumFacing.VALUES.forEach {
            val tank = tanks[it]
            val tankRef = tanksList.indexOf(tank)
            nbt.setInteger("${str}${it.name}tankRef", tankRef)
        }
    }
}
