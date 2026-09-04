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
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
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
        override fun getWorld(): World = accessor.world
        override fun getPlayer(): EntityPlayer = accessor.player
        override fun getBlock(): Block = accessor.block
        override fun getMetadata() = accessor.metadata
        override fun getBlockState(): IBlockState = accessor.blockState
        override fun getTileEntity(): TileEntity? = accessor.tileEntity
        override fun getMOP(): RayTraceResult = RayTraceResult(accessor.mop.hitVec, accessor.mop.sideHit, coord.pos)
        override fun getPosition(): BlockPos = coord.pos
        override fun getRenderingPosition(): Vec3d = accessor.renderingPosition!!
        override fun getNBTData(): NBTTagCompound = accessor.nbtData
        override fun getNBTInteger(tag: NBTTagCompound?, keyname: String?) = accessor.getNBTInteger(tag, keyname)
        override fun getPartialFrame() = accessor.partialFrame
        override fun getSide(): EnumFacing = if (side != null) side.toFacing() else accessor.side
        override fun getStack(): ItemStack = accessor.stack
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

    override fun getNBTData(player: EntityPlayerMP?, te: TileEntity?, tag: NBTTagCompound,
                            world: World?, pos: BlockPos?): NBTTagCompound = tag

    override fun getWailaHead(itemStack: ItemStack?, currenttip: MutableList<String>, accessor: IWailaDataAccessor,
                              config: IWailaConfigHandler?): MutableList<String> = if (!itemStack.isNothing()) {
        mutableListOf("${SpecialChars.WHITE}${itemStack.displayName}")
    } else {
        currenttip
    }
}
