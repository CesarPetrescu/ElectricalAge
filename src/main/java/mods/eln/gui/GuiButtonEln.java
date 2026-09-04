package mods.eln.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.init.SoundEvents;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;

public class GuiButtonEln extends GuiButton implements IGuiObject {

    IGuiObjectObserver observer;
    private boolean playPressSound = true;
    private boolean pressedInside = false;

    public GuiButtonEln(int x, int y, int width, int height, String str) {
        super(0, x, y, width, height, str);
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

    @Override
    public void idraw(int x, int y, float f) {
        GL11.glColor4f(1f, 1f, 1f, 1f);
        drawButton(Minecraft.getMinecraft(), x, y, f);
    }

    @Override
    public int getYMax() {
        return this.y + height;
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
        pressedInside = mousePressed(Minecraft.getMinecraft(), x, y);
    }

    @Override
    public void imouseMovedOrUp(int x, int y, int witch) {
        if (witch != 0) {
            return;
        }
        boolean shouldActivate = pressedInside && enabled && visible
            && x >= this.x && y >= this.y && x < this.x + width && y < this.y + height;
        pressedInside = false;
        if (shouldActivate) {
            if (playPressSound) {
                Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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
        if (helper != null && visible && x >= this.x && y >= this.y && x < this.x + width && y < this.y + height)
            helper.drawHoveringText(comment, x, y, Minecraft.getMinecraft().fontRenderer);
    }

    @Override
    public void translate(int x, int y) {
        this.x += x;
        this.y += y;
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
