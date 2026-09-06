package mods.eln.item.electricalitem

import mods.eln.generic.genericArmorItem
import mods.eln.i18n.I18N.tr
import mods.eln.item.electricalinterface.IItemEnergyBattery
import mods.eln.misc.Utils
import mods.eln.misc.editTag
import mods.eln.misc.tagCompound
import mods.eln.wiki.Data
import net.minecraft.core.Holder
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ArmorMaterial
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import java.util.function.Consumer

/**
 * Armor that absorbs damage with stored energy instead of durability. Forge's `ISpecialArmor`
 * is gone since 1.17: the absorption is [absorb], applied from a `LivingDamageEvent.Pre`
 * listener (see [mods.eln.server.ServerEventListener]), and durability loss is turned into energy
 * loss in [damageItem]. The 1.7.10 per-stack armor bar (`getArmorDisplay`) has no 1.21 equivalent.
 */
class ElectricalArmor(
    material: Holder<ArmorMaterial>,
    type: ArmourType,
    t1: String,
    t2: String,
    properties: Properties,
    var energyStorage: Double,
    var chargePower: Double,
    var ratioMax: Double,
    var ratioMaxEnergy: Double,
    var energyPerDamage: Double
) : genericArmorItem(material, type, t1, t2, properties), IItemEnergyBattery {

    /** The fraction of incoming damage this piece absorbs right now (1.7.10's ArmorProperties ratio). */
    fun absorbRatio(armor: ItemStack): Double = Math.min(1.0, getEnergy(armor) / ratioMaxEnergy) * ratioMax

    /** Damage points this piece can still absorb (1.7.10's ArmorProperties max). */
    fun absorbMax(armor: ItemStack): Double = getEnergy(armor) / energyPerDamage * 25.0

    override fun <T : LivingEntity> damageItem(stack: ItemStack, amount: Int, entity: T?, onBroken: Consumer<Item>): Int {
        var e = getEnergy(stack)
        e = Math.max(0.0, e - amount * energyPerDamage)
        setEnergy(stack, e)
        Utils.println("armor hit  damage=" + amount + " energy=" + e + " energyLost=" + amount * energyPerDamage)
        return 0
    }

    override fun isValidRepairItem(par1ItemStack: ItemStack, par2ItemStack: ItemStack): Boolean {
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

    protected fun getNbt(stack: ItemStack): CompoundTag {
        var nbt = stack.tagCompound
        if (nbt == null) {
            stack.tagCompound = defaultNBT.also { nbt = it }
        }
        return nbt!!
    }

    fun getPowerOn(stack: ItemStack): Boolean {
        return getNbt(stack).getBoolean("powerOn")
    }

    fun setPowerOn(stack: ItemStack, value: Boolean) {
        getNbt(stack)
        stack.editTag { it.putBoolean("powerOn", value) }
    }

    override fun appendHoverText(itemStack: ItemStack, context: Item.TooltipContext, list: MutableList<Component>, flag: TooltipFlag) {
        super.appendHoverText(itemStack, context, list, flag)
        list.add(Component.literal(tr("Charge power: %1\$W", chargePower.toInt())))
        list.add(Component.literal(tr("Stored energy: %1\$J (%2$%)", getEnergy(itemStack),
            (getEnergy(itemStack) / energyStorage * 100).toInt())))
        //list.add("Power button is " + (getPowerOn(itemStack) ? "ON" : "OFF"));
    }

    override fun getEnergy(stack: ItemStack): Double {
        return getNbt(stack).getDouble("energy")
    }

    override fun setEnergy(stack: ItemStack, value: Double) {
        getNbt(stack)
        stack.editTag { it.putDouble("energy", value) }
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

    override fun getEnchantmentValue(): Int {
        return 0
    }

    init {
        Data.addPortable { ItemStack(this) }
    }

    companion object {
        /**
         * 1.7.10's ISpecialArmor pass: every worn electrical piece absorbs its ratio of the damage
         * (capped by its remaining energy) and pays for it in energy.
         */
        @JvmStatic
        fun absorb(event: LivingDamageEvent.Pre) {
            val entity = event.entity
            var damage = event.newDamage.toDouble()
            if (damage <= 0) return
            for (slot in arrayOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                val stack = entity.getItemBySlot(slot)
                val armor = stack.item as? ElectricalArmor ?: continue
                val absorbed = Math.min(damage * armor.absorbRatio(stack), armor.absorbMax(stack))
                if (absorbed <= 0) continue
                damage -= absorbed
                armor.setEnergy(stack, Math.max(0.0, armor.getEnergy(stack) - absorbed * armor.energyPerDamage))
            }
            event.newDamage = damage.toFloat()
        }
    }
}
