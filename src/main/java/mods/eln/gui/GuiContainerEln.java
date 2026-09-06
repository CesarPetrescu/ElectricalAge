package mods.eln.gui;

import mods.eln.client.Keyboard;
import mods.eln.client.gl.FixedFunction;
import mods.eln.gui.GuiTextFieldEln.GuiTextFieldElnObserver;
import mods.eln.gui.IGuiObject.IGuiObjectObserver;
import mods.eln.gui.ISlotSkin.SlotSkin;
import mods.eln.misc.UtilsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.neoforged.fml.util.ObfuscationReflectionHelper;

import java.util.ArrayList;

/**
 * Base of the mod's inventory screens; see {@link GuiScreenEln} for the API it keeps.
 */
public abstract class GuiContainerEln extends AbstractContainerScreen<AbstractContainerMenu> implements IGuiObjectObserver, GuiTextFieldElnObserver {

    public GuiHelperContainer helper;

    protected abstract GuiHelperContainer newHelper();

    static final ResourceLocation slotSkin = ResourceLocation.parse("textures/gui/container/furnace.png");

    public GuiContainerEln(AbstractContainerMenu par1Container) {
        super(par1Container, Minecraft.getInstance().player.getInventory(), Component.empty());
    }

    public void add(IGuiObject object) {
        helper.add(object);
    }

    @Override
    protected void init() {
        helper = newHelper();
        imageWidth = helper.xSize;
        imageHeight = helper.ySize;
        super.init();
        apply(helper);
        initGui();
    }

    /**
     * Lays the 36 player-inventory slots out at the helper's inventory offset, as 1.7.10 did.
     * The containers add them at (0, 0) because only the screen knows its layout. Slot.x/y are
     * final since 1.14; the client-side menu is this screen's own object, so they are set through
     * reflection once, at init.
     */
    void apply(GuiHelperContainer helper) {
        int n = menu.slots.size();
        for (int idx = n - 36; idx < n; idx++) {
            Slot s = menu.slots.get(idx);
            int x = (idx - (n - 36)) % 9 * 18;
            int y = (idx - (n - 36)) / 9 * 18;
            if (idx >= n - 9) {
                y = 58;
                x = (idx - (n - 9)) * 18;
            }
            ObfuscationReflectionHelper.setPrivateValue(Slot.class, s, x + helper.xInv, "x");
            ObfuscationReflectionHelper.setPrivateValue(Slot.class, s, y + helper.yInv, "y");
        }
    }

    /** 1.7.10's initGui; screens override it and call super. */
    public void initGui() {
    }

    public GuiTextFieldEln newGuiTextField(int x, int y, int width) {
        GuiTextFieldEln o = helper.newGuiTextField(x, y, width);
        o.setObserver(this);
        return o;
    }

    public GuiTextFieldEln newGuiTextField(int x, int y, int width, int maxLength) {
        GuiTextFieldEln o = helper.newGuiTextField(x, y, width, maxLength);
        o.setObserver(this);
        return o;
    }

    public GuiButtonEln newGuiButton(int x, int y, int width, String name) {
        GuiButtonEln o = helper.newGuiButton(x, y, width, name);
        o.setObserver(this);
        return o;
    }

    public GuiHorizontalTrackBar newGuiHorizontalTrackBar(int x, int y, int width, int height) {
        GuiHorizontalTrackBar o = helper.newGuiHorizontalTrackBar(x, y, width, height);
        o.setObserver(this);
        return o;
    }

    public GuiVerticalTrackBar newGuiVerticalTrackBar(int x, int y, int width, int height) {
        GuiVerticalTrackBar o = helper.newGuiVerticalTrackBar(x, y, width, height);
        o.setObserver(this);
        return o;
    }

    public GuiVerticalTrackBarHeat newGuiVerticalTrackBarHeat(int x, int y, int width, int height) {
        GuiVerticalTrackBarHeat o = helper.newGuiVerticalTrackBarHeat(x, y, width, height);
        o.setObserver(this);
        return o;
    }

