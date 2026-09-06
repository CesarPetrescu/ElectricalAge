package mods.eln.generic;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.common.ISpecialArmor;

public class SharedItem extends GenericItemUsingDamage<GenericItemUsingDamageDescriptor> implements ISpecialArmor {

    public SharedItem(String name) {
        super();
        setTranslationKey(name);
        if (name.contains(":")) {
            setRegistryName(name);
        } else {
            setRegistryName(mods.eln.Eln.MODID, name);
        }
        setCreativeTab(mods.eln.Eln.Tab);
    }

    public SharedItem() {
        this("eln:shared_item");
    }

    @Override
    public ArmorProperties getProperties(LivingEntity player,
                                         ItemStack armor, DamageSource source, double damage, int slot) {
        return new ArmorProperties(10, 1.0, 10000);
    }

    @Override
    public int getArmorDisplay(Player player, ItemStack armor, int slot) {
        return 4;
    }

    @Override
    public void damageArmor(LivingEntity entity, ItemStack stack,
                            DamageSource source, int damage, int slot) {
    }
}
