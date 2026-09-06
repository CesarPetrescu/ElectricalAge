package mods.eln.railroad

import mods.eln.generic.GenericItemUsingDamageDescriptor
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import mods.eln.misc.getBlockState
import mods.eln.misc.isNothing

class ElectricMinecartItem(name: String) : GenericItemUsingDamageDescriptor(name) {
    override fun onItemUse(
        stack: ItemStack?,
        player: Player?,
        world: Level?,
        x: Int,
        y: Int,
        z: Int,
        side: Int,
        vx: Float,
        vy: Float,
        vz: Float
    ): Boolean {
        if (world == null || stack.isNothing()) return false
        return if (BaseRailBlock.isRailBlock(world.getBlockState(x, y, z))) {
            if (!world.isClientSide) {
                val minecart = EntityElectricMinecart(
                    world,
                    (x.toFloat() + 0.5f).toDouble(),
                    (y.toFloat() + 0.5f).toDouble(),
                    (z.toFloat() + 0.5f).toDouble()
                )
                if (stack.hasDisplayName()) {
                    minecart.customNameTag = stack.hoverName
                }
                world.addFreshEntity(minecart)
            }
            --stack.count
            true
        } else false
    }
}
