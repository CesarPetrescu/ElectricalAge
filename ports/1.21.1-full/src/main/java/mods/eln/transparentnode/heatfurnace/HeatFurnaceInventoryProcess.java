package mods.eln.transparentnode.heatfurnace;

import mods.eln.generic.GenericItemUsingDamage;
import mods.eln.item.ThermalIsolatorElement;
import mods.eln.misc.INBTTReady;
import mods.eln.misc.Utils;
import mods.eln.server.SaveConfig;
import mods.eln.sim.IProcess;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class HeatFurnaceInventoryProcess implements IProcess, INBTTReady {

    HeatFurnaceElement furnace;
    //double combustibleEnergyMax = 40000 * 2;
    double combustibleBuffer = 0;

    public HeatFurnaceInventoryProcess(HeatFurnaceElement furnace) {
        this.furnace = furnace;
    }

    @Override
    public void process(double time) {
        ItemStack combustibleStack = furnace.inventory.getStackInSlot(HeatFurnaceContainer.combustibleId);
        ItemStack combustionChamberStack = furnace.inventory.getStackInSlot(HeatFurnaceContainer.combustrionChamberId);
        ItemStack isolatorChamberStack = furnace.inventory.getStackInSlot(HeatFurnaceContainer.isolatorId);

        double isolationFactor = 1;
        if (isolatorChamberStack != null && !isolatorChamberStack.isEmpty()) {
            ThermalIsolatorElement isolatorDescriptor = (ThermalIsolatorElement) ThermalIsolatorElement.getDescriptor(isolatorChamberStack);
            if (isolatorDescriptor != null && furnace.thermalLoad.Tc > isolatorDescriptor.getTmax()) {
                furnace.inventory.decrStackSize(HeatFurnaceContainer.isolatorId, 1);
            } else if (isolatorChamberStack.getItem() instanceof GenericItemUsingDamage) {
                ThermalIsolatorElement iso = (ThermalIsolatorElement) ((GenericItemUsingDamage) isolatorChamberStack.getItem()).getDescriptor(isolatorChamberStack);
                if (iso != null) {
                    isolationFactor = iso.getConductionFactor();
                }
            }
        }
        furnace.thermalLoad.setRp(furnace.descriptor.thermal.Rp / isolationFactor);

        int combustionChamberNbr = combustionChamberStack == null || combustionChamberStack.isEmpty() ? 0 : combustionChamberStack.getCount();
        furnace.furnaceProcess.nominalPower = furnace.descriptor.nominalPower + furnace.descriptor.combustionChamberPower * combustionChamberNbr;

        boolean heatFurnaceFuelEnabled = SaveConfig.instance == null || SaveConfig.instance.heatFurnaceFuel;

        if (furnace.getTakeFuel()) {
            if (!heatFurnaceFuelEnabled) {
                combustibleBuffer = furnace.furnaceProcess.nominalCombustibleEnergy;
            } else if (!combustibleStack.isEmpty()) {
                double itemEnergy = Utils.getItemEnergie(combustibleStack);
                //Utils.println("Furnace item energy: " + itemEnergy);
                if (itemEnergy != 0) {
                    if (furnace.furnaceProcess.combustibleEnergy + combustibleBuffer < furnace.furnaceProcess.nominalCombustibleEnergy) {
                        combustibleBuffer += itemEnergy;
                        furnace.inventory.decrStackSize(HeatFurnaceContainer.combustibleId, 1);
                        if (combustibleStack.getItem().getTranslationKey().toLowerCase().contains("bucket")) {
                            furnace.inventory.setInventorySlotContents(HeatFurnaceContainer.combustibleId, new ItemStack(Items.BUCKET));
                        }
                    }
                }
            }
        }

        if (furnace.furnaceProcess.combustibleEnergy + combustibleBuffer < furnace.furnaceProcess.nominalCombustibleEnergy) {
            furnace.furnaceProcess.combustibleEnergy += combustibleBuffer;
            combustibleBuffer = 0;
        } else {
            double delta = furnace.furnaceProcess.nominalCombustibleEnergy - furnace.furnaceProcess.combustibleEnergy;
            furnace.furnaceProcess.combustibleEnergy += delta;
            combustibleBuffer -= delta;
        }
        
        // Ensure combustibleEnergy is never negative and capped
        if (furnace.furnaceProcess.combustibleEnergy < 0) furnace.furnaceProcess.combustibleEnergy = 0;
        if (furnace.furnaceProcess.combustibleEnergy > furnace.furnaceProcess.nominalCombustibleEnergy) 
            furnace.furnaceProcess.combustibleEnergy = furnace.furnaceProcess.nominalCombustibleEnergy;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt, String str) {
        combustibleBuffer = nbt.getDouble(str + "HFIP" + "combustribleBuffer");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt, String str) {
        nbt.setDouble(str + "HFIP" + "combustribleBuffer", combustibleBuffer);
        return nbt;
    }
}
