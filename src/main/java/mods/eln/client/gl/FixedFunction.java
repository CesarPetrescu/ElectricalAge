package mods.eln.client.gl;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A fixed-function OpenGL emulator on top of Minecraft 1.21's renderer.
 *
 * Electrical Age draws its ~160 OBJ models and every halo, cable and GUI gauge through the 1.7.10
 * fixed-function API: matrix stack, glColor, glBegin/glVertex/glEnd, display lists, GL_LIGHTING and
 * GL_BLEND toggles. None of that exists on the core profile the game has used since 1.17. Rather
 * than rewrite ~150 render bodies, {@link GL11} keeps the old entry points and this class gives them
 * meaning: the matrix calls drive a {@link PoseStack}, glBegin/glEnd collect vertices (with the
 * colour, normal and texture coordinate current at each glVertex), and glEnd emits them either into
 * the {@link MultiBufferSource} of the block-entity or item renderer that is running (proper world
 * lighting through the RenderType) or, with no buffer source, immediately through
 * {@link RenderSystem} (GUI screens). Display lists record the vertex batches and replay them under
 * the state current at glCallList, which is exactly what the OBJ loader relies on.
 *
 * Nothing here is thread-safe; the render thread is the only caller.
 */
public final class FixedFunction {
    private FixedFunction() {
    }

    public static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final int FULL_BRIGHT = LightTexture.pack(15, 15);

    /** The GL enable/disable flags and blend settings; copied on glPushAttrib. */
    static final class State implements Cloneable {
        boolean texture2d = true, lighting = true, cullFace = true, blend = false, depthTest = true, alphaTest = false, scissor = false, lineSmooth = false;
        /** GL_TEXTURE_2D on 1.7.10's lightmap unit: off means the block light does not apply (glowing parts). */
        boolean lightmap = true;
        /** Which unit glEnable/glDisable(GL_TEXTURE_2D) addresses: 0 the texture, 1 the lightmap (OpenGlHelper.setActiveTexture). */
        int activeTexture = 0;
        boolean depthMask = true;
        int blendSrc = GL11.GL_SRC_ALPHA, blendDst = GL11.GL_ONE_MINUS_SRC_ALPHA;
        float lineWidth = 1f;
        float r = 1f, g = 1f, b = 1f, a = 1f;
        ResourceLocation texture = null;
        int[] scissorBox = {0, 0, 0, 0};

        @Override
        protected State clone() {
            try {
                State s = (State) super.clone();
                s.scissorBox = scissorBox.clone();
                return s;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }

    static State state = new State();
    private static final Deque<State> attribStack = new ArrayDeque<>();

    // render context
    private static PoseStack pose = new PoseStack();
    private static MultiBufferSource buffers = null;
    private static int packedLight = FULL_BRIGHT;
    private static int packedOverlay = OverlayTexture.NO_OVERLAY;
    private static int depth = 0;

    /** Enters a world/item render: everything drawn until {@link #end()} goes into {@code source}. */
    public static void begin(PoseStack poseStack, MultiBufferSource source, int light, int overlay) {
        if (depth++ == 0) {
            pose = poseStack;
            buffers = source;
            packedLight = light;
            packedOverlay = overlay;
            state = new State();
            attribStack.clear();
            resetVertexState();
        }
    }

    /**
     * The current normal (and texture coordinate) at GL's defaults, so a render that sets none
     * does not inherit whatever the previous model's last face left behind.
     */
    private static void resetVertexState() {
        nx = 0f;
        ny = 0f;
        nz = 1f;
        u = 0f;
        v = 0f;
    }

    /** Enters a GUI render: drawing goes straight through RenderSystem, in the GUI's pose. */
    public static void beginGui(GuiGraphics graphics) {
        if (depth++ == 0) {
            pose = graphics.pose();
            buffers = null;
            packedLight = FULL_BRIGHT;
            packedOverlay = OverlayTexture.NO_OVERLAY;
            state = new State();
            attribStack.clear();
            resetVertexState();
        }
    }

    /** OpenGlHelper.setActiveTexture: 1.7.10 toggled the lightmap by disabling GL_TEXTURE_2D on its unit. */
    static void activeTexture(int unit) {
        state.activeTexture = unit;
    }

    /** Leaves the render entered with {@link #begin} / {@link #beginGui}, flushing the buffers. */
    public static void finish() {
        if (--depth == 0) {
            if (buffers instanceof MultiBufferSource.BufferSource bs) bs.endBatch();
            buffers = null;
            if (state.scissor) RenderSystem.disableScissor();
        }
        if (depth < 0) depth = 0;
    }

    public static PoseStack pose() {
        return pose;
    }

    public static boolean inWorld() {
        return buffers != null;
    }

    public static void setLight(int light) {
        packedLight = light;
    }

    public static int light() {
        return packedLight;
    }

    // ------------------------------------------------------------------ matrices
    static void pushMatrix() {
        pose.pushPose();
    }

    static void popMatrix() {
        pose.popPose();
    }

    static void translate(float x, float y, float z) {
        pose.translate(x, y, z);
    }

    static void rotate(float angleDeg, float x, float y, float z) {
        if (angleDeg == 0f) return;
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len == 0f) return;
        pose.mulPose(new Quaternionf().rotateAxis((float) Math.toRadians(angleDeg), x / len, y / len, z / len));
    }

