package mods.eln.sixnode.electricalsensor;

import mods.eln.gui.*;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;

import java.text.NumberFormat;
import java.text.ParseException;

import static mods.eln.i18n.I18N.tr;

public class ElectricalSensorGui extends GuiContainerEln {

    GuiButtonEln validate, voltageType, currentType, powerType, dirType;
    GuiTextFieldEln lowValue, highValue;
    ElectricalSensorRender render;

    public ElectricalSensorGui(EntityPlayer player, IInventory inventory, ElectricalSensorRender render) {
        super(new ElectricalSensorContainer(player, inventory, render.descriptor));
        this.render = render;
    }

    @Override
    public void initGui() {
        super.initGui();

        if (!render.descriptor.voltageOnly) {
            voltageType = newGuiButton(8, 8, 50, tr("Voltage"));
            voltageType.setComment(0, tr("Measure voltage (V)"));
            currentType = newGuiButton(8, 8 + 22, 50, tr("Current"));
            currentType.setComment(0, tr("Measure current (A)"));
            powerType = newGuiButton(8, 8 + 44, 50, tr("Power"));
            powerType.setComment(0, tr("Measure power (W)"));
            
            dirType = newGuiButton(8 + 50 + 4, 8, 50, "");
            dirType.setComment(0, tr("Change measurement direction"));

            int x = 8 + 50 + 4;
            int y = 32;
            highValue = newGuiTextField(x, y, 50);
            highValue.setText(render.highValue);
            highValue.setComment(tr("Value for 100 percent output\n(High setpoint)").split("\n"));

            lowValue = newGuiTextField(x, y + 22, 50);
            lowValue.setText(render.lowValue);
            lowValue.setComment(tr("Value for 0 percent output\n(Low setpoint)").split("\n"));

            validate = newGuiButton(x + 50 + 4, y + 10, 50, tr("Apply"));
            validate.setComment(0, tr("Save setpoints"));
        } else {
            int x = 10;
            int y = 10;
            highValue = newGuiTextField(x, y, 50);
            highValue.setText(render.highValue);
            highValue.setComment(tr("Voltage for 100 percent output\n(High setpoint)").split("\n"));

            lowValue = newGuiTextField(x, y + 20, 50);
            lowValue.setText(render.lowValue);
            lowValue.setComment(tr("Voltage for 0 percent output\n(Low setpoint)").split("\n"));

            validate = newGuiButton(x + 50 + 10, y + 10, 50, tr("Apply"));
            validate.setComment(0, tr("Save setpoints"));
        }
    }

    @Override
    public void guiObjectEvent(IGuiObject object) {
        super.guiObjectEvent(object);
        if (object == validate) {
            float lowVoltage, highVoltage;

            try {
                if (lowValue == null || highValue == null) return;
                lowVoltage = NumberFormat.getInstance().parse(lowValue.getText()).floatValue();
                highVoltage = NumberFormat.getInstance().parse(highValue.getText()).floatValue();
                render.clientSetFloat(ElectricalSensorElement.setValueId, lowVoltage, highVoltage);
            } catch (ParseException e) {
            }
        } else if (currentType != null && object == currentType) {
            render.clientSetByte(ElectricalSensorElement.setTypeOfSensorId, (byte) ElectricalSensorElement.currantType);
        } else if (voltageType != null && object == voltageType) {
            render.clientSetByte(ElectricalSensorElement.setTypeOfSensorId, (byte) ElectricalSensorElement.voltageType);
        } else if (powerType != null && object == powerType) {
            render.clientSetByte(ElectricalSensorElement.setTypeOfSensorId, (byte) ElectricalSensorElement.powerType);
        } else if (dirType != null && object == dirType) {
            render.dirType = (byte) ((render.dirType + 1) % 3);
            render.clientSetByte(ElectricalSensorElement.setDirType, render.dirType);
        }
    }

    @Override
    protected void preDraw(float f, int x, int y) {
        super.preDraw(f, x, y);
        if (!render.descriptor.voltageOnly) {
            if (dirType != null) {
                switch (render.dirType) {
                    case ElectricalSensorElement.dirNone:
                        dirType.displayString = "\u00a72\u25CF\u00a77 <=> \u00a71\u25CF";
                        break;
                    case ElectricalSensorElement.dirAB:
                        dirType.displayString = "\u00a72\u25CF\u00a77 => \u00a71\u25CF";
                        break;
                    case ElectricalSensorElement.dirBA:
                        dirType.displayString = "\u00a72\u25CF\u00a77 <= \u00a71\u25CF";
                        break;
                }
            }

            if (currentType != null && voltageType != null && powerType != null) {
                voltageType.enabled = render.typeOfSensor != ElectricalSensorElement.voltageType;
                currentType.enabled = render.typeOfSensor != ElectricalSensorElement.currantType;
                powerType.enabled = render.typeOfSensor != ElectricalSensorElement.powerType;
            }
        }
    }

    @Override
    protected GuiHelperContainer newHelper() {
        if (!render.descriptor.voltageOnly)
            return new HelperStdContainer(this);
        else
            return new GuiHelperContainer(this, 176, 166 - 30, 8, 84 - 30);
    }
}
