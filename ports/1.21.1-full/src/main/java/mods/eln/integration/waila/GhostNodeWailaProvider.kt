package mods.eln.integration.waila

import com.google.common.cache.CacheLoader
import mcp.mobius.waila.api.IWailaConfigHandler
import mcp.mobius.waila.api.IWailaDataAccessor
import mcp.mobius.waila.api.IWailaDataProvider
import mcp.mobius.waila.api.SpecialChars
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.packets.GhostNodeWailaResponsePacket
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.ChatFormatting
import net.minecraft.world.level.Level
import net.minecraftforge.fml.common.Optional

@Optional.Interface(iface = "mcp.mobius.waila.api.IWailaDataProvider", modid = "Waila")
class GhostNodeWailaProvider(private val transparentNodeProvider: TransparentNodeWailaProvider,
                             private val sixNodeProvider: SixNodeWailaProvider) : IWailaDataProvider {
    private class WailaDataAccessorProxy(val accessor: IWailaDataAccessor, val coord: Coordinate,
                                         val side: Direction? = null) : IWailaDataAccessor {
        override fun getPlayer() = accessor.player
        override fun getStack() = accessor.stack
        override fun getPosition() = coord.pos

        override fun getSide() = if (side != null) side.toForge() else accessor.side
        override fun getPartialFrame() = accessor.partialFrame
        override fun getMetadata() = accessor.metadata
        override fun getRenderingPosition() = accessor.renderingPosition
        override fun getNBTData() = accessor.nbtData
        override fun getTileEntity() = accessor.tileEntity
        override fun getWorld() = coord.world()
        override fun getBlock() = accessor.block
        override fun getNBTInteger(tag: CompoundTag?, keyname: String?) = accessor.getNBTInteger(tag, keyname)
        override fun getBlockState(): BlockState {
            return coord.world().getBlockState(coord.pos)
        }
        override fun getMOP(): RayTraceResult {
            return accessor.mop
        }
    }

    override fun getNBTData(player: ServerPlayer?, te: BlockEntity?, tag: CompoundTag?, world: Level?, pos: BlockPos?): CompoundTag {
        return tag ?: CompoundTag()
    }

    private fun getGhostData(accessor: IWailaDataAccessor): GhostNodeWailaData? {
        val coord = Coordinate(accessor.position.x, accessor.position.y, accessor.position.z,
            accessor.world)
        var ghostData: GhostNodeWailaData? = null
        try {
            ghostData = WailaCache.ghostNodes.get(coord)
        } catch(e: CacheLoader.InvalidCacheLoadException) {
        }

        return ghostData
    }

    override fun getWailaBody(itemStack: ItemStack?, currentTip: MutableList<String>, accessor: IWailaDataAccessor,
                              config: IWailaConfigHandler?): MutableList<String> {
        val ghostData = getGhostData(accessor)
        val realCoord = ghostData?.realCoord
        return if (ghostData != null && realCoord != null) {
            return when (ghostData.realType) {
                GhostNodeWailaResponsePacket.TRANSPARENT_BLOCK_TYPE ->
                    transparentNodeProvider.getWailaBody(itemStack, currentTip,
                        WailaDataAccessorProxy(accessor, realCoord), config)
                GhostNodeWailaResponsePacket.SIXNODE_TYPE ->
                    sixNodeProvider.getWailaBody(itemStack, currentTip,
                        WailaDataAccessorProxy(accessor, realCoord, ghostData.realSide), config)
                else -> currentTip
            }
        } else {
            currentTip
        }
    }

    override fun getWailaStack(accessor: IWailaDataAccessor, config: IWailaConfigHandler?): ItemStack =
        getGhostData(accessor)?.itemStack ?: ItemStack.EMPTY

    override fun getWailaTail(itemStack: ItemStack?, currenttip: MutableList<String>, accessor: IWailaDataAccessor,
                              config: IWailaConfigHandler?) = currenttip

    override fun getWailaHead(itemStack: ItemStack?, currenttip: MutableList<String>, accessor: IWailaDataAccessor,
                              config: IWailaConfigHandler?): MutableList<String> = if (itemStack != null) {
        mutableListOf("${TextFormatting.WHITE}${itemStack.displayName}")
    } else {
        currenttip
    }
}