    static void scale(float x, float y, float z) {
        pose.scale(x, y, z);
    }

    // ------------------------------------------------------------------ state
    static void enable(int cap, boolean on) {
        switch (cap) {
            case GL11.GL_TEXTURE_2D -> {
                if (state.activeTexture == 1) state.lightmap = on; else state.texture2d = on;
            }
            case GL11.GL_LIGHTING -> state.lighting = on;
            case GL11.GL_CULL_FACE -> state.cullFace = on;
            case GL11.GL_BLEND -> state.blend = on;
            case GL11.GL_DEPTH_TEST -> state.depthTest = on;
            case GL11.GL_ALPHA_TEST -> state.alphaTest = on;
            case GL11.GL_LINE_SMOOTH -> state.lineSmooth = on;
            case GL11.GL_SCISSOR_TEST -> {
                state.scissor = on;
                if (on) applyScissor(); else RenderSystem.disableScissor();
            }
            default -> {
            }
        }
    }

    static boolean isEnabled(int cap) {
        return switch (cap) {
            case GL11.GL_TEXTURE_2D -> state.activeTexture == 1 ? state.lightmap : state.texture2d;
            case GL11.GL_LIGHTING -> state.lighting;
            case GL11.GL_CULL_FACE -> state.cullFace;
            case GL11.GL_BLEND -> state.blend;
            case GL11.GL_DEPTH_TEST -> state.depthTest;
            case GL11.GL_ALPHA_TEST -> state.alphaTest;
            case GL11.GL_SCISSOR_TEST -> state.scissor;
            case GL11.GL_LINE_SMOOTH -> state.lineSmooth;
            default -> false;
        };
    }

    static void scissor(int x, int y, int w, int h) {
        state.scissorBox = new int[]{x, y, w, h};
        if (state.scissor) applyScissor();
    }

    private static void applyScissor() {
        int[] b = state.scissorBox;
        RenderSystem.enableScissor(b[0], b[1], b[2], b[3]);
    }

    static int[] scissorBox() {
        return state.scissorBox;
    }

    static void pushAttrib() {
        attribStack.push(state.clone());
    }

    static void popAttrib() {
        if (!attribStack.isEmpty()) {
            boolean wasScissor = state.scissor;
            state = attribStack.pop();
            if (wasScissor && !state.scissor) RenderSystem.disableScissor();
            else if (state.scissor) applyScissor();
        }
    }

    public static void bindTexture(ResourceLocation texture) {
        state.texture = texture;
    }

    /**
     * Draws a line of text at the current matrix (1.7.10's FontRenderer.drawString inside a
     * render pass): in the world through the buffer source, in a GUI through the GuiGraphics pose.
     */
    /** 1.7.10's `FontRenderer.drawString(text, x, y, color)` (no shadow). */
    public static void drawString(net.minecraft.client.gui.Font font, String text, float x, float y, int color) {
        drawString(font, text, x, y, color, false);
    }

    public static void drawStringShadow(net.minecraft.client.gui.Font font, String text, float x, float y, int color) {
        drawString(font, text, x, y, color, true);
    }

    public static void drawString(net.minecraft.client.gui.Font font, String text, float x, float y, int color, boolean shadow) {
        if (pose == null) return;
        MultiBufferSource source = buffers;
        boolean own = false;
        if (source == null) {
            source = net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
            own = true;
        }
        font.drawInBatch(text, x, y, color, shadow, pose.last().pose(), source, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, packedLight);
        if (own) ((MultiBufferSource.BufferSource) source).endBatch();
    }

    /**
     * Draws an item's baked model at the current matrix (what the 1.7.10 code did by rendering an
     * EntityItem in place). World renders only: in a GUI, GuiGraphics.renderItem is the way.
     */
    public static void drawItem(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.ItemDisplayContext context) {
        if (buffers == null || stack.isEmpty()) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.getItemRenderer().renderStatic(stack, context, packedLight, packedOverlay, pose, buffers, mc.level, 0);
    }

