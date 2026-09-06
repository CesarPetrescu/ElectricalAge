package mods.eln.item;

import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;

public class ItemPickaxeEln extends PickaxeItem {

    private final String descriptionId;

    public ItemPickaxeEln(Tier tier, String name, Properties properties) {
        super(tier, properties);
        this.descriptionId = "item." + name + ".name";
    }

    @Override
    public String getDescriptionId() {
        return descriptionId;
    }
}
