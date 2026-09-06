package mods.eln.ore

import mods.eln.generic.GenericItemBlockUsingDamageDescriptor
import mods.eln.wiki.Data
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * One ore. Since 1.21 each ore is its own [OreBlock] (no more metadata variants) and its world
 * generation is a data-generated placed feature built from the spawn numbers kept here.
 */
class OreDescriptor(
    name: String?, var metadata: Int,
    var spawnRate: Int, var spawnSizeMin: Int, var spawnSizeMax: Int, var spawnHeightMin: Int, var spawnHeightMax: Int
) : GenericItemBlockUsingDamageDescriptor(name) {

    lateinit var block: OreBlock

    override fun setParent(item: Item, damage: Int) {
        super.setParent(item, damage)
        Data.addOre(newItemStack())
    }

    fun getBlockDropped(@Suppress("UNUSED_PARAMETER") fortune: Int): ArrayList<ItemStack> {
        val list = ArrayList<ItemStack>()
        list.add(newItemStack())
        return list
    }
}
