package mods.eln.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;

/** An {@link EditBox} driven by the mod's {@link IGuiObject} event loop; keeps 1.7.10's getText/setText. */
public class GuiTextFieldEln extends EditBox implements IGuiObject {

    int xPos, yPos, width, height;
    GuiTextFieldElnObserver observer;
    GuiHelper helper;

    private boolean enabled = true;

    IGuiObjectObserver iGuiObjectObserver;

    public GuiTextFieldEln(Font par1FontRenderer, int x, int y, int w, int h, GuiHelper helper) {
        this(par1FontRenderer, x, y, w, h, helper, 150);
    }

    public GuiTextFieldEln(Font par1FontRenderer, int x, int y, int w, int h, GuiHelper helper, int maxLength) {
        super(par1FontRenderer, x, y, w, h, Component.empty());
        setTextColor(-1);
        setTextColorUneditable(-1);
        setBordered(true);
        setMaxLength(maxLength);
        xPos = x;
        yPos = y;
        width = w;
        height = h;
        this.helper = helper;
    }

    public GuiTextFieldEln(Font par1FontRenderer, int x, int y, int w, GuiHelper helper) {
        this(par1FontRenderer, x, y, w, 12, helper);
    }

    @Override
    public int getYMax() {
        return yPos + height;
    }

    public void setObserver(GuiTextFieldElnObserver observer) {
        this.observer = observer;
    }

    public interface GuiTextFieldElnObserver {
        void textFieldNewValue(GuiTextFieldEln textField, String value);
    }

    ArrayList<String> comment = new ArrayList<String>();

    public void setComment(String[] comment) {
        for (String str : comment) {
            this.comment.add(str);
        }
    }

    public void setComment(int line, String comment) {
        if (this.comment.size() < line + 1)
            this.comment.add(line, comment);
        else
            this.comment.set(line, comment);
    }

    // 1.7.10 names
    public String getText() {
        return getValue();
    }

    public void setText(String text) {
        setValue(text);
    }

    public void setMaxStringLength(int length) {
        setMaxLength(length);
    }

    public void setText(float value) {
        if (Math.abs(value) < 1000)
            setText(String.format("%3.2f", value));
        else
            setText(String.format("%3.0f", value));
    }

    public void setText(int value) {
        setText(String.format("%d", value));
    }

    public void setEnabled(boolean par1) {
        enabled = par1;
        setEditable(par1);
    }

    public boolean getEnabled() {
        return enabled;
    }

    public boolean textboxKeyTyped(char par1, int par2) {
        if (getEnabled() && this.isFocused() && par1 == '\r') {
            setFocused(false);
            return true;
        }
        if (par1 != 0) return charTyped(par1, 0);
        if (par2 != 0) return keyPressed(par2, 0, 0);
        return false;
    }

    @Override
    public void setFocused(boolean par1) {
        if (isFocused() == true && par1 == false) {
            if (observer != null)
                observer.textFieldNewValue(this, this.getText());
            if (iGuiObjectObserver != null)
                iGuiObjectObserver.guiObjectEvent(this);
        }
        super.setFocused(par1);
    }

    @Override
    public void idraw(int x, int y, float f) {
        GuiGraphics g = Gui.graphics();
        if (g == null) return;
        setX(xPos);
        setY(yPos);
        render(g, x, y, f);
    }

    @Override
    public boolean ikeyTyped(char key, int code) {
        return this.textboxKeyTyped(key, code);
    }

    @Override
    public void imouseClicked(int x, int y, int code) {
        boolean inside = x >= xPos && y >= yPos && x < xPos + width && y < yPos + height;
        if (inside != isFocused()) setFocused(inside);
        if (inside) this.mouseClicked(x, y, code);
    }

    @Override
    public void imouseMove(int x, int y) {
    }

    @Override
    public void imouseMovedOrUp(int x, int y, int witch) {
    }

    @Override
    public void idraw2(int x, int y) {
        if (!isFocused() && visible && x >= xPos && y >= yPos && x < xPos + width && y < yPos + height)
            helper.drawHoveringText(comment, x, y, Minecraft.getInstance().font);

    }

    @Override
    public void translate(int x, int y) {
        this.xPos += x;
        this.yPos += y;
    }

    public int getHeight() {
        return height;
    }

    public void setGuiObserver(IGuiObjectObserver iGuiObjectObserver) {
        this.iGuiObjectObserver = iGuiObjectObserver;
    }
}
