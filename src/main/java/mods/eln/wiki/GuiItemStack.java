package mods.eln.wiki;

import mods.eln.gui.Gui;
import mods.eln.gui.GuiHelper;
import mods.eln.gui.IGuiObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public class GuiItemStack extends Gui implements IGuiObject {
    int posX, posY, h = 18, w = 18;
    final ItemStack stack;
    public final GuiHelper helper;

    public GuiItemStack(int x, int y, ItemStack stack, GuiHelper helper) {
        posX = x; posY = y;
        this.stack = stack == null ? ItemStack.EMPTY : stack;
        this.helper = helper;
    }

    boolean contains(int x, int y) {
        return x >= posX && y >= posY && x < posX + w && y < posY + h;
    }

    @Override
    public void idraw(int x, int y, float partialTick) {
        var g = graphics();
        g.fill(posX - 1, posY - 1, posX + 17, posY + 17, contains(x, y) ? 0xFF72CDB3 : 0xFF465B66);
        g.fill(posX, posY, posX + 16, posY + 16, 0xFF26343D);
        // Flush background before the custom OBJ renderer's immediate draw calls.
        g.flush();
        if (!stack.isEmpty()) {
            g.renderItem(stack, posX, posY);
            g.renderItemDecorations(Minecraft.getInstance().font, stack, posX, posY);
        }
    }

    void renderTooltip(int x, int y) {
        if (!stack.isEmpty()) graphics().renderTooltip(Minecraft.getInstance().font, stack, x, y);
    }

    @Override
    public void idraw2(int x, int y) { if (contains(x, y)) renderTooltip(x, y); }
    @Override
    public boolean ikeyTyped(char key, int code) { return false; }
    @Override
    public void imouseClicked(int x, int y, int button) {
        if (button == 0 && contains(x, y) && !stack.isEmpty())
            Minecraft.getInstance().setScreen(new ItemDefault(stack, helper.screen));
    }
    @Override
    public void imouseMove(int x, int y) {}
    @Override
    public void imouseMovedOrUp(int x, int y, int button) {}
    @Override
    public void translate(int x, int y) { posX += x; posY += y; }
    @Override
    public int getYMax() { return posY + h; }
}
