package mods.eln.node

import net.minecraft.world.level.block.Block
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class NodeBlockItem(b: Block?) : BlockItem(b) {

    override fun getMetadata(damageValue: Int): Int {
        return damageValue
    }

    val block: NodeBlock
        get() = Block.getBlockFromItem(this) as NodeBlock

    init {
        translationKey = "NodeBlockItem"
    }
}
