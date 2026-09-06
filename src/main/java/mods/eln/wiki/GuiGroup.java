package mods.eln.wiki;

import mods.eln.gui.Gui;
import mods.eln.gui.GuiHelper;
import mods.eln.gui.IGuiObject;
import mods.eln.gui.IGuiObject.IGuiObjectObserver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import mods.eln.client.gl.GL11;

import java.util.ArrayList;

public class GuiGroup extends Gui implements IGuiObject, IGuiObjectObserver {


    int offX, offY;

    public GuiGroup(int x, int y, int w, int h, GuiHelper helper) {
        this.posX = x;
        this.posY = y;
        this.h = h;
        this.w = w;
        this.offX = x;
        this.offY = y;

        this.helper = helper;
    }


    GuiHelper helper;

    IGuiObjectObserver observer;

    int posX, posY, h, w;
    ItemStack stack;

    ArrayList<IGuiObject> objectList = new ArrayList<IGuiObject>();

    void add(IGuiObject o) {
        objectList.add(o);
    }

    void remove(IGuiObject o) {
        objectList.remove(o);
    }

    int getxOffset() {
        return posX;
    }

    int getYOffset() {

        return posY;
    }

    IGuiObject[] objectListCopy() {
        IGuiObject[] cpy = new IGuiObject[objectList.size()];
        for (int idx = 0; idx < cpy.length; idx++) {
            cpy[idx] = objectList.get(idx);
        }
        return cpy;
    }


    @Override
    public void idraw(int x, int y, float f) {

        x -= getxOffset();
        y -= getYOffset();
        GL11.glPushMatrix();
        // 1.21: the scissor box is in GUI coordinates (GuiGraphics.enableScissor handles the window scale).
        int sx = (this.helper.screen.width - this.helper.xSize) / 2 + posX;
        int sy = (this.helper.screen.height - this.helper.ySize) / 2 + posY;
        mods.eln.gui.Gui.graphics().enableScissor(sx, sy, sx + w, sy + h);

        GL11.glTranslatef(getxOffset(), getYOffset(), 0f);

        for (IGuiObject o : objectList) {
            o.idraw(x, y, f);
        }
        mods.eln.gui.Gui.graphics().disableScissor();
        GL11.glPopMatrix();
    }

    @Override
    public void idraw2(int x, int y) {

        x -= getxOffset();
        y -= getYOffset();
        GL11.glPushMatrix();
        //	GL11.glScissor(displayWidth-(int)((posX+w)*ratio),displayHeight-(int)((posY+h)*ratio),(int)(w*ratio),(int)(h*ratio));
        //	GL11.glEnable(GL11.GL_SCISSOR_TEST);
        //	GL11.glEnable(GL11.GL_SCISSOR_BOX);

        GL11.glTranslatef(getxOffset(), getYOffset(), 0f);
        for (IGuiObject o : objectList) {
            o.idraw2(x, y);
        }
        //	GL11.glDisable(GL11.GL_SCISSOR_TEST);
        //GL11.glDisable(GL11.GL_SCISSOR_BOX);
        GL11.glPopMatrix();
    }

    @Override
    public boolean ikeyTyped(char key, int code) {
        for (IGuiObject o : objectListCopy()) {
            if (o.ikeyTyped(key, code)) return true;
        }
        return false;
    }

    @Override
    public void imouseClicked(int x, int y, int code) {

        x -= getxOffset();
        y -= getYOffset();
        for (IGuiObject o : objectListCopy()) {
            o.imouseClicked(x, y, code);
        }
    }

    @Override
    public void imouseMove(int x, int y) {

        x -= getxOffset();
        y -= getYOffset();
        for (IGuiObject o : objectList) {
            o.imouseMove(x, y);
        }
    }

    @Override
    public void imouseMovedOrUp(int x, int y, int witch) {

        x -= getxOffset();
        y -= getYOffset();
        for (IGuiObject o : objectList) {
            o.imouseMovedOrUp(x, y, witch);
        }
    }

    @Override
    public void translate(int x, int y) {

        posX += x;
        posY += y;
    }

    @Override
    public void guiObjectEvent(IGuiObject object) {

    }

    @Override
    public int getYMax() {

        return posY + h;
    }


}
