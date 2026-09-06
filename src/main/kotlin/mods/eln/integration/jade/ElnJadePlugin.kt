package mods.eln.integration.jade

import com.google.common.cache.CacheLoader
import mods.eln.Eln
import mods.eln.ghost.GhostBlock
import mods.eln.integration.waila.GhostNodeWailaData
import mods.eln.integration.waila.SixNodeCoordonate
import mods.eln.integration.waila.SixNodeWailaData
import mods.eln.integration.waila.TransparentNodeWailaEntry
import mods.eln.integration.waila.WailaCache
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.node.six.SixNodeBlock
import mods.eln.node.transparent.TransparentNodeBlock
import mods.eln.packets.GhostNodeWailaResponsePacket
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import snownee.jade.api.BlockAccessor
import snownee.jade.api.IBlockComponentProvider
import snownee.jade.api.ITooltip
import snownee.jade.api.IWailaClientRegistration
import snownee.jade.api.IWailaPlugin
import snownee.jade.api.JadeIds
import snownee.jade.api.WailaPlugin
import snownee.jade.api.config.IPluginConfig
import snownee.jade.api.ui.IElement
import snownee.jade.api.ui.IElementHelper

/**
 * The hover overlay, on Jade (Waila's 1.21 successor). The data still travels the mod's own way:
 * looking at a node asks the server through [WailaCache]'s request packets and the answers land
 * in the cache, so these providers only read it - what the Hwyla providers of 1.12 did. Jade is
 * optional: this class is only loaded when Jade found the `@WailaPlugin` annotation.
 */
@WailaPlugin
class ElnJadePlugin : IWailaPlugin {
    override fun registerClient(registration: IWailaClientRegistration) {
        registration.registerBlockComponent(TransparentNodeProvider, TransparentNodeBlock::class.java)
        registration.registerBlockComponent(SixNodeProvider, SixNodeBlock::class.java)
        registration.registerBlockIcon(SixNodeProvider, SixNodeBlock::class.java)
        registration.registerBlockComponent(GhostNodeProvider, GhostBlock::class.java)
        registration.registerBlockIcon(GhostNodeProvider, GhostBlock::class.java)
    }
}

private fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(Eln.MODID, path)

private fun white(text: String): Component = Component.literal(text).withStyle(ChatFormatting.WHITE)

private fun ITooltip.entries(entries: List<TransparentNodeWailaEntry>) {
    for (entry in entries) {
        if (entry.values.size == 1) {
            add(Component.literal("${entry.label}: ").append(white(entry.values.single())))
        } else {
            add(Component.literal("${entry.label}:"))
            entry.values.forEach { add(white(it)) }
        }
    }
}

/** A cache miss throws (the loader returns null while the request is in flight); that is "nothing yet". */
private inline fun <T> cached(block: () -> T?): T? = try {
    block()
} catch (e: CacheLoader.InvalidCacheLoadException) {
    null
}

object TransparentNodeProvider : IBlockComponentProvider {
    override fun getUid(): ResourceLocation = id("transparent_node")

    fun append(tooltip: ITooltip, coord: Coordinate) {
        cached { WailaCache.nodes.get(coord) }?.let { tooltip.entries(it) }
    }

    override fun appendTooltip(tooltip: ITooltip, accessor: BlockAccessor, config: IPluginConfig) {
        append(tooltip, Coordinate(accessor.position.x, accessor.position.y, accessor.position.z, accessor.level))
    }
}

object SixNodeProvider : IBlockComponentProvider {
    override fun getUid(): ResourceLocation = id("six_node")

    private fun data(coord: Coordinate, side: Direction): SixNodeWailaData? =
        cached { WailaCache.sixNodes.get(SixNodeCoordonate(coord, side)) }

    /** The element under the cursor: the six-node's slab that was hit, as the block itself reads a click. */
    private fun sideOf(accessor: BlockAccessor): Direction {
        val block = accessor.block as? SixNodeBlock ?: return Direction.from(accessor.side)
        val hit = accessor.hitResult.location
        val pos = accessor.position
        val entity = accessor.blockEntity as? mods.eln.node.six.SixNodeEntity
        return block.elementSide(Direction.from(accessor.side),
            (hit.x - pos.x).toFloat(), (hit.y - pos.y).toFloat(), (hit.z - pos.z).toFloat()
        ) { d -> entity?.getSyncronizedSideEnable(d) ?: false }
    }

    fun append(tooltip: ITooltip, coord: Coordinate, side: Direction) {
        val data = data(coord, side) ?: return
        data.itemStack?.let { if (!it.isEmpty) tooltip.replace(JadeIds.CORE_OBJECT_NAME, white(it.hoverName.string)) }
        data.data.forEach { (key, value) -> tooltip.add(Component.literal("$key: ").append(white(value))) }
    }

    fun icon(coord: Coordinate, side: Direction, current: IElement): IElement {
        val stack = data(coord, side)?.itemStack ?: return current
        return if (stack.isEmpty) current else IElementHelper.get().item(stack)
    }

    override fun appendTooltip(tooltip: ITooltip, accessor: BlockAccessor, config: IPluginConfig) {
        append(tooltip, Coordinate(accessor.position.x, accessor.position.y, accessor.position.z, accessor.level), sideOf(accessor))
    }

    override fun getIcon(accessor: BlockAccessor, config: IPluginConfig, currentIcon: IElement): IElement =
        icon(Coordinate(accessor.position.x, accessor.position.y, accessor.position.z, accessor.level), sideOf(accessor), currentIcon)
}

/** A ghost block answers for the machine it belongs to: the overlay shows that node. */
object GhostNodeProvider : IBlockComponentProvider {
    override fun getUid(): ResourceLocation = id("ghost_node")

    private fun data(accessor: BlockAccessor): GhostNodeWailaData? =
        cached { WailaCache.ghostNodes.get(Coordinate(accessor.position.x, accessor.position.y, accessor.position.z, accessor.level)) }

    override fun appendTooltip(tooltip: ITooltip, accessor: BlockAccessor, config: IPluginConfig) {
        val ghost = data(accessor) ?: return
        val real = ghost.realCoord ?: return
        ghost.itemStack?.let { if (!it.isEmpty) tooltip.replace(JadeIds.CORE_OBJECT_NAME, white(it.hoverName.string)) }
        when (ghost.realType) {
            GhostNodeWailaResponsePacket.TRANSPARENT_BLOCK_TYPE -> TransparentNodeProvider.append(tooltip, real)
            GhostNodeWailaResponsePacket.SIXNODE_TYPE -> SixNodeProvider.append(tooltip, real, ghost.realSide)
        }
    }

    override fun getIcon(accessor: BlockAccessor, config: IPluginConfig, currentIcon: IElement): IElement {
        val stack: ItemStack = data(accessor)?.itemStack ?: return currentIcon
        return if (stack.isEmpty) currentIcon else IElementHelper.get().item(stack)
    }
}
