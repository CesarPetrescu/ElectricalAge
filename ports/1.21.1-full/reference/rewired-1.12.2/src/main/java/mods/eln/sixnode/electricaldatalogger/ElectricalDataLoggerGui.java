package mods.eln.sixnode.electricaldatalogger;

import mods.eln.gui.*;
import mods.eln.gui.GuiTextFieldEln.GuiTextFieldElnObserver;
import mods.eln.misc.Color;
import mods.eln.misc.UtilsClient;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import org.lwjgl.opengl.GL11;

import java.text.NumberFormat;
import java.text.ParseException;

import static mods.eln.i18n.I18N.tr;

public class ElectricalDataLoggerGui extends GuiContainerEln implements GuiTextFieldElnObserver {

    GuiButtonEln resetBt, voltageType, energyType, currentType, powerType, celsiusType, percentType, config, printBt, pause;
    GuiTextFieldEln samplingPeriod, maxValue, minValue, yCursorValue;
    ElectricalDataLoggerRender render;

    enum State {display, config}

    State state = State.display;

    public ElectricalDataLoggerGui(EntityPlayer player, IInventory inventory, ElectricalDataLoggerRender render) {
        super(new ElectricalDataLoggerContainer(player, inventory));
        this.render = render;
    }

    void displayEntry() {
        if (config != null) {
            config.displayString = tr("Configuration");
            config.visible = true;
        }
        if (pause != null) pause.visible = true;
        if (resetBt != null) resetBt.visible = true;
        if (voltageType != null) voltageType.visible = false;
        if (energyType != null) energyType.visible = false;
        if (percentType != null) percentType.visible = false;
        if (currentType != null) currentType.visible = false;
        if (powerType != null) powerType.visible = false;
        if (celsiusType != null) celsiusType.visible = false;
        if (samplingPeriod != null) samplingPeriod.setVisible(false);
        if (maxValue != null) maxValue.setVisible(false);
        if (minValue != null) minValue.setVisible(false);
        if (printBt != null) printBt.visible = true;
        state = State.display;
    }

    void configEntry() {
        if (pause != null) pause.visible = false;
        if (config != null) {
            config.visible = true;
            config.displayString = tr("Back to display");
        }
        if (resetBt != null) resetBt.visible = false;
        if (printBt != null) printBt.visible = true;
        if (voltageType != null) voltageType.visible = true;
        if (energyType != null) energyType.visible = true;
        if (percentType != null) percentType.visible = true;
        if (currentType != null) currentType.visible = true;
        if (powerType != null) powerType.visible = true;
        if (celsiusType != null) celsiusType.visible = true;
        if (samplingPeriod != null) samplingPeriod.setVisible(true);
        if (maxValue != null) maxValue.setVisible(true);
        if (minValue != null) minValue.setVisible(true);
        state = State.config;
    }

    @Override
    public void initGui() {
        super.initGui();

        config = newGuiButton(176 / 2 - 50, 8 - 2, 100, "");
        config.setComment(0, tr("Toggle between display and configuration"));

        //@devs: Do not translate the following elements. Please.
        voltageType = newGuiButton(176 / 2 - 75 - 2, 8 + 20 + 2 - 2, 75, tr("Voltage [V]"));
        currentType = newGuiButton(176 / 2 + 2, 8 + 20 + 2 - 2, 75, tr("Current [A]"));
        powerType = newGuiButton(176 / 2 - 75 - 2, 8 + 40 + 4 - 2, 75, tr("Power [W]"));
        celsiusType = newGuiButton(176 / 2 + 2, 8 + 40 + 4 - 2, 75, tr("Temp. [*C]"));
        percentType = newGuiButton(176 / 2 - 75 - 2, 8 + 60 + 6 - 2, 75, tr("Percent [-] percent"));
        energyType = newGuiButton(176 / 2 + 2, 8 + 60 + 6 - 2, 75, tr("Energy [J]"));

        resetBt = newGuiButton(176 / 2 - 50, 8 + 20 + 2 - 2, 48, tr("Reset"));
        resetBt.setComment(0, tr("Clear all recorded data"));
        pause = newGuiButton(176 / 2 + 2, 8 + 20 + 2 - 2, 48, "");
        pause.setComment(0, tr("Pause/Resume data recording"));

        printBt = newGuiButton(176 / 2 - 48 / 2, 123, 48, tr("Print"));
        printBt.setComment(0, tr("Print current graph to paper"));

        samplingPeriod = newGuiTextField(10, 105, 50);
        samplingPeriod.setText(render.log.samplingPeriod);
        samplingPeriod.setComment(new String[]{tr("Sampling period (seconds)")});

        maxValue = newGuiTextField(176 - 50 - 10, 95, 50);
        maxValue.setText(render.log.maxValue);
        maxValue.setComment(new String[]{tr("Y-axis scale maximum")});

        minValue = newGuiTextField(176 - 50 - 10, 115, 50);
        minValue.setText(render.log.minValue);
        minValue.setComment(new String[]{tr("Y-axis scale minimum")});

        displayEntry();
    }

