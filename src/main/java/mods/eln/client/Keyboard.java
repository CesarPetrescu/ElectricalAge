package mods.eln.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** The LWJGL 2 {@code mods.eln.client.Keyboard} calls the mod makes, on GLFW key codes. */
public final class Keyboard {
    private Keyboard() {
    }

    public static final int KEY_ESCAPE = GLFW.GLFW_KEY_ESCAPE;
    public static final int KEY_RETURN = GLFW.GLFW_KEY_ENTER;
    public static final int KEY_LSHIFT = GLFW.GLFW_KEY_LEFT_SHIFT;
    public static final int KEY_RSHIFT = GLFW.GLFW_KEY_RIGHT_SHIFT;
    public static final int KEY_LCONTROL = GLFW.GLFW_KEY_LEFT_CONTROL;
    public static final int KEY_RCONTROL = GLFW.GLFW_KEY_RIGHT_CONTROL;
    public static final int KEY_LMENU = GLFW.GLFW_KEY_LEFT_ALT;
    public static final int KEY_BACK = GLFW.GLFW_KEY_BACKSPACE;
    public static final int KEY_DELETE = GLFW.GLFW_KEY_DELETE;
    public static final int KEY_UP = GLFW.GLFW_KEY_UP;
    public static final int KEY_DOWN = GLFW.GLFW_KEY_DOWN;
    public static final int KEY_LEFT = GLFW.GLFW_KEY_LEFT;
    public static final int KEY_RIGHT = GLFW.GLFW_KEY_RIGHT;
    public static final int KEY_TAB = GLFW.GLFW_KEY_TAB;
    public static final int KEY_SPACE = GLFW.GLFW_KEY_SPACE;
    public static final int KEY_C = GLFW.GLFW_KEY_C;
    public static final int KEY_V = GLFW.GLFW_KEY_V;
    public static final int KEY_X = GLFW.GLFW_KEY_X;
    public static final int KEY_A = GLFW.GLFW_KEY_A;
    public static final int KEY_S = GLFW.GLFW_KEY_S;
    public static final int KEY_W = GLFW.GLFW_KEY_W;
    public static final int KEY_F = GLFW.GLFW_KEY_F;
    public static final int KEY_NONE = GLFW.GLFW_KEY_UNKNOWN;

    public static boolean isKeyDown(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key);
    }
}
