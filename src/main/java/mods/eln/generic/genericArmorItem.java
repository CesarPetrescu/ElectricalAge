package mods.eln.generic;

import net.minecraft.entity.Entity;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

public class genericArmorItem extends ItemArmor {

    String t1, t2;

    public enum ArmourType {
        // 1.9+: armor slots are EntityEquipmentSlot values, not the old 0..3 index.
        Helmet(EntityEquipmentSlot.HEAD),
        Chestplate(EntityEquipmentSlot.CHEST),
        Leggings(EntityEquipmentSlot.LEGS),
        Boots(EntityEquipmentSlot.FEET);

        private EntityEquipmentSlot _Value;

        private ArmourType(EntityEquipmentSlot Value) {
            this._Value = Value;
        }

        public EntityEquipmentSlot getValue() {
            return _Value;
        }
    }

    public genericArmorItem(ArmorMaterial par2EnumArmorMaterial, int par3, ArmourType Type, String t1, String t2) {
        super(par2EnumArmorMaterial, par3, Type.getValue());
        this.t1 = t1;
        this.t2 = t2;
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String layer) {
        if (this.armorType == EntityEquipmentSlot.LEGS) {
            return t2;
        } else {
            return t1;
        }
    }
}