    @Override
    public void guiObjectEvent(IGuiObject object) {
        super.guiObjectEvent(object);
        try {
            if (object == resetBt) {
                render.clientSend(ElectricalDataLoggerElement.resetId);
            } else if (object == pause) {
                render.clientSend(ElectricalDataLoggerElement.tooglePauseId);
            } else if (object == printBt) {
                render.clientSend(ElectricalDataLoggerElement.printId);
            } else if (object == currentType) {
                render.clientSetByte(ElectricalDataLoggerElement.setUnitId, DataLogs.currentType);
            } else if (object == voltageType) {
                render.clientSetByte(ElectricalDataLoggerElement.setUnitId, DataLogs.voltageType);
            } else if (object == energyType) {
                render.clientSetByte(ElectricalDataLoggerElement.setUnitId, DataLogs.energyType);
            } else if (object == percentType) {
                render.clientSetByte(ElectricalDataLoggerElement.setUnitId, DataLogs.percentType);
            } else if (object == powerType) {
                render.clientSetByte(ElectricalDataLoggerElement.setUnitId, DataLogs.powerType);
            } else if (object == celsiusType) {
                render.clientSetByte(ElectricalDataLoggerElement.setUnitId, DataLogs.celsiusType);
            } else if (object == config) {
                switch (state) {
                    case config:
                        displayEntry();
                        break;
                    case display:
                        configEntry();
                        break;
                    default:
                        break;
                }
            } else if (object == maxValue) {
                render.clientSetFloat(ElectricalDataLoggerElement.setMaxValue, NumberFormat.getInstance().parse(maxValue.getText()).floatValue());
            } else if (object == minValue) {
                render.clientSetFloat(ElectricalDataLoggerElement.setMinValue, NumberFormat.getInstance().parse(minValue.getText()).floatValue());
            } else if (object == samplingPeriod) {
                float value = NumberFormat.getInstance().parse(samplingPeriod.getText()).floatValue();
                if (value < 0.05f) value = 0.05f;
                samplingPeriod.setText(value);

                render.clientSetFloat(ElectricalDataLoggerElement.setSamplingPeriodeId, value);
            }
        } catch (ParseException e) {
        }
    }

    @Override
    protected void preDraw(float f, int x, int y) {
        super.preDraw(f, x, y);
        if (powerType != null) powerType.enabled = true;
        if (currentType != null) currentType.enabled = true;
        if (voltageType != null) voltageType.enabled = true;
        if (celsiusType != null) celsiusType.enabled = true;
        if (percentType != null) percentType.enabled = true;
        if (energyType != null) energyType.enabled = true;

        switch (render.log.unitType) {
            case DataLogs.currentType:
                if (currentType != null) currentType.enabled = false;
                break;
            case DataLogs.voltageType:
                if (voltageType != null) voltageType.enabled = false;
                break;
            case DataLogs.powerType:
                if (powerType != null) powerType.enabled = false;
                break;
            case DataLogs.celsiusType:
                if (celsiusType != null) celsiusType.enabled = false;
                break;
            case DataLogs.percentType:
                if (percentType != null) percentType.enabled = false;
                break;
            case DataLogs.energyType:
                if (energyType != null) energyType.enabled = false;
                break;
        }

        if (render.pause) {
            if (pause != null) pause.displayString = Color.COLOR_DARK_YELLOW + "Paused";
        } else {
            if (pause != null) pause.displayString = Color.COLOR_BRIGHT_GREEN + "Running";
        }

        if (printBt != null) {
            boolean a = !inventorySlots.getSlot(ElectricalDataLoggerContainer.paperSlotId).getStack().isEmpty();
            boolean b = inventorySlots.getSlot(ElectricalDataLoggerContainer.printSlotId).getStack().isEmpty();
            printBt.enabled = a && b;
        }
    }

    @Override
    protected void postDraw(float f, int x, int y) {
        super.postDraw(f, x, y);
        final float bckrndMargin = 0.05f;

        if (state == State.display) {

            GL11.glPushMatrix();
            GL11.glTranslatef(guiLeft + 8, guiTop + 53, 0);
            GL11.glScalef(50, 50, 1f);

            GL11.glColor4f(0.15f, 0.15f, 0.15f, 1.0f);
            UtilsClient.disableTexture();
            UtilsClient.disableCulling();
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(-bckrndMargin, -bckrndMargin);
            GL11.glVertex2f(3.2f + bckrndMargin, -bckrndMargin);
            GL11.glVertex2f(3.2f + bckrndMargin, 1.2f + 3 * bckrndMargin);
            GL11.glVertex2f(-bckrndMargin, 1.2f + 3 * bckrndMargin);
            GL11.glEnd();
            UtilsClient.enableCulling();
            UtilsClient.enableTexture();

            GL11.glColor4f(render.descriptor.cr, render.descriptor.cg, render.descriptor.cb, 1);
            render.log.draw(2.9f, 1.2f, render.descriptor.textColor);
            GL11.glPopMatrix();
        }
    }

    @Override
    protected GuiHelperContainer newHelper() {
        return new GuiHelperContainer(this, 176, 230, 8, 148);
    }
}
