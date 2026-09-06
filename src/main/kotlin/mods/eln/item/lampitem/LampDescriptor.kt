package mods.eln.item.lampitem

import mods.eln.Eln
import mods.eln.i18n.I18N
import mods.eln.item.GenericItemUsingDamageDescriptorUpgrade
import mods.eln.misc.Utils
import mods.eln.misc.VoltageLevelColor
import mods.eln.sim.mna.component.Resistor
import mods.eln.wiki.Data
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import kotlin.math.abs
import kotlin.math.pow
import mods.eln.misc.isNothing

class LampDescriptor(name: String, iconName: String, val lampData: SpecificLampData) : GenericItemUsingDamageDescriptorUpgrade(name) {

    init {
        setDefaultIcon(iconName)
        voltageLevelColor = VoltageLevelColor.fromVoltage(lampData.nominalU)
        LampLists.registeredLampList.add(lampData)
    }

    override fun setParent(item: Item?, damage: Int) {
        super.setParent(item, damage)
        Data.addLight(newItemStack())
    }

    fun getLifeInTag(stack: ItemStack): Double {
        if (!stack.hasTagCompound()) stack.tagCompound /* TODO(components) */ = getDefaultNBT()

        return if (stack.tagCompound /* TODO(components) */!!.contains("life")) stack.tagCompound /* TODO(components) */!!.getDouble("life")
        else 24.0 // default 24 hours
    }

    fun setLifeInTag(stack: ItemStack, life: Double) {
        if (!stack.hasTagCompound()) stack.tagCompound /* TODO(components) */ = getDefaultNBT()
        stack.tagCompound /* TODO(components) */!!.putDouble("life", life)
    }

    override fun getDefaultNBT(): CompoundTag {
        val tag = CompoundTag()
        tag.putDouble("life", lampData.technology.nominalLifeInHours)
        return tag
    }

    fun applyTo(resistor: Resistor) {
        resistor.resistance = lampData.resistance
    }

    /**
     * This function is currently set up to be called once per second (IRL). If the NBT update bug is ever fixed, this
     * function should be updated to run once per tick by dividing the life lost by 20 before calculating the new life.
     * See https://www.desmos.com/calculator/0uuzozsiuu for a plot of the lamp aging function.
     */
    fun decreaseLampLife(lampStack: ItemStack, appliedVoltage: Double): Double {
        val currentLife = getLifeInTag(lampStack)

        // resetLampLifeFlag should be automatically set to false after 1-2 seconds (see usage in Simulator.java)
        if (currentLife > lampData.technology.nominalLifeInHours || LampLists.resetLampLifeFlag) {
            setLifeInTag(lampStack, lampData.technology.nominalLifeInHours)
            return getLifeInTag(lampStack)
        }

        if (!lampData.technology.infiniteLifeEnabled) {
            // Division by 3600 converts seconds to hours (IRL)
            val lifeLost = when {
                // Life lost per second increases exponentially when voltage is above nominal (10x as fast at 1.25x nominal)
                abs(appliedVoltage) > lampData.nominalU -> {
                    10.0.pow(4.0 / lampData.nominalU).pow(abs(appliedVoltage) - lampData.nominalU) / 3600.0
                }
                // Life lost per second increases linearly when voltage is between nominal and minimal
                abs(appliedVoltage) in (lampData.nominalU * lampData.technology.minimalUFactor)..lampData.nominalU -> {
                    val slope = 1.0 / (lampData.nominalU * (1.0 - lampData.technology.minimalUFactor))
                    val intercept = 1.0 - (slope * lampData.nominalU)

                    ((slope * abs(appliedVoltage)) + intercept) / 3600.0
                }
                // Lamp does not lose life when voltage is below minimal (no light produced)
                else -> 0.0
            }

            var newLife = currentLife - lifeLost
            if (newLife < 0.0) newLife = 0.0

            setLifeInTag(lampStack, newLife)
            return newLife
        } else {
            return currentLife
        }
    }

    override fun addInformation(itemStack: ItemStack?, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)

        list.add(I18N.tr($$"Nominal voltage: %1$V", Utils.plotValue(lampData.nominalU)))
        list.add(I18N.tr($$"Nominal power: %1$W", Utils.plotValue(lampData.nominalP)))
        list.add(I18N.tr("Resistance: %1$\u2126", Utils.plotValue(lampData.resistance)))
        list.add(I18N.tr("Nominal brightness: %1$", Utils.plotValue(lampData.nominalLightValue.toDouble())))
        list.add(I18N.tr($$"Nominal lifetime: %1$h", lampData.technology.nominalLifeInHours))

        if (!itemStack.isNothing()) {
            if (Eln.config.getBooleanOrElse("debug.logging.enabled", false)) {
                list.add(I18N.tr($$"Current lifetime: %1$h", getLifeInTag(itemStack)))
            }
            list.add(I18N.tr("Condition: %1$", getLampCondition(itemStack)))
        }
    }

    private fun getLampCondition(itemStack: ItemStack): String {
        return if (!itemStack.hasTagCompound() || !itemStack.tagCompound /* TODO(components) */!!.contains("life")) {
            I18N.tr("New")
        } else {
            val lampLife = getLifeInTag(itemStack)

            if (lampLife == lampData.technology.nominalLifeInHours) I18N.tr("New")
            else if (lampLife > (0.5 * lampData.technology.nominalLifeInHours)) I18N.tr("Good")
            else if (lampLife > (0.15 * lampData.technology.nominalLifeInHours)) I18N.tr("Used")
            else if (lampLife > (0.01 * lampData.technology.nominalLifeInHours)) I18N.tr("Bad")
            else I18N.tr("End of life")
        }
    }

}