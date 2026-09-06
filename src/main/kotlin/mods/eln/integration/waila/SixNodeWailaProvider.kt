package mods.eln.integration.waila

import com.google.common.cache.CacheLoader
import net.minecraftforge.fml.common.Optional
import mcp.mobius.waila.api.IWailaConfigHandler
import mcp.mobius.waila.api.IWailaDataAccessor
import mcp.mobius.waila.api.IWailaDataProvider
import mcp.mobius.waila.api.SpecialChars
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import mods.eln.misc.isNothing

@Optional.Interface(iface = "mcp.mobius.waila.api.IWailaDataProvider", modid = "waila")
class SixNodeWailaProvider : IWailaDataProvider {
    private fun getSixData(accessor: IWailaDataAccessor): SixNodeWailaData? {
        val coord = Coordinate(accessor.position.x, accessor.position.y, accessor.position.z,
            accessor.level)
        val side = Direction.from(accessor.side)
        var sixData: SixNodeWailaData? = null
        try {
            sixData = WailaCache.sixNodes.get(SixNodeCoordonate(coord, side))
        } catch(e: CacheLoader.InvalidCacheLoadException) {
        }

        return sixData
    }

    override fun getWailaBody(itemStack: ItemStack?, currenttip: MutableList<String>, accessor: IWailaDataAccessor,
                              config: IWailaConfigHandler?): MutableList<String> {
        getSixData(accessor)?.data?.forEach {
            currenttip.add("${it.key}: ${SpecialChars.WHITE}${it.value}")
        }

        return currenttip
    }

    override fun getWailaStack(accessor: IWailaDataAccessor, config: IWailaConfigHandler?): ItemStack
        = getSixData(accessor)?.itemStack ?: ItemStack.EMPTY

    override fun getWailaTail(itemStack: ItemStack?, currenttip: MutableList<String>, accessor: IWailaDataAccessor?,
                              config: IWailaConfigHandler?): MutableList<String> = currenttip

    override fun getNBTData(player: ServerPlayer?, te: BlockEntity?, tag: CompoundTag, world: Level?,
                            pos: BlockPos?): CompoundTag = tag

    override fun getWailaHead(itemStack: ItemStack?, currenttip: MutableList<String>, accessor: IWailaDataAccessor,
                              config: IWailaConfigHandler?): MutableList<String> = if (!itemStack.isNothing()) {
        mutableListOf("${SpecialChars.WHITE}${itemStack.hoverName}")
    } else {
        currenttip
    }
}
