package mods.eln.gui;

import mods.eln.misc.UtilsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class GuiHelper {
    public Screen screen;
    public int xSize, ySize;
    ResourceLocation background;
    static final ResourceLocation helperTexture = ResourceLocation.fromNamespaceAndPath("eln", "sprites/gui/helpertexture.png");

    static final ResourceLocation slotSkin = ResourceLocation.parse("textures/gui/container/furnace.png");


    public GuiHelper(Screen screen, int xSize, int ySize, String backgroundName) {
        this.screen = screen;
        this.xSize = xSize;
        this.ySize = ySize;
        background = ResourceLocation.fromNamespaceAndPath("eln", "sprites/gui/" + backgroundName);
    }

    public GuiHelper(Screen screen, int xSize, int ySize) {
        this.screen = screen;
        this.xSize = xSize;
        this.ySize = ySize;
    }

    GuiTextFieldEln newGuiTextField(int x, int y, int width) {
        return newGuiTextField(x, y, width, 150);
    }

    GuiTextFieldEln newGuiTextField(int x, int y, int width, int maxLength) {
        GuiTextFieldEln o;
        o = new GuiTextFieldEln(Minecraft.getInstance().font,
            screen.width / 2 - xSize / 2 + x, screen.height / 2 - ySize / 2 + y, width, 12, this, maxLength);
        objectList.add(o);
        return o;
    }

    GuiButtonEln newGuiButton(int x, int y, int width, String name) {
        GuiButtonEln o;
        o = new GuiButtonEln(screen.width / 2 - xSize / 2 + x, screen.height / 2 - ySize / 2 + y, width, 20, name);
        o.setHelper(this);
        objectList.add(o);
        return o;
    }

    GuiHorizontalTrackBar newGuiHorizontalTrackBar(int x, int y, int width, int height) {
        GuiHorizontalTrackBar o;
        o = new GuiHorizontalTrackBar(screen.width / 2 - xSize / 2 + x, screen.height / 2 - ySize / 2 + y, width, height, this);
        objectList.add(o);
        return o;
    }

    GuiVerticalTrackBar newGuiVerticalTrackBar(int x, int y, int width, int height) {
        GuiVerticalTrackBar o;
        o = new GuiVerticalTrackBar(screen.width / 2 - xSize / 2 + x, screen.height / 2 - ySize / 2 + y, width, height, this);
        objectList.add(o);
        return o;
    }

    GuiVerticalTrackBarHeat newGuiVerticalTrackBarHeat(int x, int y, int width, int height) {
        GuiVerticalTrackBarHeat o;
        o = new GuiVerticalTrackBarHeat(screen.width / 2 - xSize / 2 + x, screen.height / 2 - ySize / 2 + y, width, height, this);
        objectList.add(o);
        return o;
    }

    public GuiVerticalProgressBar newGuiVerticalProgressBar(int x, int y, int width, int height) {
        GuiVerticalProgressBar o;
        o = new GuiVerticalProgressBar(screen.width / 2 - xSize / 2 + x, screen.height / 2 - ySize / 2 + y, width, height, this);
        objectList.add(o);
        return o;
    }

    public GuiVerticalCustomValuesBar newGuiVerticalCustomValuesBar(int x, int y, int width, int height, Float[] values) {
        GuiVerticalCustomValuesBar o;
        o = new GuiVerticalCustomValuesBar(screen.width / 2 - xSize / 2 + x, screen.height / 2 - ySize / 2 + y, width, height, this, values);
        objectList.add(o);
        return o;
    }

	/*public void drawHoveringText(List list, int x, int y, Font fontRenderer, GuiContainerEln cont) {
        drawHoveringText(list, screen.width / 2 - xSize / 2 + x, screen.height / 2 - ySize / 2 + y, Minecraft.getInstance().font);
	}*/

    public void add(IGuiObject o) {
        o.translate(screen.width / 2 - xSize / 2, screen.height / 2 - ySize / 2);
        objectList.add(o);
    }

    public void remove(IGuiObject o) {
        o.translate(-screen.width / 2 + xSize / 2, -screen.height / 2 + ySize / 2);
        objectList.remove(o);
    }

    /*
    void flushRemove() {
        for(IGuiObject o : removeList) {
            o.translate(-screen.width / 2 + xSize / 2, -screen.height / 2 + ySize / 2);
            objectList.remove(o);
        }
        removeList.clear();
    }

    ArrayList<IGuiObject> removeList = new ArrayList<IGuiObject>();*/
    ArrayList<IGuiObject> objectList = new ArrayList<IGuiObject>();

    /** The graphics of the frame being drawn (published by the screen through {@link Gui#begin}). */
    public GuiGraphics graphics() {
        return Gui.graphics();
    }

    void draw(int x, int y, float f) {
        GuiGraphics g = graphics();
        if (g == null) return;
        if (background != null)
            UtilsClient.drawGuiBackground(background, screen, xSize, ySize);
        else {
            int px = 0, py = 0;
            px += (screen.width - xSize) / 2;
            py += (screen.height - ySize) / 2;

            g.fill(px + 2, py + 2, px + xSize - 2, py + ySize - 2, 0xFFC6C6C6);

            g.fill(px + 4, py, px + xSize - 4, py + 1, 0xFF000000);
            g.fill(px + 4, py + 1, px + xSize - 4, py + 3, 0xFFFFFFFF);
            g.fill(px + 4, py + ySize - 1, px + xSize - 4, py + ySize - 0, 0xFF000000);
            g.fill(px + 4, py + ySize - 3, px + xSize - 4, py + ySize - 1, 0xFF555555);

            g.fill(px, py + 4, px + 1, py + ySize - 4, 0xFF000000);
            g.fill(px + 1, py + 4, px + 3, py + ySize - 4, 0xFFFFFFFF);
            g.fill(px + xSize - 1, py + 4, px + xSize - 0, py + ySize - 4, 0xFF000000);
            g.fill(px + xSize - 3, py + 4, px + xSize - 1, py + ySize - 4, 0xFF555555);

            g.blit(helperTexture, px, py, 0, 0, 4, 4);
            g.blit(helperTexture, px + xSize - 4, py, 4, 0, 4, 4);
            g.blit(helperTexture, px, py + ySize - 4, 0, 4, 4, 4);
            g.blit(helperTexture, px + xSize - 4, py + ySize - 4, 4, 4, 4, 4);
        }

        for (IGuiObject o : objectList) {
            o.idraw(x, y, f);
        }
    }

    /** Draws from the texture last bound through UtilsClient.bindTexture (256x256, as before). */
    public void drawTexturedModalRect(int x, int y, int u, int v, int width, int height) {
        x += (screen.width - xSize) / 2;
        y += (screen.height - ySize) / 2;
        ResourceLocation texture = mods.eln.client.gl.FixedFunction.texture();
        GuiGraphics g = graphics();
        if (g == null || texture == null) return;
        g.blit(texture, x, y, u, v, width, height);
    }

    public void drawRect(int x0, int y0, int x1, int y1, int color) {
        int dx = (screen.width - xSize) / 2;
        int dy = (screen.height - ySize) / 2;
        Gui.drawRect(x0 + dx, y0 + dy, x1 + dx, y1 + dy, color);
    }

    IGuiObject[] objectListCopy() {
        IGuiObject[] cpy = new IGuiObject[objectList.size()];
        for (int idx = 0; idx < cpy.length; idx++) {
            cpy[idx] = objectList.get(idx);
        }
        return cpy;
    }

    /** Whether a text field has the keyboard, so the screen does not treat typed letters as hotkeys. */
    public boolean hasFocusedTextField() {
        for (IGuiObject o : objectList) {
            if (o instanceof GuiTextFieldEln t && t.isFocused()) return true;
        }
        return false;
    }

    protected void keyTyped(char key, int code) {
        for (IGuiObject o : objectListCopy()) {
            o.ikeyTyped(key, code);
        }
    }

    protected void mouseClicked(int x, int y, int code) {
        for (IGuiObject o : objectListCopy()) {
            o.imouseClicked(x, y, code);
        }
    }

    protected void mouseMove(int x, int y) {
        for (IGuiObject o : objectList) {
            o.imouseMove(x, y);
        }
    }

    protected void mouseMovedOrUp(int x, int y, int witch) {
        for (IGuiObject o : objectList) {
            o.imouseMovedOrUp(x, y, witch);
        }
    }

    public void drawString(int x, int y, int color, String str) {
        GuiGraphics g = graphics();
        if (g == null) return;
        g.drawString(Minecraft.getInstance().font, str, screen.width / 2 - xSize / 2 + x, screen.height / 2 - ySize / 2 + y, color, false);
    }

    public void draw2(int x, int y) {
        for (IGuiObject o : objectList) {
            o.idraw2(x, y);
        }
    }

    /** A vanilla tooltip at (x, y); the coordinates are relative to the GUI for container screens, as before. */
    public void drawHoveringText(List par1List, int x, int y, Font font) {
        GuiGraphics g = graphics();
        if (g == null || par1List.isEmpty()) return;
        List<Component> lines = new ArrayList<>();
        for (Object o : par1List) lines.add(Component.literal(String.valueOf(o)));
        if (screen instanceof AbstractContainerScreen) {
            x += (screen.width - xSize) / 2;
            y += (screen.height - ySize) / 2;
        }
        g.renderComponentTooltip(font, lines, x, y);
    }

    public void drawGradientRect(int par1, int par2, int par3, int par4, int par5, int par6) {
        GuiGraphics g = graphics();
        if (g == null) return;
        g.fillGradient(Math.min(par1, par3), Math.min(par2, par4), Math.max(par1, par3), Math.max(par2, par4), par5, par6);
    }

    public int getHoveringTextWidth(List<String> comment, Font fontRenderer) {
        int strWidth = 0;
        for (String str : comment) {
            int size = fontRenderer.width(str);
            if (size > strWidth) strWidth = size;
        }
        return strWidth + 5;
    }

    public int getHoveringTextHeight(List<String> comment, Font fontRenderer) {
        return comment.size() * 9 - 4;
    }

    public void drawProcess(int x, int y, float value) {
        UtilsClient.bindTexture(helperTexture);
        drawTexturedModalRect(x, y, 8, 0, (int) (22), 16);
        drawTexturedModalRect(x, y, 8 + 22, 0, (int) (22 * value), 16);
    }

    protected void drawSlot(int x, int y) {
        UtilsClient.bindTexture(slotSkin);

        drawTexturedModalRect(x - 1, y - 1, 55, 16, 73 - 55, 34 - 16);
    }
}
