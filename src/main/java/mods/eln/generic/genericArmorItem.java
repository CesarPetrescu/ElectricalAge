package mods.eln.generic;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

public class genericArmorItem extends ArmorItem {

    String t1, t2;

    public enum ArmourType {
        // 1.9+: armor slots are EquipmentSlot values, not the old 0..3 index.
        Helmet(EquipmentSlot.HEAD),
        Chestplate(EquipmentSlot.CHEST),
        Leggings(EquipmentSlot.LEGS),
        Boots(EquipmentSlot.FEET);

        private EquipmentSlot _Value;

        private ArmourType(EquipmentSlot Value) {
            this._Value = Value;
        }

        public EquipmentSlot getValue() {
            return _Value;
        }
    }

    public genericArmorItem(ArmorMaterial par2EnumArmorMaterial, int par3, ArmourType Type, String t1, String t2) {
        super(par2EnumArmorMaterial, par3, Type.getValue());
        this.t1 = t1;
        this.t2 = t2;
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String layer) {
        if (this.armorType == EquipmentSlot.LEGS) {
            return t2;
        } else {
            return t1;
        }
    }
}
