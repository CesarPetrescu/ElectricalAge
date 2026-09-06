package mods.eln.gui;

import mods.eln.client.Keyboard;
import mods.eln.client.gl.FixedFunction;
import mods.eln.gui.GuiTextFieldEln.GuiTextFieldElnObserver;
import mods.eln.gui.IGuiObject.IGuiObjectObserver;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base of the mod's non-inventory screens. The 1.7.10 protected API the ~40 screens override
 * ({@code initGui}, {@code preDraw}/{@code postDraw}, {@code guiObjectEvent}, {@code newHelper})
 * is kept; this class maps it onto 1.21's {@link Screen}.
 */
public abstract class GuiScreenEln extends Screen implements GuiTextFieldElnObserver, IGuiObjectObserver {

    protected GuiHelper helper;

    protected GuiScreenEln() {
        super(Component.empty());
    }

    protected abstract GuiHelper newHelper();

    @Override
    protected void init() {
        super.init();
        initGui();
    }

    /** 1.7.10's initGui; screens override it and call super. */
    public void initGui() {
        helper = newHelper();
    }

    public GuiTextFieldEln newGuiTextField(int x, int y, int width) {
        GuiTextFieldEln o = helper.newGuiTextField(x, y, width);
        o.setObserver(this);
        return o;
    }

    public GuiButtonEln newGuiButton(int x, int y, int width, String name) {
        GuiButtonEln o = helper.newGuiButton(x, y, width, name);
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

    public GuiVerticalCustomValuesBar newGuiVerticalCustomValuesBar(int x, int y, int width, int height, Float[] values) {
        GuiVerticalCustomValuesBar o = helper.newGuiVerticalCustomValuesBar(x, y, width, height, values);
        o.setObserver(this);
        return o;
    }

    public GuiVerticalProgressBar newGuiVerticalProgressBar(int x, int y, int width, int height) {
        return helper.newGuiVerticalProgressBar(x, y, width, height);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        helper.keyTyped(codePoint, 0);
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Editing keys reach the widgets as a key code with no character, like LWJGL 2 did.
        if (keyCode == Keyboard.KEY_RETURN) helper.keyTyped('\r', keyCode);
        else if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT)
            helper.keyTyped('\0', keyCode);
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
            preDraw(f, x, y);
            helper.mouseMove(x, y);
            helper.draw(x, y, f);
            postDraw(f, x, y);
            helper.draw2(x, y);
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

    protected void preDraw(float f, int x, int y) {
    }

    protected void postDraw(float f, int x, int y) {
    }

    protected void drawString(int x, int y, String str) {
        drawString(x, y, 4210752, str);
    }

    protected void drawString(int x, int y, int color, String str) {
        helper.drawString(x, y, color, str);
    }

    protected void add(IGuiObject o) {
        helper.add(o);
    }

    protected void remove(IGuiObject o) {
        helper.remove(o);
    }
}
