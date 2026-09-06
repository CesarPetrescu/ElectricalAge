package mods.eln.client.gl;

import java.nio.IntBuffer;

/**
 * The subset of {@code org.lwjgl.opengl.GL11} Electrical Age's render code calls, with the same
 * names and constants, implemented on {@link FixedFunction}. A render body ported from 1.7.10
 * only changes its import. Functions that have no meaning on the core profile (shade model, hints,
 * texture filters, alpha test) are accepted and ignored.
 */
@SuppressWarnings("unused")
public final class GL11 {
    private GL11() {
    }

    public static final int GL_POINTS = 0x0, GL_LINES = 0x1, GL_LINE_LOOP = 0x2, GL_LINE_STRIP = 0x3, GL_TRIANGLES = 0x4,
        GL_TRIANGLE_STRIP = 0x5, GL_TRIANGLE_FAN = 0x6, GL_QUADS = 0x7, GL_QUAD_STRIP = 0x8, GL_POLYGON = 0x9;
    public static final int GL_ZERO = 0, GL_ONE = 1, GL_SRC_COLOR = 0x300, GL_ONE_MINUS_SRC_COLOR = 0x301, GL_SRC_ALPHA = 0x302,
        GL_ONE_MINUS_SRC_ALPHA = 0x303, GL_DST_ALPHA = 0x304, GL_ONE_MINUS_DST_ALPHA = 0x305, GL_DST_COLOR = 0x306, GL_ONE_MINUS_DST_COLOR = 0x307;
    public static final int GL_NEVER = 0x200, GL_LESS = 0x201, GL_EQUAL = 0x202, GL_LEQUAL = 0x203, GL_GREATER = 0x204, GL_NOTEQUAL = 0x205, GL_GEQUAL = 0x206, GL_ALWAYS = 0x207;
    public static final int GL_FRONT = 0x404, GL_BACK = 0x405, GL_FRONT_AND_BACK = 0x408;
    public static final int GL_CURRENT_BIT = 0x1, GL_ENABLE_BIT = 0x2000, GL_ALL_ATTRIB_BITS = 0xFFFFF;
    public static final int GL_LINE_SMOOTH = 0xB20, GL_CULL_FACE = 0xB44, GL_LIGHTING = 0xB50, GL_DEPTH_TEST = 0xB71, GL_ALPHA_TEST = 0xBC0,
        GL_BLEND = 0xBE2, GL_SCISSOR_TEST = 0xC11, GL_TEXTURE_2D = 0xDE1, GL_NORMALIZE = 0xBA1, GL_LIGHT0 = 0x4000, GL_LIGHT1 = 0x4001;
    public static final int GL_BLEND_DST = 0xBE0, GL_BLEND_SRC = 0xBE1, GL_SCISSOR_BOX = 0xC10, GL_VIEWPORT = 0xBA2;
    public static final int GL_LINE_SMOOTH_HINT = 0xC52, GL_FASTEST = 0x1101, GL_NICEST = 0x1102, GL_DONT_CARE = 0x1100;
    public static final int GL_FLAT = 0x1D00, GL_SMOOTH = 0x1D01;
    public static final int GL_COMPILE = 0x1300, GL_COMPILE_AND_EXECUTE = 0x1301;
    public static final int GL_TEXTURE_MAG_FILTER = 0x2800, GL_TEXTURE_MIN_FILTER = 0x2801, GL_NEAREST = 0x2600, GL_LINEAR = 0x2601;
    public static final int GL_MODELVIEW = 0x1700, GL_PROJECTION = 0x1701;
    public static final int GL_T4 = 0x2A2C; // GL_T4F_V4F; kept for the one legacy caller

    // matrices
    public static void glPushMatrix() { FixedFunction.pushMatrix(); }
    public static void glPopMatrix() { FixedFunction.popMatrix(); }
    public static void glTranslatef(float x, float y, float z) { FixedFunction.translate(x, y, z); }
    public static void glTranslated(double x, double y, double z) { FixedFunction.translate((float) x, (float) y, (float) z); }
    public static void glRotatef(float angle, float x, float y, float z) { FixedFunction.rotate(angle, x, y, z); }
    public static void glRotated(double angle, double x, double y, double z) { FixedFunction.rotate((float) angle, (float) x, (float) y, (float) z); }
    public static void glScalef(float x, float y, float z) { FixedFunction.scale(x, y, z); }
    public static void glScaled(double x, double y, double z) { FixedFunction.scale((float) x, (float) y, (float) z); }
    public static void glLoadIdentity() { }
    public static void glMatrixMode(int mode) { }