    /** The texture bound through {@link #bindTexture}; the GUI helpers blit from it. */
    public static ResourceLocation texture() {
        return state.texture;
    }

    public static void color(float r, float g, float b, float a) {
        state.r = r;
        state.g = g;
        state.b = b;
        state.a = a;
    }

    // ------------------------------------------------------------------ immediate mode
    /** One recorded glBegin..glEnd block: positions, uvs, normals and colours per vertex. */
    static final class Batch {
        final int mode;
        final List<float[]> vertices = new ArrayList<>(); // x y z u v nx ny nz r g b a
        // the matrix current at glBegin, relative to the list start, so replays can honour glTranslate inside a list
        Matrix4f transform;

        Batch(int mode) {
            this.mode = mode;
        }
    }

    private static Batch current = null;
    private static float u = 0f, v = 0f, nx = 0f, ny = 0f, nz = 1f;   // GL defaults: normal toward the viewer

    // display lists
    private static final Map<Integer, List<Batch>> lists = new HashMap<>();
    private static int nextList = 1;
    private static List<Batch> recording = null;
    private static Matrix4f recordingBase = null;

    static void begin(int mode) {
        current = new Batch(mode);
        if (recording != null) {
            // matrix relative to the list start
            Matrix4f rel = new Matrix4f(recordingBase).invert().mul(pose.last().pose());
            current.transform = rel;
        }
    }

    static void texCoord(float s, float t) {
        u = s;
        v = t;
    }

    static void normal(float x, float y, float z) {
        nx = x;
        ny = y;
        nz = z;
    }

    static void vertex(float x, float y, float z) {
        if (current == null) return; // glVertex outside glBegin: GL ignores it too
        current.vertices.add(new float[]{x, y, z, u, v, nx, ny, nz, state.r, state.g, state.b, state.a});
    }

    /** glEnd: closes the primitive opened by {@link #begin(int)}. */
    static void endPrimitive() {
        if (current == null) return;
        Batch batch = current;
        current = null;
        if (recording != null) {
            recording.add(batch);
        } else {
            emit(batch, null);
        }
    }

    static int genLists(int n) {
        int first = nextList;
        nextList += Math.max(1, n);
        return first;
    }

    static void deleteLists(int first, int n) {
        for (int i = 0; i < n; i++) lists.remove(first + i);
    }

    static void newList(int id) {
        recording = new ArrayList<>();
        recordingBase = new Matrix4f(pose.last().pose());
        lists.put(id, recording);
    }

    static void endList() {
        recording = null;
        recordingBase = null;
    }

    static void callList(int id) {
        List<Batch> batches = lists.get(id);
        if (batches == null) return;
        if (recording != null) {
            // a list calling a list: inline it
            recording.addAll(batches);
            return;
        }
        for (Batch b : batches) emit(b, b.transform);
    }

