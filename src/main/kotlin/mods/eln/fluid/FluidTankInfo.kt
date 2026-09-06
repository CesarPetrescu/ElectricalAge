package mods.eln.fluid

import net.neoforged.neoforge.fluids.FluidStack

/** 1.7.10's `FluidTankInfo`: what a tank holds and how much it can hold. Internal to the mod's [ISidedFluidHandler]. */
class FluidTankInfo(@JvmField val fluid: FluidStack?, @JvmField val capacity: Int) {
    constructor(tank: net.neoforged.neoforge.fluids.capability.templates.FluidTank) : this(tank.fluid, tank.capacity)
}
