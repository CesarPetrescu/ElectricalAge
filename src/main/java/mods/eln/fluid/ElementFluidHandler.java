package mods.eln.fluid;

import mods.eln.misc.INBTTReady;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraftforge.fluids.*;

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
        if (fluid_heat_mb == 0 && tank.getFluid() != null) setHeatEnergyPerMilliBucket(tank.getFluid().getFluid());
        return fluid_heat_mb;
    }

    private void setHeatEnergyPerMilliBucket(Fluid fluid) {
        fluid_heat_mb = (float) FuelRegistry.INSTANCE.heatEnergyPerMilliBucket(fluid);
    }

    @Override
    public int fill(Direction from, FluidStack resource, boolean doFill) {
        if (tank.getFluidAmount() > 0) {
            // No change in type of fluid.
            return tank.fill(resource, doFill);
        } else if (whitelist == null) {
            // May have a different fluid.
            setHeatEnergyPerMilliBucket(resource.getFluid());
            return tank.fill(resource, doFill);
        } else {
            // 1.12.2: fluids have no integer id; FluidRegistry hands out singletons, so compare instances.
            Fluid resourceFluid = resource.getFluid();
            for (int i = 0; i < whitelist.length; i++) {
                if (whitelist[i] == resourceFluid) {
                    setHeatEnergyPerMilliBucket(resource.getFluid());
                    return tank.fill(resource, doFill);
                }
            }
            return 0;
        }
    }

    @Override
    public FluidStack drain(Direction from, FluidStack resource, boolean doDrain) {
        if (resource.isFluidEqual(tank.getFluid()))
            return tank.drain(resource.amount, doDrain);
        else
            return null;
    }

    @Override
    public FluidStack drain(Direction from, int maxDrain, boolean doDrain) {
        return tank.drain(maxDrain, doDrain);
    }

    @Override
    public boolean canFill(Direction from, Fluid fluid) {
        if (tank.getFluidAmount() > 0) {
            return tank.getFluid().getFluid() == fluid;
        } else {
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
        return new FluidTankInfo[]{tank.getInfo()};
    }

    @Override
    public void readFromNBT(CompoundTag nbt, String str) {
        tank.readFromNBT(nbt.getCompound(str + "tank"));
        fluid_heat_mb = nbt.getFloat(str + "fhm");
    }

    @Override
    public void writeToNBT(CompoundTag nbt, String str) {
        CompoundTag t = new CompoundTag();
        tank.writeToNBT(t);
        nbt.put(str + "tank", t);
        nbt.putFloat(str + "fhm", fluid_heat_mb);
    }
}
