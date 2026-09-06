package mods.eln.generic;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.BlockItem;

public class GenericItemBlock extends BlockItem {

    int textureIdOffset;

    public String[] subNames = {
        "Copper", "Silver", "Gold"
    };

    public GenericItemBlock(Block b, int textureIdOffset, String ItemName, String[] subNames) {
        super(b);
        this.textureIdOffset = textureIdOffset;
        this.subNames = subNames;
        setHasSubtypes(true);
        setTranslationKey("wireItemBlock");
    }

	/*
    @Override//caca1.5.1
    public int getIconFromDamage(int par1) {
        return textureIdOffset + par1;
    }
	@Override
	public String getTextureFile () {
		return CommonProxy.ITEMS_PNG;
	}
	*/

    @Override
    public int getMetadata(int damageValue) {
        return damageValue;
    }

	/*
	@Override //caca1.5.1
	public String getItemNameIS(ItemStack itemstack) {
		return getItemName() + "." + subNames[itemstack.getItemDamage() /* TODO(flattening) */];
	}*/
}
