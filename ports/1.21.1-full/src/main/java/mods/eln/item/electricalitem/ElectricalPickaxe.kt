package mods.eln.item.electricalitem

import mods.eln.wiki.Data
import net.minecraft.block.material.Material
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

class ElectricalPickaxe(name: String, strengthOn: Float, strengthOff: Float,
                        energyStorage: Double, energyPerBlock: Double, chargePower: Double) : ElectricalTool(name, strengthOn, strengthOff, energyStorage, energyPerBlock, chargePower) {

    override fun setParent(item: Item, damage: Int) {
        super.setParent(item, damage)
        Data.addPortable(newItemStack())
    }

    override fun getDestroySpeed(stack: ItemStack, state: BlockState): Float {
        return when {
            state.material in pickaxeEffectiveAgainst -> getStrength(stack)
            state.block in blocksEffectiveAgainst -> getStrength(stack)
            else -> super.getDestroySpeed(stack, state)
        }
    }

    private val pickaxeEffectiveAgainst = arrayOf(
        Material.IRON,
        Material.GLASS,
        Material.ANVIL,
        Material.ROCK
    )
}
