package mods.eln.node.transparent;

import mods.eln.node.Node;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nullable;

/**
 * Proxy class for TNEs with Forge fluids.
 */
public class TransparentNodeEntityWithFluid extends TransparentNodeEntity implements IFluidHandler {

    private IFluidHandler getFluidHandler() {
        if (!world.isRemote) {
            Node node = getNode();
            if (node instanceof TransparentNode) {
                TransparentNode tn = (TransparentNode) node;
                IFluidHandler i = tn.getFluidHandler();
                if (i != null) {
                    return i;
                }
            }
        }
        return FakeFluidHandler.INSTANCE;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, facing);
    }

    /**
     * Fills fluid into internal tanks, distribution is left entirely to the IFluidHandler.
     *
     * @param resource FluidStack representing the Fluid and maximum amount of fluid to be filled.
     * @param doFill   If false, fill will only be simulated.
     * @return Amount of resource that was (or would have been, if simulated) filled.
     */
    @Override
    public int fill(FluidStack resource, boolean doFill) {
        return getFluidHandler().fill(resource, doFill);
    }

    /**
     * Drains fluid out of internal tanks, distribution is left entirely to the IFluidHandler.
     *
     * @param resource FluidStack representing the Fluid and maximum amount of fluid to be drained.
     * @param doDrain  If false, drain will only be simulated.
     * @return FluidStack representing the Fluid and amount that was (or would have been, if
     * simulated) drained.
     */
    @Override
    public FluidStack drain(FluidStack resource, boolean doDrain) {
        return getFluidHandler().drain(resource, doDrain);
    }

    /**
     * Drains fluid out of internal tanks, distribution is left entirely to the IFluidHandler.
     * <p>
     * This method is not Fluid-sensitive.
     *
     * @param maxDrain Maximum amount of fluid to drain.
     * @param doDrain  If false, drain will only be simulated.
     * @return FluidStack representing the Fluid and amount that was (or would have been, if
     * simulated) drained.
     */
    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        return getFluidHandler().drain(maxDrain, doDrain);
    }

    @Override
    public IFluidTankProperties[] getTankProperties() {
        return getFluidHandler().getTankProperties();
    }

    private static class FakeFluidHandler implements IFluidHandler {
        static FakeFluidHandler INSTANCE = new FakeFluidHandler();

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            return null;
        }

        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            return null;
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            return new IFluidTankProperties[0];
        }
    }
}
