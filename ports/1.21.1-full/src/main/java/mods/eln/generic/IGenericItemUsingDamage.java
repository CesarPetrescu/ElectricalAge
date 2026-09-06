package mods.eln.generic;

import net.minecraft.world.item.ItemStack;

public interface IGenericItemUsingDamage {

    public GenericItemUsingDamageDescriptor getDescriptor(int damage);

    public GenericItemUsingDamageDescriptor getDescriptor(ItemStack itemStack);
}
