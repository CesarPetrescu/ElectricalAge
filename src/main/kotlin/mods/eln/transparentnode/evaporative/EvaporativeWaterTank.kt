package mods.eln.transparentnode.evaporative

import mods.eln.fluid.FluidTankInfo
import mods.eln.fluid.ISidedFluidHandler
import mods.eln.misc.McRegistries
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import net.neoforged.neoforge.fluids.capability.templates.FluidTank

/** Vertical water ports; all queries and SIMULATE calls are side-effect free. */
class EvaporativeWaterTank(private val changed: () -> Unit) : ISidedFluidHandler {
    companion object { const val CAPACITY = 4000 }
    val tank = FluidTank(CAPACITY) { it.fluid === Fluids.WATER }
    private val film = FractionalWater()
    val availableMb: Double get() = tank.fluidAmount + film.filmMb()
    val filmMb: Double get() = film.filmMb()
    private fun port(side: Direction?) = side == null || side.axis == Direction.Axis.Y
    override fun canFill(from: Direction?, fluid: Fluid?) = port(from) && fluid === Fluids.WATER
    override fun canDrain(from: Direction?, fluid: Fluid?) = port(from) && fluid === Fluids.WATER
    override fun fill(from: Direction?, resource: FluidStack?, doFill: Boolean): Int {
        if (resource == null || resource.isEmpty || !canFill(from, resource.fluid)) return 0
        return tank.fill(resource, if (doFill) FluidAction.EXECUTE else FluidAction.SIMULATE).also {
            if (doFill && it > 0) changed()
        }
    }
    override fun drain(from: Direction?, resource: FluidStack?, doDrain: Boolean): FluidStack {
        if (resource == null || resource.isEmpty || resource.fluid !== Fluids.WATER) return FluidStack.EMPTY
        return drain(from, resource.amount, doDrain)
    }
    override fun drain(from: Direction?, maxDrain: Int, doDrain: Boolean): FluidStack {
        if (!port(from) || maxDrain <= 0) return FluidStack.EMPTY
        return tank.drain(maxDrain, if (doDrain) FluidAction.EXECUTE else FluidAction.SIMULATE).also {
            if (doDrain && !it.isEmpty) changed()
        }
    }
    override fun getTankInfo(from: Direction?) = if (port(from)) arrayOf(FluidTankInfo(tank)) else emptyArray()
    fun evaporate(requestedMb: Double): Double = film.consume(requestedMb, tank.fluidAmount) {
        tank.drain(it, FluidAction.EXECUTE).amount.also { drained -> if (drained > 0) changed() }
    }
    fun save(tag: CompoundTag) {
        tag.put("water", tank.writeToNBT(McRegistries.access(), CompoundTag()))
        tag.putDouble("filmMb", film.filmMb())
    }
    fun load(tag: CompoundTag) {
        tank.readFromNBT(McRegistries.access(), tag.getCompound("water"))
        if (!tank.isEmpty && tank.fluid.fluid !== Fluids.WATER) tank.fluid = FluidStack.EMPTY
        if (tank.fluidAmount > CAPACITY) tank.fluid = FluidStack(Fluids.WATER, CAPACITY)
        film.restore(tag.getDouble("filmMb"))
    }
}