    public GuiVerticalProgressBar newGuiVerticalProgressBar(int x, int y, int width, int height) {
        return helper.newGuiVerticalProgressBar(x, y, width, height);
    }

    public void drawTexturedModalRectEln(int x, int y, int u, int v, int width, int height) {
        helper.drawTexturedModalRect(x, y, u, v, width, height);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        helper.keyTyped(codePoint, 0);
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == Keyboard.KEY_RETURN) helper.keyTyped('\r', keyCode);
        else if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT)
            helper.keyTyped('\0', keyCode);
        // A focused text field owns the keyboard (the inventory key must not close the screen).
        if (keyCode != Keyboard.KEY_ESCAPE && helper.hasFocusedTextField()) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double x, double y, int code) {
        helper.mouseClicked((int) x, (int) y, code);
        return super.mouseClicked(x, y, code);
    }

    @Override
    public boolean mouseReleased(double x, double y, int witch) {
        helper.mouseMovedOrUp((int) x, (int) y, witch);
        return super.mouseReleased(x, y, witch);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float f) {
        Gui.begin(graphics);
        FixedFunction.beginGui(graphics);
        try {
            super.render(graphics, x, y, f);
            renderTooltip(graphics, x, y);
        } finally {
            FixedFunction.finish();
            Gui.end();
        }
    }

    @Override
    public void textFieldNewValue(GuiTextFieldEln textField, String value) {
        guiObjectEvent(textField);
    }

    @Override
    public void guiObjectEvent(IGuiObject object) {
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float f, int mx, int my) {
        preDraw(f, mx, my);
        helper.mouseMove(mx, my);
        helper.draw(mx, my, f);
        UtilsClient.bindTexture(slotSkin);

        for (Slot slot : menu.slots) {
            SlotSkin skin = SlotSkin.none;
            if (slot instanceof ISlotSkin) skin = ((ISlotSkin) slot).getSlotSkin();
            switch (skin) {
                case medium -> drawTexturedModalRectEln(slot.x - 1, slot.y - 1, 55, 16, 73 - 55, 34 - 16);
                case big -> drawTexturedModalRectEln(slot.x - 5, slot.y - 5, 111, 30, 137 - 111, 56 - 30);
                default -> {
                }
            }
        }
        postDraw(f, mx, my);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mx, int my) {
        // 1.21 draws the labels in GUI-local coordinates; the mod's foreground layer draws in screen
        // coordinates like 1.7.10 did, so undo the translation for it.
        graphics.pose().pushPose();
        graphics.pose().translate(-leftPos, -topPos, 0);
        helper.draw2(mx, my);
        ArrayList<String> list = new ArrayList<String>();

        for (Slot slot : menu.slots) {
            if (!slot.hasItem()
                && mx - leftPos >= slot.x && my - topPos >= slot.y
                && mx - leftPos < slot.x + 17 && my - topPos < slot.y + 17) {
                list.clear();
                if (slot instanceof ISlotWithComment) {
                    ((ISlotWithComment) slot).getComment(list);
                    int strWidth = 0;
                    for (String str : list) {
                        int size = font.width(str);
                        if (size > strWidth) strWidth = size;
                    }
                    int xOffset = 0;
                    if (leftPos + slot.x + strWidth + 30 > this.width) {
                        xOffset -= strWidth + 20;
                    }
                    if (!list.isEmpty())
                        helper.drawHoveringText(list, mx - leftPos + xOffset, my - topPos, font);
                }
            }
        }
        graphics.pose().popPose();
    }

    protected void preDraw(float f, int x, int y) {
    }

    protected void postDraw(float f, int x, int y) {
        if (helper.background != null)
            UtilsClient.bindTexture(helper.background);
    }

    protected void drawString(int x, int y, String str) {
        drawString(x, y, 4210752, str);
    }

    protected void drawString(int x, int y, int color, String str) {
        helper.drawString(x, y, color, str);
    }
}
