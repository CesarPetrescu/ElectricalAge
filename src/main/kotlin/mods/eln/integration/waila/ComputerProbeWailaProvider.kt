package mods.eln.integration.waila

import net.minecraftforge.fml.common.Optional
import mcp.mobius.waila.api.IWailaConfigHandler
import mcp.mobius.waila.api.IWailaDataAccessor
import mcp.mobius.waila.api.IWailaDataProvider
import mcp.mobius.waila.api.SpecialChars
import mods.eln.simplenode.computerprobe.ComputerProbeEntity
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

@Optional.Interface(iface = "mcp.mobius.waila.api.IWailaDataProvider", modid = "waila")
class ComputerProbeWailaProvider : IWailaDataProvider {
    override fun getWailaBody(
        itemStack: ItemStack?,
        currenttip: MutableList<String>,
        accessor: IWailaDataAccessor,
        config: IWailaConfigHandler?
    ): MutableList<String> {
        val nbt = accessor.nbtData
        val componentName = nbt.getString(TAG_COMPONENT_NAME)
        val address = nbt.getString(TAG_ADDRESS)

        if (componentName.isNotEmpty()) {
            currenttip.add("Component: ${SpecialChars.WHITE}$componentName")
        }
        if (address.isNotEmpty()) {
            currenttip.add("Address: ${SpecialChars.WHITE}$address")
        }

        return currenttip
    }

    override fun getWailaStack(accessor: IWailaDataAccessor?, config: IWailaConfigHandler?): ItemStack = ItemStack.EMPTY

    override fun getWailaTail(
        itemStack: ItemStack?,
        currenttip: MutableList<String>,
        accessor: IWailaDataAccessor?,
        config: IWailaConfigHandler?
    ): MutableList<String> = currenttip

    override fun getNBTData(
        player: ServerPlayer?,
        te: BlockEntity?,
        tag: CompoundTag,
        world: Level?,
        pos: BlockPos?
    ): CompoundTag {
        val probe = te as? ComputerProbeEntity ?: return tag
        tag.putString(TAG_COMPONENT_NAME, probe.getComponentName())
        probe.getOpenComputersAddress()?.let { tag.putString(TAG_ADDRESS, it) }
        return tag
    }

    override fun getWailaHead(
        itemStack: ItemStack?,
        currenttip: MutableList<String>,
        accessor: IWailaDataAccessor?,
        config: IWailaConfigHandler?
    ): MutableList<String> = currenttip

    companion object {
        private const val TAG_COMPONENT_NAME = "eln.ocComponentName"
        private const val TAG_ADDRESS = "eln.ocAddress"
    }
}
