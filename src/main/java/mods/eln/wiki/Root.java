package mods.eln.wiki;

import mods.eln.gui.GuiLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import static mods.eln.i18n.I18N.tr;

public class Root extends Default {

    public Root(Screen preview) {
        super(preview);

    }


    @Override
    public void initGui() {

        super.initGui();

        rebuild();

    }

    @Override
    protected void searchChanged(String text) { rebuild(); }

    private void rebuild() {
        extender.clear();
        int y = 8;
        if (query().isBlank()) {
            y = CircuitLessons.addLinks(this, y);
            for (var group : WikiContent.groups().entrySet())
                y = addStackGroupe(group.getValue(), group.getKey(), y);
        } else {
            var results = WikiContent.search(query());
            y = addStackGroupe(results, tr("Search results: %1$", results.size()), y);
            if (results.isEmpty()) extender.add(new GuiLabel(8, y, tr("No matching items.")));
        }
        extender.setSliderPosition(0);
    }

    int addStackGroupe(List<ItemStack> list, String name, int y) {
        int idx = 0;
        int stackPerLine = Math.max(1, (extender.contentWidth() - 16) / 20);
        extender.add(new GuiLabel(8, y, name));
        y += 15;
        for (ItemStack stack : list) {
            GuiItemStack gui = new GuiItemStack((idx % stackPerLine) * 20 + 8, y + (idx / stackPerLine) * 20, stack, helper);
            extender.add(gui);
            idx++;
        }
        y += ((idx + stackPerLine - 1) / stackPerLine) * 20 + 14;
        return y;
    }

}
