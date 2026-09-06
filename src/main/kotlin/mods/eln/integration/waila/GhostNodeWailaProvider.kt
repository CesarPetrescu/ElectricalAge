package mods.eln.integration.waila

import com.google.common.cache.CacheLoader
import net.minecraftforge.fml.common.Optional
import mcp.mobius.waila.api.IWailaConfigHandler
import mcp.mobius.waila.api.IWailaDataAccessor
import mcp.mobius.waila.api.IWailaDataProvider
import mcp.mobius.waila.api.SpecialChars
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.packets.GhostNodeWailaResponsePacket
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.core.Direction as EnumFacing
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.Level
import mods.eln.misc.getBlock
import mods.eln.misc.getBlockState
import mods.eln.misc.getTileEntity
import mods.eln.misc.isNothing

@Optional.Interface(iface = "mcp.mobius.waila.api.IWailaDataProvider", modid = "waila")
class GhostNodeWailaProvider(private val transparentNodeProvider: TransparentNodeWailaProvider,
                             private val sixNodeProvider: SixNodeWailaProvider) : IWailaDataProvider {
    /** Re-targets a Hwyla accessor at the real node behind a ghost block. */
    private class WailaDataAccessorProxy(val accessor: IWailaDataAccessor, val coord: Coordinate,
                                         val side: Direction? = null) : IWailaDataAccessor {
        override fun getLevel(): Level = accessor.level
        override fun getPlayer(): Player = accessor.player
        override fun getBlock(): Block = accessor.block
        override fun getMetadata() = accessor.metadata
        override fun getBlockState(): BlockState = accessor.blockState
        override fun getTileEntity(): BlockEntity? = accessor.tileEntity
        override fun getMOP(): HitResult = HitResult(accessor.mop.hitVec, accessor.mop.sideHit, coord.pos)
        override fun getPosition(): BlockPos = coord.pos
        override fun getRenderingPosition(): Vec3 = accessor.renderingPosition!!
        override fun getNBTData(): CompoundTag = accessor.nbtData
        override fun getNBTInteger(tag: CompoundTag?, keyname: String?) = accessor.getNBTInteger(tag, keyname)
        override fun getPartialFrame() = accessor.partialFrame
        override fun getSide(): EnumFacing = if (side != null) side.toFacing() else accessor.side
        override fun getStack(): ItemStack = accessor.stack
    }

    private fun getGhostData(accessor: IWailaDataAccessor): GhostNodeWailaData? {
        val coord = Coordinate(accessor.position.x, accessor.position.y, accessor.position.z,
            accessor.level)
        var ghostData: GhostNodeWailaData? = null
        try {
            ghostData = WailaCache.ghostNodes.get(coord)
        } catch(e: CacheLoader.InvalidCacheLoadException) {
        }

        return ghostData
    }

    override fun getWailaBody(itemStack: ItemStack?, currenttip: MutableList<String>, accessor: IWailaDataAccessor,
                              config: IWailaConfigHandler?): MutableList<String> {
        val ghostData = getGhostData(accessor)
        val realCoord = ghostData?.realCoord
        return if (ghostData != null && realCoord != null) {
            return when (ghostData.realType) {
                GhostNodeWailaResponsePacket.TRANSPARENT_BLOCK_TYPE ->
                    transparentNodeProvider.getWailaBody(itemStack, currenttip,
                        WailaDataAccessorProxy(accessor, realCoord), config)
                GhostNodeWailaResponsePacket.SIXNODE_TYPE ->
                    sixNodeProvider.getWailaBody(itemStack, currenttip,
                        WailaDataAccessorProxy(accessor, realCoord, ghostData.realSide), config)
                else -> currenttip
            }
        } else {
            currenttip
        }
    }

    override fun getWailaStack(accessor: IWailaDataAccessor, config: IWailaConfigHandler?): ItemStack =
        getGhostData(accessor)?.itemStack ?: ItemStack.EMPTY

    override fun getWailaTail(itemStack: ItemStack?, currenttip: MutableList<String>, accessor: IWailaDataAccessor,
                              config: IWailaConfigHandler?) = currenttip

    override fun getNBTData(player: ServerPlayer?, te: BlockEntity?, tag: CompoundTag,
                            world: Level?, pos: BlockPos?): CompoundTag = tag

    override fun getWailaHead(itemStack: ItemStack?, currenttip: MutableList<String>, accessor: IWailaDataAccessor,
                              config: IWailaConfigHandler?): MutableList<String> = if (!itemStack.isNothing()) {
        mutableListOf("${SpecialChars.WHITE}${itemStack.hoverName}")
    } else {
        currenttip
    }
}
