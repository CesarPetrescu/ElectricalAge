package mods.eln.railroad

import mods.eln.generic.GenericItemUsingDamageDescriptor
import net.minecraft.block.BlockRailBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import mods.eln.misc.getBlockState
import mods.eln.misc.isNothing

class ElectricMinecartItem(name: String) : GenericItemUsingDamageDescriptor(name) {
    override fun onItemUse(
        stack: ItemStack?,
        player: EntityPlayer?,
        world: World?,
        x: Int,
        y: Int,
        z: Int,
        side: Int,
        vx: Float,
        vy: Float,
        vz: Float
    ): Boolean {
        if (world == null || stack.isNothing()) return false
        return if (BlockRailBase.isRailBlock(world.getBlockState(x, y, z))) {
            if (!world.isRemote) {
                val minecart = EntityElectricMinecart(
                    world,
                    (x.toFloat() + 0.5f).toDouble(),
                    (y.toFloat() + 0.5f).toDouble(),
                    (z.toFloat() + 0.5f).toDouble()
                )
                if (stack.hasDisplayName()) {
                    minecart.customNameTag = stack.displayName
                }
                world.spawnEntity(minecart)
            }
            --stack.count
            true
        } else false
    }
}
