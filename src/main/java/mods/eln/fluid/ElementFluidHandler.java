package mods.eln.fluid;

import mods.eln.misc.INBTTReady;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import mods.eln.misc.McRegistries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Use one of these if you want your block to support Forge fluids!
 * <p>
 * See the steam turbine for an example.
 */
public class ElementFluidHandler implements ISidedFluidHandler, INBTTReady {
    private Fluid[] whitelist;
    private float fluid_heat_mb = 0;
    public FluidTank tank;

    /**
     * Stores fluids.
     *
     * @param tankSize Tank size, in mB.
     */
    public ElementFluidHandler(int tankSize) {
        tank = new FluidTank(tankSize);
    }

    public void setFilter(Fluid[] whitelist) {
        assert whitelist != null;
        this.whitelist = whitelist;
    }

    public float getHeatEnergyPerMilliBucket() {
        if (fluid_heat_mb == 0 && !tank.getFluid().isEmpty()) setHeatEnergyPerMilliBucket(tank.getFluid().getFluid());
        return fluid_heat_mb;
    }

    /** 1.7.10's `doFill`/`doDrain` booleans on the 1.21 handler enum. */
    private static FluidAction action(boolean execute) {
        return execute ? FluidAction.EXECUTE : FluidAction.SIMULATE;
    }

    private void setHeatEnergyPerMilliBucket(Fluid fluid) {
        fluid_heat_mb = (float) FuelRegistry.INSTANCE.heatEnergyPerMilliBucket(fluid);
    }

    @Override
    public int fill(Direction from, FluidStack resource, boolean doFill) {
        if (resource == null || resource.isEmpty() || !canFill(from, resource.getFluid())) return 0;
        int filled = tank.fill(resource, action(doFill));
        // SIMULATE must leave both contents and the persisted fuel-energy cache unchanged.
        if (doFill && filled > 0) setHeatEnergyPerMilliBucket(resource.getFluid());
        return filled;
    }

    @Override
    public FluidStack drain(Direction from, FluidStack resource, boolean doDrain) {
        if (FluidStack.isSameFluidSameComponents(resource, tank.getFluid()))
            return tank.drain(resource.getAmount(), action(doDrain));
        else
            return null;
    }

    @Override
    public FluidStack drain(Direction from, int maxDrain, boolean doDrain) {
        return tank.drain(maxDrain, action(doDrain));
    }

    @Override
    public boolean canFill(Direction from, Fluid fluid) {
        if (fluid == null) return false;
        if (tank.getFluidAmount() > 0) {
            return tank.getFluid().getFluid() == fluid;
        } else {
            if (whitelist == null) return true;
            for (int i = 0; i < whitelist.length; i++) {
                if (whitelist[i] == fluid) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean canDrain(Direction from, Fluid fluid) {
        return true;
    }

    @Override
    public FluidTankInfo[] getTankInfo(Direction from) {
        return new FluidTankInfo[]{new FluidTankInfo(tank)};
    }

    @Override
    public void readFromNBT(CompoundTag nbt, String str) {
        tank.readFromNBT(McRegistries.access(), nbt.getCompound(str + "tank"));
        fluid_heat_mb = nbt.getFloat(str + "fhm");
    }

    @Override
    public void writeToNBT(CompoundTag nbt, String str) {
        CompoundTag t = new CompoundTag();
        tank.writeToNBT(McRegistries.access(), t);
        nbt.put(str + "tank", t);
        nbt.putFloat(str + "fhm", fluid_heat_mb);
    }
}
