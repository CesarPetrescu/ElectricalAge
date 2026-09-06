package mods.eln.transparentnode.waterturbine;

import mods.eln.misc.INBTTReady;
import mods.eln.misc.RcRcInterpolator;
import mods.eln.misc.Utils;
import mods.eln.sim.IProcess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.nbt.CompoundTag;

public class WaterTurbineSlowProcess implements IProcess, INBTTReady {

    WaterTurbineElement turbine;

    public WaterTurbineSlowProcess(WaterTurbineElement turbine) {
        this.turbine = turbine;
    }

    double refreshTimeout = 0;
    double refreshPeriode = 0.2;

    RcRcInterpolator filter = new RcRcInterpolator(2, 2);

    @Override
    public void process(double time) {
        WaterTurbineDescriptor d = turbine.descriptor;

        refreshTimeout -= time;
        if (refreshTimeout < 0) {
            refreshTimeout = refreshPeriode;
            double waterFactor = getWaterFactor();
            if (waterFactor < 0) {
                filter.setValue((float) (filter.get() * (1 - 0.5f * time)));
            } else {
                filter.setTarget((float) (waterFactor * d.nominalPower));
                filter.step((float) time);
            }

            turbine.powerSource.setPower(filter.get());
        }
    }

    double getWaterFactor() {
        //Block b = turbine.waterCoord.getBlock();
        double time = 0;
        if (turbine.waterCoord.getBlockExist()) {
            Block block = turbine.waterCoord.getBlock();
            // 1.13+: the water level is the fluid state; a source block (level 0 in 1.7.10 terms) gives no flow.
            net.minecraft.world.level.material.FluidState fluid = turbine.waterCoord.getBlockState().getFluidState();
            if (block != Blocks.WATER) return -1;
            if (fluid.isSource()) return 0;
            time = Utils.getWorldTime(turbine.world());
        }

        double timeFactor = 1 + 0.2 * Math.sin((time - 0.20) * Math.PI * 2);
        double weatherFactor = 1 + Utils.getWeatherNoLoad(turbine.coordinate().dimension) * 2;
        return timeFactor * weatherFactor;
    }

    @Override
    public void readFromNBT(CompoundTag nbt, String str) {
        filter.readFromNBT(nbt, str + "filter");
    }

    @Override
    public void writeToNBT(CompoundTag nbt, String str) {

        filter.writeToNBT(nbt, str + "filter");

    }
}
