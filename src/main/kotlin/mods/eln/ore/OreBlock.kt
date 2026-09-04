package mods.eln.ore

import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import mods.eln.Eln
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.world.World

class OreBlock : Block(Material.ROCK) {
    init {
        setHardness(3.0f) //The block hardness
        setResistance(5.0f) //The explosion resistance
    }

    //Makes sure pick block works right
    override fun damageDropped(i: Int): Int {
        return i
    }

    //Puts all sub blocks into the creative inventory
    override fun getSubBlocks(
        i: Item,
        tab: CreativeTabs,
        l: List<*>?
    ) {
        // Block#getSubBlocks exposes a raw list to Kotlin, but the item path consumes ItemStack entries.
        @Suppress("UNCHECKED_CAST")
        Eln.oreItem.getSubItems(i, tab, l as MutableList<ItemStack?>?)
    }


    fun getBlockDropped(
        @Suppress("UNUSED_PARAMETER") w: World?,
        @Suppress("UNUSED_PARAMETER") x: Int,
        @Suppress("UNUSED_PARAMETER") y: Int,
        @Suppress("UNUSED_PARAMETER") z: Int,
        meta: Int,
        fortune: Int
    ): ArrayList<ItemStack> {
        val desc = Eln.oreItem.getDescriptor(meta) ?: return ArrayList()
        return desc.getBlockDropped(fortune)
    }

    override fun breakBlock(par1World: World, par2: Int, par3: Int, par4: Int, par5: Block, par6: Int) {
        super.breakBlock(par1World, par2, par3, par4, par5, par6)
        if (par1World.isRemote) return
    }
}
