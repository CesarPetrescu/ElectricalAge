package mods.eln.generic;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;

/**
 * An armor piece that draws its own layer textures. 1.21 resolves textures through the
 * {@link ArmorMaterial.Layer}s; the NeoForge per-item hook keeps the mod's own paths.
 */
public class genericArmorItem extends ArmorItem {

    final ResourceLocation t1, t2;
    /** The 1.7.10 lang key ("item.Copper Helmet.name"); set by the registration through {@link #setTranslationKey}. */
    private String descriptionId;

    public enum ArmourType {
        Helmet(ArmorItem.Type.HELMET),
        Chestplate(ArmorItem.Type.CHESTPLATE),
        Leggings(ArmorItem.Type.LEGGINGS),
        Boots(ArmorItem.Type.BOOTS);

        private final ArmorItem.Type _Value;

        ArmourType(ArmorItem.Type Value) {
            this._Value = Value;
        }

        public ArmorItem.Type getValue() {
            return _Value;
        }
    }

    public genericArmorItem(Holder<ArmorMaterial> material, ArmourType Type, String t1, String t2, Properties properties) {
        super(material, Type.getValue(), properties);
        this.t1 = ResourceLocation.parse(t1);
        this.t2 = ResourceLocation.parse(t2);
    }

    public genericArmorItem setTranslationKey(String name) {
        this.descriptionId = "item." + name + ".name";
        return this;
    }

    @Override
    public String getDescriptionId() {
        return descriptionId != null ? descriptionId : super.getDescriptionId();
    }

    /** 1.7.10's `getArmorTexture(stack, entity, slot, type)`: layer 2 for the leggings, layer 1 otherwise. */
    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
        return slot == EquipmentSlot.LEGS ? t2 : t1;
    }
}
