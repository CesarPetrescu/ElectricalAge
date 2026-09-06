package mods.eln.gui;

import mods.eln.client.gl.FixedFunction;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.7.10's {@code net.minecraft.client.gui.Gui}: the flat drawing helpers the widgets are built
 * on. 1.20+ draws through a {@link GuiGraphics} handed to the screen's render method, so the
 * screen publishes it here ({@link #begin}) for the duration of a frame and the helpers read it.
 */
public class Gui {
    private static GuiGraphics current;

    public static void begin(GuiGraphics graphics) {
        current = graphics;
    }

    public static void end() {
        current = null;
    }

    /** The graphics of the frame being drawn; null outside a screen render. */
    public static GuiGraphics graphics() {
        return current;
    }

    public static void drawRect(int x0, int y0, int x1, int y1, int color) {
        if (current == null) return;
        current.fill(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1), color);
    }

    public void drawString(Font font, String text, int x, int y, int color) {
        if (current == null) return;
        current.drawString(font, text, x, y, color, false);
    }

    public void drawCenteredString(Font font, String text, int x, int y, int color) {
        if (current == null) return;
        current.drawCenteredString(font, text, x, y, color);
    }

    /** Draws from the texture last bound through UtilsClient.bindTexture, 256x256 like the original. */
    public void drawTexturedModalRect(int x, int y, int u, int v, int width, int height) {
        ResourceLocation texture = FixedFunction.texture();
        if (current == null || texture == null) return;
        current.blit(texture, x, y, u, v, width, height);
    }

    public static void drawTexturedModalRect(ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
        if (current == null) return;
        current.blit(texture, x, y, u, v, width, height);
    }
}
