package mods.eln.client.gl;

/**
 * 1.7.10's {@code RenderHelper} lighting toggles. World lighting comes from the packed light the
 * renderer hands {@link FixedFunction}; these only flip the GL_LIGHTING flag so full-bright parts
 * (lamps, halos) still work the way the render bodies expect.
 */
public final class RenderHelper {
    private RenderHelper() {
    }

    public static void enableStandardItemLighting() {
        FixedFunction.enable(GL11.GL_LIGHTING, true);
    }

    public static void enableGUIStandardItemLighting() {
        FixedFunction.enable(GL11.GL_LIGHTING, true);
    }

    public static void disableStandardItemLighting() {
        FixedFunction.enable(GL11.GL_LIGHTING, false);
    }
}
