/**
 *
 * Copyright © SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 *
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 *
 * 1.0, or MMPL. Please check the contents of the license located in
 *
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 */
package mods.eln.fluid

import net.minecraftforge.fml.common.eventhandler.Event
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraft.item.ItemBucket
import net.minecraft.item.ItemStack
import net.minecraft.util.math.RayTraceResult
import net.minecraft.world.World
import net.minecraftforge.event.entity.player.FillBucketEvent

object BucketHandler {
    @JvmField
    var buckets: MutableMap<Block, ItemBucket> = mutableMapOf()
    @SubscribeEvent
    fun onBucketFill(event: FillBucketEvent) {
        val result = fillCustomBucket(event.world, event.target) ?: return
        event.result = result
        event.setResult(Event.Result.ALLOW)
    }

    private fun fillCustomBucket(world: World, pos: RayTraceResult): ItemStack? {
        val block = world.getBlock(pos.blockPos.x, pos.blockPos.y, pos.blockPos.z)
        val bucket = buckets[block]
        return if (bucket != null && world.getBlockMetadata(pos.blockPos.x, pos.blockPos.y, pos.blockPos.z) == 0) {
            world.setBlockToAir(pos.blockPos.x, pos.blockPos.y, pos.blockPos.z)
            ItemStack(bucket)
        } else null
    }
}
