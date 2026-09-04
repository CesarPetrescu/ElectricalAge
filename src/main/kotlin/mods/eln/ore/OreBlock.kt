package mods.eln.ore

import mods.eln.Eln
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.block.properties.PropertyInteger
import net.minecraft.block.state.BlockStateContainer
import net.minecraft.block.state.IBlockState
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.item.ItemStack
import net.minecraft.util.NonNullList
import net.minecraft.world.World

class OreBlock : Block(Material.ROCK) {
    companion object {
        /** 1.8+: the ore variant that was plain metadata is a block state property (still 4 bits). */
        @JvmField
        val VARIANT: PropertyInteger = PropertyInteger.create("variant", 0, 15)
    }

    init {
        setHardness(3.0f) //The block hardness
        setResistance(5.0f) //The explosion resistance
        defaultState = blockState.baseState.withProperty(VARIANT, 0)
    }

    override fun createBlockState(): BlockStateContainer = BlockStateContainer(this, VARIANT)

    override fun getStateFromMeta(meta: Int): IBlockState = defaultState.withProperty(VARIANT, meta and 15)

    override fun getMetaFromState(state: IBlockState): Int = state.getValue(VARIANT)

    //Makes sure pick block works right
    override fun damageDropped(state: IBlockState): Int {
        return getMetaFromState(state)
    }

    //Puts all sub blocks into the creative inventory
    override fun getSubBlocks(tab: CreativeTabs, items: NonNullList<ItemStack>) {
        Eln.oreItem.getSubItems(tab, items)
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
}
