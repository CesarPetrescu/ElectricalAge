package mods.eln.client.gl;

import net.minecraft.client.renderer.LightTexture;

/** The lightmap calls of 1.7.10's {@code OpenGlHelper}: they set the packed light the emulator emits with. */
public final class OpenGlHelper {
    private OpenGlHelper() {
    }

    public static final int defaultTexUnit = 0x84C0;
    public static final int lightmapTexUnit = 0x84C1;
    public static int lastBrightnessX = 240, lastBrightnessY = 240;

    public static void setActiveTexture(int unit) {
    }

    /** Lightmap coordinates are 0..240 in 1.7.10 terms (block, sky); pack them the way the light texture does. */
    public static void setLightmapTextureCoords(int unit, float x, float y) {
        lastBrightnessX = (int) x;
        lastBrightnessY = (int) y;
        FixedFunction.setLight(LightTexture.pack(Math.round(x / 16f), Math.round(y / 16f)));
    }
}