    // colour
    public static void glColor3f(float r, float g, float b) { FixedFunction.color(r, g, b, 1f); }
    public static void glColor4f(float r, float g, float b, float a) { FixedFunction.color(r, g, b, a); }
    public static void glColor3d(double r, double g, double b) { FixedFunction.color((float) r, (float) g, (float) b, 1f); }
    public static void glColor4d(double r, double g, double b, double a) { FixedFunction.color((float) r, (float) g, (float) b, (float) a); }

    // state
    public static void glEnable(int cap) { FixedFunction.enable(cap, true); }
    public static void glDisable(int cap) { FixedFunction.enable(cap, false); }
    public static boolean glIsEnabled(int cap) { return FixedFunction.isEnabled(cap); }
    public static void glBlendFunc(int src, int dst) { FixedFunction.state.blendSrc = src; FixedFunction.state.blendDst = dst; }
    public static void glDepthMask(boolean flag) { FixedFunction.state.depthMask = flag; }
    public static void glDepthFunc(int func) { }
    public static void glLineWidth(float width) { FixedFunction.state.lineWidth = width; }
    public static void glPointSize(float size) { }
    public static void glCullFace(int mode) { }
    public static void glShadeModel(int mode) { }
    public static void glHint(int target, int mode) { }
    public static void glAlphaFunc(int func, float ref) { }
    public static void glTexParameteri(int target, int pname, int param) { }
    public static int glGetTexParameteri(int target, int pname) { return GL_LINEAR; }
    public static void glScissor(int x, int y, int w, int h) { FixedFunction.scissor(x, y, w, h); }
    public static void glPushAttrib(int mask) { FixedFunction.pushAttrib(); }
    public static void glPopAttrib() { FixedFunction.popAttrib(); }

    public static int glGetInteger(int pname) {
        return switch (pname) {
            case GL_BLEND_SRC -> FixedFunction.state.blendSrc;
            case GL_BLEND_DST -> FixedFunction.state.blendDst;
            default -> 0;
        };
    }

    public static void glGetInteger(int pname, IntBuffer params) {
        if (pname == GL_SCISSOR_BOX) {
            int[] b = FixedFunction.scissorBox();
            for (int i = 0; i < 4 && params.remaining() > i; i++) params.put(params.position() + i, b[i]);
        }
    }

    public static boolean glGetBoolean(int pname) { return FixedFunction.isEnabled(pname); }

    // immediate mode
    public static void glBegin(int mode) { FixedFunction.begin(mode); }
    public static void glEnd() { FixedFunction.end(); }
    public static void glVertex2f(float x, float y) { FixedFunction.vertex(x, y, 0f); }
    public static void glVertex2d(double x, double y) { FixedFunction.vertex((float) x, (float) y, 0f); }
    public static void glVertex3f(float x, float y, float z) { FixedFunction.vertex(x, y, z); }
    public static void glVertex3d(double x, double y, double z) { FixedFunction.vertex((float) x, (float) y, (float) z); }
    public static void glTexCoord2f(float u, float v) { FixedFunction.texCoord(u, v); }
    public static void glTexCoord2d(double u, double v) { FixedFunction.texCoord((float) u, (float) v); }
    public static void glNormal3f(float x, float y, float z) { FixedFunction.normal(x, y, z); }
    public static void glNormal3d(double x, double y, double z) { FixedFunction.normal((float) x, (float) y, (float) z); }

    // display lists
    public static int glGenLists(int n) { return FixedFunction.genLists(n); }
    public static void glDeleteLists(int first, int n) { FixedFunction.deleteLists(first, n); }
    public static void glNewList(int list, int mode) { FixedFunction.newList(list); }
    public static void glEndList() { FixedFunction.endList(); }
    public static void glCallList(int list) { FixedFunction.callList(list); }
}
