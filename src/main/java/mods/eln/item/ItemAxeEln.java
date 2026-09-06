package mods.eln.item;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Tier;

public class ItemAxeEln extends AxeItem {

    private final String descriptionId;

    public ItemAxeEln(Tier tier, String name, Properties properties) {
        super(tier, properties);
        this.descriptionId = "item." + name + ".name";
    }

    @Override
    public String getDescriptionId() {
        return descriptionId;
    }
}
