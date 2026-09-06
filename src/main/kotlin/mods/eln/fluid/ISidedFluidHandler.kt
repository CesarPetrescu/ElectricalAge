package mods.eln.fluid

import net.minecraft.core.Direction
import net.minecraftforge.fluids.Fluid
import net.neoforged.neoforge.fluids.FluidStack
import net.minecraftforge.fluids.FluidTankInfo

/**
 * The side-aware fluid interface Electrical Age is written against: 1.7.10's
 * `net.minecraftforge.fluids.IFluidHandler`.
 *
 * 1.11 replaced it with a capability whose methods carry no side at all
 * ([net.neoforged.neoforge.fluids.capability.IFluidHandler]) - the side is chosen when the
 * capability is looked up, and each side may hand back a different handler. Every fluid machine
 * in the mod branches on the side *inside* its handler (see [ElementSidedFluidHandler] and the
 * thermal heat exchanger, which only accepts flow bottom-to-top), so the interface stays as the
 * mod's own internal contract.
 *
 * Phase 4 wraps it: `getCapability(FLUID_HANDLER_CAPABILITY, side)` returns a small adapter that
 * binds this handler to one side and exposes the capability shape to other mods. Nothing outside
 * Electrical Age should implement this interface.
 */
interface ISidedFluidHandler {

    /** @return amount of [resource] that was (or, when [doFill] is false, would have been) filled. */
    fun fill(from: Direction?, resource: FluidStack?, doFill: Boolean): Int

    fun drain(from: Direction?, resource: FluidStack?, doDrain: Boolean): FluidStack?

    fun drain(from: Direction?, maxDrain: Int, doDrain: Boolean): FluidStack?

    fun canFill(from: Direction?, fluid: Fluid?): Boolean

    fun canDrain(from: Direction?, fluid: Fluid?): Boolean

    fun getTankInfo(from: Direction?): Array<FluidTankInfo>
}
