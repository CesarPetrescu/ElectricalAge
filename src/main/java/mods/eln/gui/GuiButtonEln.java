package mods.eln.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;

/**
 * A vanilla-looking button driven by the mod's {@link IGuiObject} event loop rather than the
 * screen's widget list (the helper positions and draws it, the observer gets the press).
 */
public class GuiButtonEln extends Button implements IGuiObject {

    IGuiObjectObserver observer;
    private boolean playPressSound = true;
    private boolean pressedInside = false;
    /** 1.7.10's public fields, kept for the screens that poke them. */
    public boolean enabled = true;
    public String displayString;

    public GuiButtonEln(int x, int y, int width, int height, String str) {
        super(x, y, width, height, Component.literal(str), b -> {
        }, DEFAULT_NARRATION);
        displayString = str;
    }

    GuiHelper helper;

    public void setHelper(GuiHelper helper) {
        this.helper = helper;
    }

    public void setObserver(IGuiObjectObserver observer) {
        this.observer = observer;
    }

    public GuiButtonEln setPlayPressSound(boolean playPressSound) {
        this.playPressSound = playPressSound;
        return this;
    }

    public void setDisplayString(String str) {
        displayString = str;
        setMessage(Component.literal(str));
    }

    @Override
    public void idraw(int x, int y, float f) {
        GuiGraphics g = Gui.graphics();
        if (g == null) return;
        active = enabled;
        if (!getMessage().getString().equals(displayString)) setMessage(Component.literal(displayString));
        render(g, x, y, f);
    }

    @Override
    public int getYMax() {
        return getY() + height;
    }

    @Override
    public boolean ikeyTyped(char key, int code) {
        return false;
    }

    public void onMouseClicked() {
    }

    @Override
    public void imouseClicked(int x, int y, int code) {
        if (code != 0) {
            return;
        }
        pressedInside = enabled && visible && clicked(x, y);
    }

    @Override
    public void imouseMovedOrUp(int x, int y, int witch) {
        if (witch != 0) {
            return;
        }
        boolean shouldActivate = pressedInside && enabled && visible
            && x >= getX() && y >= getY() && x < getX() + width && y < getY() + height;
        pressedInside = false;
        if (shouldActivate) {
            if (playPressSound) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            onMouseClicked();
            if (observer != null) {
                observer.guiObjectEvent(this);
            }
        }
    }

    @Override
    public void imouseMove(int x, int y) {
    }

    @Override
    public void idraw2(int x, int y) {
        if (helper != null && visible && x >= getX() && y >= getY() && x < getX() + width && y < getY() + height)
            helper.drawHoveringText(comment, x, y, Minecraft.getInstance().font);
    }

    @Override
    public void translate(int x, int y) {
        setX(getX() + x);
        setY(getY() + y);
    }

    ArrayList<String> comment = new ArrayList<String>();

    public void setComment(int line, String comment) {
        if (this.comment.size() < line + 1)
            this.comment.add(line, comment);
        else
            this.comment.set(line, comment);
    }

    public void clearComment() {
        comment.clear();
    }
}
