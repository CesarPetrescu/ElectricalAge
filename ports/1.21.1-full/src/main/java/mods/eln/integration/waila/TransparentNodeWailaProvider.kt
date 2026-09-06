package mods.eln.integration.waila

import com.google.common.cache.CacheLoader
import mcp.mobius.waila.api.IWailaConfigHandler
import mcp.mobius.waila.api.IWailaDataAccessor
import mcp.mobius.waila.api.IWailaDataProvider
import mods.eln.misc.Coordinate
import mods.eln.node.transparent.TransparentNodeEntity
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.ChatFormatting
import net.minecraft.world.level.Level
import net.minecraftforge.fml.common.Optional

@Optional.Interface(iface = "mcp.mobius.waila.api.IWailaDataProvider", modid = "Waila")
class TransparentNodeWailaProvider : IWailaDataProvider {
    override fun getWailaBody(itemStack: ItemStack?, currenttip: MutableList<String>,
                              accessor: IWailaDataAccessor, config: IWailaConfigHandler?): MutableList<String> {
        val coord = Coordinate(accessor.position.x, accessor.position.y, accessor.position.z,
            accessor.world)
        try {
            val data = WailaCache.nodes.get(coord)
            data?.data?.forEach { currenttip.add("${it.key}: ${TextFormatting.WHITE}${it.value}") }
        } catch(e: CacheLoader.InvalidCacheLoadException) {
            //This is probably just it complaining about the cache returning null. Should be safe to ignore.
        }

        return currenttip
    }

    override fun getNBTData(player: ServerPlayer?, te: BlockEntity?, tag: CompoundTag?, world: Level?, pos: BlockPos?): CompoundTag {
        return tag ?: CompoundTag()
    }

    override fun getWailaStack(accessor: IWailaDataAccessor, config: IWailaConfigHandler?): ItemStack {
        val coord = Coordinate(accessor.position.x, accessor.position.y, accessor.position.z,
            accessor.world)
        return try {
            WailaCache.nodes.get(coord)?.itemStack ?: ItemStack.EMPTY
        } catch (e: CacheLoader.InvalidCacheLoadException) {
            ItemStack.EMPTY
        }
    }

    override fun getWailaTail(itemStack: ItemStack?, currenttip: MutableList<String>, accessor: IWailaDataAccessor?, config: IWailaConfigHandler?): MutableList<String> {
        return currenttip
    }

    override fun getWailaHead(itemStack: ItemStack?, currenttip: MutableList<String>, accessor: IWailaDataAccessor?, config: IWailaConfigHandler?): MutableList<String> {
        return currenttip
    }


}
