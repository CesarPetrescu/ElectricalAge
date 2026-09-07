package mods.eln.wiki;

import mods.eln.misc.Utils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

public class ItemDefault extends Default {
    public interface IPlugIn {
        int top(int y, GuiVerticalExtender extender, ItemStack stack);
        int bottom(int y, GuiVerticalExtender extender, ItemStack stack);
    }

    private final ItemStack stack;

    public ItemDefault(ItemStack stack, Screen previewScreen) {
        super(previewScreen);
        this.stack = stack.copy();
    }

    @Override
    public void initGui() {
        super.initGui();
        if (stack.isEmpty()) return;
        extender.add(new GuiItemStack(8, 8, stack, helper));
        WikiText title = new WikiText(34, 10, stack.getHoverName().getString(), extender.contentWidth() - 42);
        extender.add(title);
        int y = Math.max(34, title.getYMax() + 10);
        Object desc = Utils.getItemObject(stack);
        IPlugIn plugIn = desc instanceof IPlugIn p ? p : null;
        if (plugIn != null) y = plugIn.top(y, extender, stack);
        y = WikiRecipes.addCrafting(extender, y, stack);
        y = WikiRecipes.addProcessing(extender, y, stack);
        if (plugIn != null) plugIn.bottom(y, extender, stack);
    }
}