    // ------------------------------------------------------------------ emission
    private static void emit(Batch batch, Matrix4f extra) {
        if (batch.vertices.isEmpty()) return;
        boolean lines = batch.mode == GL11.GL_LINES || batch.mode == GL11.GL_LINE_STRIP || batch.mode == GL11.GL_LINE_LOOP;
        List<float[]> prims = lines ? toLines(batch) : toQuads(batch);
        if (prims.isEmpty()) return;

        PoseStack.Pose p = pose.last();
        Matrix4f model = extra == null ? p.pose() : new Matrix4f(p.pose()).mul(extra);
        boolean textured = state.texture2d && state.texture != null;
        ResourceLocation tex = textured ? state.texture : WHITE;
        // the lightmap unit off (UtilsClient.disableLight) is full bright: glowing parts, GUI overlays
        int light = state.lightmap ? packedLight : FULL_BRIGHT;

        if (buffers != null) {
            // GL_LIGHTING on: the entity types, shaded by the normal. Off: no directional shading at all.
            RenderType type = lines ? RenderType.lines()
                : !state.lighting ? ElnRenderTypes.unlit(tex, state.blend, state.cullFace)
                : state.blend ? RenderType.entityTranslucent(tex)
                : state.cullFace ? RenderType.entityCutout(tex) : RenderType.entityCutoutNoCull(tex);
            VertexConsumer vc = buffers.getBuffer(type);
            if (lines) {
                for (int i = 0; i + 1 < prims.size(); i += 2) {
                    float[] a = prims.get(i), b = prims.get(i + 1);
                    Vector3f dir = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
                    if (dir.lengthSquared() > 0) dir.normalize();
                    lineVertex(vc, model, p, a, dir);
                    lineVertex(vc, model, p, b, dir);
                }
            } else {
                for (float[] vtx : prims) {
                    Vector4f pos = new Vector4f(vtx[0], vtx[1], vtx[2], 1f).mul(model);
                    vc.addVertex(pos.x, pos.y, pos.z)
                        .setColor(vtx[8], vtx[9], vtx[10], vtx[11])
                        .setUv(vtx[3], vtx[4])
                        .setOverlay(packedOverlay)
                        .setLight(light)
                        .setNormal(p, vtx[5], vtx[6], vtx[7]);
                }
            }
        } else {
            // GUI / no buffer source: immediate draw through RenderSystem
            if (state.blend) {
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(state.blendSrc, state.blendDst);
            } else {
                RenderSystem.disableBlend();
            }
            if (state.depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.depthMask(state.depthMask);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            BufferBuilder bb;
            if (lines) {
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                RenderSystem.lineWidth(state.lineWidth);
                bb = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
                for (float[] vtx : prims) {
                    Vector4f pos = new Vector4f(vtx[0], vtx[1], vtx[2], 1f).mul(model);
                    bb.addVertex(pos.x, pos.y, pos.z).setColor(vtx[8], vtx[9], vtx[10], vtx[11]);
                }
            } else {
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                RenderSystem.setShaderTexture(0, tex);
                bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                for (float[] vtx : prims) {
                    Vector4f pos = new Vector4f(vtx[0], vtx[1], vtx[2], 1f).mul(model);
                    bb.addVertex(pos.x, pos.y, pos.z).setUv(vtx[3], vtx[4]).setColor(vtx[8], vtx[9], vtx[10], vtx[11]);
                }
            }
            BufferUploader.drawWithShader(bb.buildOrThrow());
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    private static void lineVertex(VertexConsumer vc, Matrix4f model, PoseStack.Pose p, float[] vtx, Vector3f dir) {
        Vector4f pos = new Vector4f(vtx[0], vtx[1], vtx[2], 1f).mul(model);
        vc.addVertex(pos.x, pos.y, pos.z).setColor(vtx[8], vtx[9], vtx[10], vtx[11]).setNormal(p, dir.x, dir.y, dir.z);
    }

    /** Every primitive type Electrical Age uses, as a flat quad list (triangles get a repeated vertex). */
    private static List<float[]> toQuads(Batch batch) {
        List<float[]> in = batch.vertices;
        List<float[]> out = new ArrayList<>(in.size() + 4);
        switch (batch.mode) {
            case GL11.GL_QUADS -> {
                int n = in.size() - in.size() % 4;
                out.addAll(in.subList(0, n));
            }
            case GL11.GL_TRIANGLES -> {
                for (int i = 0; i + 2 < in.size(); i += 3) {
                    out.add(in.get(i));
                    out.add(in.get(i + 1));
                    out.add(in.get(i + 2));
                    out.add(in.get(i + 2));
                }
            }
            case GL11.GL_QUAD_STRIP -> {
                for (int i = 0; i + 3 < in.size(); i += 2) {
                    out.add(in.get(i));
                    out.add(in.get(i + 1));
                    out.add(in.get(i + 3));
                    out.add(in.get(i + 2));
                }
            }
            case GL11.GL_TRIANGLE_STRIP -> {
                for (int i = 0; i + 2 < in.size(); i++) {
                    boolean even = (i & 1) == 0;
                    out.add(in.get(i));
                    out.add(in.get(even ? i + 1 : i + 2));
                    out.add(in.get(even ? i + 2 : i + 1));
                    out.add(in.get(even ? i + 2 : i + 1));
                }
            }
            case GL11.GL_TRIANGLE_FAN, GL11.GL_POLYGON -> {
                for (int i = 1; i + 1 < in.size(); i++) {
                    out.add(in.get(0));
                    out.add(in.get(i));
                    out.add(in.get(i + 1));
                    out.add(in.get(i + 1));
                }
            }
            default -> {
            }
        }
        return out;
    }

    private static List<float[]> toLines(Batch batch) {
        List<float[]> in = batch.vertices;
        List<float[]> out = new ArrayList<>(in.size() * 2);
        switch (batch.mode) {
            case GL11.GL_LINES -> out.addAll(in.subList(0, in.size() - in.size() % 2));
            case GL11.GL_LINE_STRIP -> {
                for (int i = 0; i + 1 < in.size(); i++) {
                    out.add(in.get(i));
                    out.add(in.get(i + 1));
                }
            }
            case GL11.GL_LINE_LOOP -> {
                for (int i = 0; i < in.size(); i++) {
                    out.add(in.get(i));
                    out.add(in.get((i + 1) % in.size()));
                }
            }
            default -> {
            }
        }
        return out;
    }
}
