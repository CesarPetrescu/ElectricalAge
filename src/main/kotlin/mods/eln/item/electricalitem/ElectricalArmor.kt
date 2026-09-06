package mods.eln.item.electricalitem

import mods.eln.generic.genericArmorItem
import mods.eln.i18n.I18N.tr
import mods.eln.item.electricalinterface.IItemEnergyBattery
import mods.eln.misc.Utils
import mods.eln.wiki.Data
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.level.Level
import net.minecraftforge.common.ISpecialArmor
import net.minecraftforge.common.ISpecialArmor.ArmorProperties

class ElectricalArmor(
    par2EnumArmorMaterial: ArmorMaterial?,
    par3: Int,
    type: ArmourType?,
    t1: String?,
    t2: String?,  //String icon,
    var energyStorage: Double,
    var chargePower: Double,
    var ratioMax: Double,
    var ratioMaxEnergy: Double,
    var energyPerDamage: Double
    ) : genericArmorItem(par2EnumArmorMaterial, par3, type, t1, t2), IItemEnergyBattery, ISpecialArmor {


    override fun getProperties(player: LivingEntity, armor: ItemStack, source: DamageSource, damage: Double, slot: Int): ArmorProperties {
        return ArmorProperties(100, Math.min(1.0, getEnergy(armor) / ratioMaxEnergy) * ratioMax, (getEnergy(armor) / energyPerDamage * 25.0).toInt())
    }

    override fun getArmorDisplay(player: Player, armor: ItemStack, slot: Int): Int {
        return (Math.min(1.0, getEnergy(armor) / ratioMaxEnergy) * ratioMax * 20).toInt()
    }

    override fun damageArmor(entity: LivingEntity, stack: ItemStack, source: DamageSource, damage: Int, slot: Int) {
        var e = getEnergy(stack)
        e = Math.max(0.0, e - damage * energyPerDamage)
        setEnergy(stack, e)
        Utils.println("armor hit  damage=" + damage + " energy=" + e + " energyLost=" + damage * energyPerDamage)
    }

    override fun getIsRepairable(par1ItemStack: ItemStack, par2ItemStack: ItemStack): Boolean {
        return false
    }

    override fun hasColor(par1ItemStack: ItemStack): Boolean {
        return false
    }

    val defaultNBT: CompoundTag
        get() {
            val nbt = CompoundTag()
            nbt.putDouble("energy", 0.0)
            nbt.putBoolean("powerOn", false)
            nbt.putInt("rand", (Math.random() * 0xFFFFFFF).toInt())
            return nbt
        }

    protected fun getNbt(stack: ItemStack): CompoundTag? {
        var nbt = stack.tagCompound /* TODO(components) */
        if (nbt == null) {
            stack.tagCompound /* TODO(components) */ = defaultNBT.also { nbt = it }
        }
        return nbt
    }

    fun getPowerOn(stack: ItemStack): Boolean {
        return getNbt(stack)!!.getBoolean("powerOn")
    }

    fun setPowerOn(stack: ItemStack, value: Boolean) {
        getNbt(stack)!!.setBoolean("powerOn", value)
    }

    override fun addInformation(itemStack: ItemStack, world: Level?, list: MutableList<String>, flag: TooltipFlag) {
        super.addInformation(itemStack, world, list, flag)
        list.add(tr("Charge power: %1\$W", chargePower.toInt()))
        list.add(tr("Stored energy: %1\$J (%2$%)", getEnergy(itemStack),
            (getEnergy(itemStack) / energyStorage * 100).toInt()))
        //list.add("Power button is " + (getPowerOn(itemStack) ? "ON" : "OFF"));
    }

    override fun getEnergy(stack: ItemStack): Double {
        return getNbt(stack)!!.getDouble("energy")
    }

    override fun setEnergy(stack: ItemStack, value: Double) {
        getNbt(stack)!!.setDouble("energy", value)
    }

    override fun getEnergyMax(stack: ItemStack): Double {
        return energyStorage
    }

    override fun getChargePower(stack: ItemStack): Double {
        return chargePower
    }

    override fun getDischagePower(stack: ItemStack): Double {
        return 0.0
    }

    override fun getPriority(stack: ItemStack): Int {
        return 0
    }

    override fun electricalItemUpdate(stack: ItemStack, time: Double) {}

    override fun getItemEnchantability(): Int {
        return 0;
    }

    init {
        //rIcon = ResourceLocation.fromNamespaceAndPath("eln", icon);
        Data.addPortable(ItemStack(this))
    }
}
