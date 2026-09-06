package mods.eln.sixnode.thermalsensor;

import mods.eln.gui.*;
import mods.eln.sim.PhysicalConstant;
import mods.eln.sixnode.electricalsensor.ElectricalSensorElement;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;

import java.text.NumberFormat;
import java.text.ParseException;

import static mods.eln.i18n.I18N.tr;

public class ThermalSensorGui extends GuiContainerEln {

    GuiButtonEln validate, temperatureType, powerType;
    GuiTextFieldEln lowValue, highValue;
    ThermalSensorRender render;

    public ThermalSensorGui(EntityPlayer player, IInventory inventory, ThermalSensorRender render) {
        super(new ThermalSensorContainer(player, inventory, render.descriptor.temperatureOnly));
        this.render = render;
    }

    @Override
    public void initGui() {
        super.initGui();

        if (!render.descriptor.temperatureOnly) {
            powerType = newGuiButton(8, 8, 70, tr("Power"));
            powerType.setComment(0, tr("Measure power flow (W)"));
            temperatureType = newGuiButton(176 - 8 - 70, 8, 70, tr("Temperature"));
            temperatureType.setComment(0, tr("Measure temperature (°C)"));

            int x = 10;
            int y = 35;
            highValue = newGuiTextField(x, y, 50);
            highValue.setText(render.highValue);
            highValue.setComment(tr("Value for 100 percent output\n(High setpoint)").split("\n"));

            lowValue = newGuiTextField(x, y + 22, 50);
            lowValue.setText(render.lowValue);
            lowValue.setComment(tr("Value for 0 percent output\n(Low setpoint)").split("\n"));

            validate = newGuiButton(x + 50 + 10, y + 10, 50, tr("Apply"));
            validate.setComment(0, tr("Save setpoints"));
        } else {
            int x = 10;
            int y = 10;
            highValue = newGuiTextField(x, y, 50);
            highValue.setText(render.highValue);
            highValue.setComment(tr("Temperature for 100 percent output\n(High setpoint)").split("\n"));

            lowValue = newGuiTextField(x, y + 20, 50);
            lowValue.setText(render.lowValue);
            lowValue.setComment(tr("Temperature for 0 percent output\n(Low setpoint)").split("\n"));

            validate = newGuiButton(x + 50 + 10, y + 10, 50, tr("Apply"));
            validate.setComment(0, tr("Save setpoints"));
        }
    }

    @Override
    public void guiObjectEvent(IGuiObject object) {
        super.guiObjectEvent(object);
        if (object == validate) {
            float lowValueFloat, highValueFloat;

            try {
                if (lowValue == null || highValue == null) return;
                lowValueFloat = NumberFormat.getInstance().parse(lowValue.getText()).floatValue();
                highValueFloat = NumberFormat.getInstance().parse(highValue.getText()).floatValue();
                render.clientSetFloat(ElectricalSensorElement.setValueId, lowValueFloat, highValueFloat);
            } catch (ParseException e) {
            }
        } else if (temperatureType != null && object == temperatureType) {
            render.clientSetByte(ThermalSensorElement.setTypeOfSensorId, ThermalSensorElement.temperatureType);
        } else if (powerType != null && object == powerType) {
            render.clientSetByte(ThermalSensorElement.setTypeOfSensorId, ThermalSensorElement.powerType);
        }
    }

    @Override
    protected void preDraw(float f, int x, int y) {
        super.preDraw(f, x, y);
        if (!render.descriptor.temperatureOnly) {
            if (temperatureType != null && powerType != null) {
                powerType.enabled = render.typeOfSensor != ThermalSensorElement.powerType;
                temperatureType.enabled = render.typeOfSensor != ThermalSensorElement.temperatureType;
            }
        }
    }

    @Override
    protected GuiHelperContainer newHelper() {
        if (!render.descriptor.temperatureOnly)
            return new HelperStdContainer(this);
        else
            return new GuiHelperContainer(this, 176, 166 - 30, 8, 84 - 30);
    }
}
