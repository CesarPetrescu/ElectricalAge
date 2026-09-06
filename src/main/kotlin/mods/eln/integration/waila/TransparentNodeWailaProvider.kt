package mods.eln.integration.waila

import com.google.common.cache.CacheLoader
import net.minecraftforge.fml.common.Optional
import mcp.mobius.waila.api.IWailaConfigHandler
import mcp.mobius.waila.api.IWailaDataAccessor
import mcp.mobius.waila.api.IWailaDataProvider
import mcp.mobius.waila.api.SpecialChars
import mods.eln.misc.Coordinate
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

@Optional.Interface(iface = "mcp.mobius.waila.api.IWailaDataProvider", modid = "waila")
class TransparentNodeWailaProvider : IWailaDataProvider {
    override fun getWailaBody(itemStack: ItemStack?, currenttip: MutableList<String>,
                              accessor: IWailaDataAccessor, config: IWailaConfigHandler?): MutableList<String> {
        val coord = Coordinate(accessor.position.x, accessor.position.y, accessor.position.z,
            accessor.level)
        try {
            WailaCache.nodes.get(coord)?.forEach { entry ->
                if (entry.values.size == 1) {
                    currenttip.add("${entry.label}: ${SpecialChars.WHITE}${entry.values.single()}")
                } else {
                    currenttip.add("${entry.label}:")
                    entry.values.forEach { currenttip.add("${SpecialChars.WHITE}$it") }
                }
            }
        } catch(e: CacheLoader.InvalidCacheLoadException) {
            //This is probably just it complaining about the cache returning null. Should be safe to ignore.
        }

        return currenttip
    }

    override fun getWailaStack(accessor: IWailaDataAccessor?, config: IWailaConfigHandler?): ItemStack {
        return ItemStack.EMPTY
    }

    override fun getWailaTail(itemStack: ItemStack?, currenttip: MutableList<String>, accessor: IWailaDataAccessor?, config: IWailaConfigHandler?): MutableList<String> {
        return currenttip
    }

    override fun getNBTData(player: ServerPlayer?, te: BlockEntity?, tag: CompoundTag, world: Level?, pos: BlockPos?): CompoundTag {
        return tag
    }

    override fun getWailaHead(itemStack: ItemStack?, currenttip: MutableList<String>, accessor: IWailaDataAccessor?, config: IWailaConfigHandler?): MutableList<String> {
        return currenttip
    }


}
