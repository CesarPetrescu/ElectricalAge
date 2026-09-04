package mods.eln.node.transparent

import net.minecraft.util.EnumFacing
import net.minecraftforge.fluids.Fluid
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.FluidTankInfo
import mods.eln.fluid.ISidedFluidHandler

/**
 * Proxy class for TNEs with Forge fluids.
 */
class TransparentNodeEntityWithFluid : TransparentNodeEntity(), ISidedFluidHandler {
    private val fluidHandler: ISidedFluidHandler
        get() {
            if (!world.isRemote) {
                val node = node
                if (node != null && node is TransparentNode) {
                    val i = node.fluidHandler
                    if (i != null) {
                        return i
                    }
                }
            }
            return FakeFluidHandler.INSTANCE
        }

    /**
     * Fills fluid into internal tanks, distribution is left entirely to the ISidedFluidHandler.
     *
     * @param from     Orientation the Fluid is pumped in from.
     * @param resource FluidStack representing the Fluid and maximum amount of fluid to be filled.
     * @param doFill   If false, fill will only be simulated.
     * @return Amount of resource that was (or would have been, if simulated) filled.
     */
    override fun fill(from: EnumFacing?, resource: FluidStack?, doFill: Boolean): Int {
        return fluidHandler.fill(from, resource, doFill)
    }

    /**
     * Drains fluid out of internal tanks, distribution is left entirely to the ISidedFluidHandler.
     *
     * @param from     Orientation the Fluid is drained to.
     * @param resource FluidStack representing the Fluid and maximum amount of fluid to be drained.
     * @param doDrain  If false, drain will only be simulated.
     * @return FluidStack representing the Fluid and amount that was (or would have been, if
     * simulated) drained.
     */
    override fun drain(from: EnumFacing?, resource: FluidStack?, doDrain: Boolean): FluidStack? {
        return fluidHandler.drain(from, resource, doDrain)
    }

    /**
     * Drains fluid out of internal tanks, distribution is left entirely to the ISidedFluidHandler.
     *
     *
     * This method is not Fluid-sensitive.
     *
     * @param from     Orientation the fluid is drained to.
     * @param maxDrain Maximum amount of fluid to drain.
     * @param doDrain  If false, drain will only be simulated.
     * @return FluidStack representing the Fluid and amount that was (or would have been, if
     * simulated) drained.
     */
    override fun drain(from: EnumFacing?, maxDrain: Int, doDrain: Boolean): FluidStack? {
        return fluidHandler.drain(from, maxDrain, doDrain)
    }

    /**
     * Returns true if the given fluid can be inserted into the given direction.
     *
     *
     * More formally, this should return true if fluid is able to enter from the given direction.
     *
     * @param from
     * @param fluid
     */
    override fun canFill(from: EnumFacing?, fluid: Fluid?): Boolean {
        return false
    }

    /**
     * Returns true if the given fluid can be extracted from the given direction.
     *
     *
     * More formally, this should return true if fluid is able to leave from the given direction.
     *
     * @param from
     * @param fluid
     */
    override fun canDrain(from: EnumFacing?, fluid: Fluid?): Boolean {
        return fluidHandler.canDrain(from, fluid)
    }

    /**
     * Returns an array of objects which represent the internal tanks. These objects cannot be used
     * to manipulate the internal tanks. See [FluidTankInfo].
     *
     * @param from Orientation determining which tanks should be queried.
     * @return Info for the relevant internal tanks.
     */
    override fun getTankInfo(from: EnumFacing?): Array<FluidTankInfo> {
        return fluidHandler.getTankInfo(from)
    }

    private class FakeFluidHandler : ISidedFluidHandler {
        override fun fill(from: EnumFacing?, resource: FluidStack?, doFill: Boolean): Int {
            return 0
        }

        override fun drain(from: EnumFacing?, resource: FluidStack?, doDrain: Boolean): FluidStack? {
            return null
        }

        override fun drain(from: EnumFacing?, maxDrain: Int, doDrain: Boolean): FluidStack? {
            return null
        }

        override fun canFill(from: EnumFacing?, fluid: Fluid?): Boolean {
            return false
        }

        override fun canDrain(from: EnumFacing?, fluid: Fluid?): Boolean {
            return false
        }

        override fun getTankInfo(from: EnumFacing?): Array<FluidTankInfo> {
            return arrayOfNulls(0)
        }

        companion object {
            var INSTANCE = FakeFluidHandler()
        }
    }
}
