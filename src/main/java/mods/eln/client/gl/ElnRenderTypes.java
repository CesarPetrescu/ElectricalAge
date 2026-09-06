package mods.eln.client.gl;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * The render types the fixed-function emulator needs and vanilla does not have: textured geometry
 * with <b>no directional lighting</b>, which is what 1.7.10 drew whenever {@code GL_LIGHTING} was
 * off (inventory icons, glowing lamp parts, cable sprites, GUI overlays). Every vanilla entity
 * render type shades by the vertex normal; these use the text shader (colour x texture x
 * lightmap, no normal at all), so a quad drawn without a normal comes out at its own colour.
 *
 * A subclass only to reach the protected state shards.
 */
public final class ElnRenderTypes extends RenderType {
    private ElnRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    private record Key(ResourceLocation texture, boolean blend, boolean cull) {
    }

    private static final Map<Key, RenderType> UNLIT = new HashMap<>();

    /**
     * Unlit textured quads, lit only by the lightmap: translucent-blended or cutout, back faces
     * culled or not. The blended variant is depth-sorted on upload like every translucent type.
     */
    public static RenderType unlit(ResourceLocation texture, boolean blend, boolean cull) {
        return UNLIT.computeIfAbsent(new Key(texture, blend, cull), key -> create(
            "eln_unlit" + (blend ? "_translucent" : "_cutout") + (cull ? "" : "_no_cull"),
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            blend,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_TEXT_SHADER)
                .setTextureState(new TextureStateShard(texture, false, false))
                .setTransparencyState(blend ? TRANSLUCENT_TRANSPARENCY : NO_TRANSPARENCY)
                .setCullState(cull ? CULL : NO_CULL)
                .setLightmapState(LIGHTMAP)
                .createCompositeState(false)
        ));
    }
}
