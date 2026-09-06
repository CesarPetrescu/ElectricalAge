package mods.eln.sixnode.electricaldatalogger;

import mods.eln.Eln;
import mods.eln.sim.IProcess;
import net.minecraft.item.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ElectricalDataLoggerProcess implements IProcess {

    ElectricalDataLoggerElement e;

    public ElectricalDataLoggerProcess(ElectricalDataLoggerElement e) {
        this.e = e;
    }

    @Override
    public void process(double time) {
        if (!e.pause) {
            e.timeToNextSample -= time;
            byte value = (byte) (e.inputGate.getNormalized() * 255.5 - 128);
            e.sampleStack += value;
            e.sampleStackNbr++;
        }
        // TODO(1.12)... er, does literally anyone ever use this?
/*
        if (e.printToDo) {
            ItemStack paperStack = e.inventory.getStackInSlot(ElectricalDataLoggerContainer.paperSlotId);
            ItemStack printStack = e.inventory.getStackInSlot(ElectricalDataLoggerContainer.printSlotId);
            if (paperStack != null && printStack == null) {
                e.inventory.decrStackSize(ElectricalDataLoggerContainer.paperSlotId, 1);
                ItemStack print = Eln.dataLogsPrintDescriptor.newItemStack(1);
                Eln.dataLogsPrintDescriptor.initializeStack(print, e.logs);
                e.inventory.setInventorySlotContents(ElectricalDataLoggerContainer.printSlotId, print);
            }
            e.printToDo = false;
        }
*/
        if (e.timeToNextSample <= 0.0) {
            e.timeToNextSample += e.logs.samplingPeriod;
            if (e.timeToNextSample < 0.05) e.timeToNextSample = 0.05; // Cap sampling period

            byte value;
            if (e.sampleStackNbr > 0) {
                value = (byte) (e.sampleStack / e.sampleStackNbr);
            } else {
                value = (byte) (e.inputGate.getNormalized() * 255.5 - 128);
            }
            e.sampleStackReset();
            e.logs.write(value);

            ByteArrayOutputStream bos = new ByteArrayOutputStream(64);
            DataOutputStream packet = new DataOutputStream(bos);

            e.preparePacketForClient(packet);

            try {
                packet.writeByte(e.toClientLogsAdd);
                packet.write(value);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            //p.add("D");
            e.sendPacketToAllClient(bos);
        }
    }
}
