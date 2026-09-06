@file:Suppress("NAME_SHADOWING")
package mods.eln.node.transparent

import mods.eln.generic.DescriptorBlockItem
import mods.eln.generic.GenericItemBlockUsingDamage
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.InteractionResult
import net.minecraft.sounds.SoundSource
import java.util.function.Supplier
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction.Companion.fromIntMinecraftSide
import mods.eln.misc.Utils.sendMessage
import mods.eln.misc.Utils.nullCheck
import mods.eln.node.NodeBlock
import net.minecraft.world.level.block.Block
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.client.itemrender.IItemRenderer.ItemRenderType
import mods.eln.client.itemrender.IItemRenderer.ItemRendererHelper
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.Direction as EnumFacing
import net.minecraft.core.BlockPos
import mods.eln.client.gl.GL11
import mods.eln.misc.setBlock

/**
 * The transparent-node element family: one [Placer] per descriptor (the Flattening); the block
 * goes to the descriptor's spawn offset from the clicked cell, so vanilla's placement is replaced
 * rather than adapted (1.7.10's `placeBlockAt`).
 */
class TransparentNodeItem(b: Supplier<Block>) : GenericItemBlockUsingDamage<TransparentNodeDescriptor?>(b, "TransparentNodeItem"), IItemRenderer {

    class Placer(family: TransparentNodeItem, descriptor: TransparentNodeDescriptor, id: Int, block: Block, properties: Properties) :
        DescriptorBlockItem<TransparentNodeDescriptor?>(family, descriptor, id, block, properties) {
        override fun useOn(context: UseOnContext): InteractionResult {
            val player = context.player ?: return InteractionResult.PASS
            val place = BlockPlaceContext(context)
            if (!place.canPlace()) return InteractionResult.FAIL
            val stack = context.itemInHand
            if (!player.mayUseItemAt(place.clickedPos, context.clickedFace, stack)) return InteractionResult.FAIL
            if (context.level.isClientSide) return InteractionResult.SUCCESS
            if (!(family as TransparentNodeItem).placeBlockAt(stack, player, context.level, place.clickedPos, context.clickedFace)) return InteractionResult.FAIL
            val sound = context.level.getBlockState(place.clickedPos).getSoundType(context.level, place.clickedPos, player)
            context.level.playSound(null, place.clickedPos, sound.placeSound, SoundSource.BLOCKS, (sound.volume + 1.0f) / 2.0f, sound.pitch * 0.8f)
            stack.shrink(1)
            return InteractionResult.SUCCESS
        }
    }

    override fun newItem(id: Int, descriptor: TransparentNodeDescriptor?): Item =
        Placer(this, descriptor!!, id, block.get(), newProperties(descriptor))

    /** Server side: node first, then the block at the descriptor's spawn offset. */
    fun placeBlockAt(stack: ItemStack, player: Player, world: Level, pos: BlockPos, side: EnumFacing): Boolean {
        var x = pos.x
        var y = pos.y
        var z = pos.z
        if (world.isClientSide) return false
        val descriptor = getDescriptor(stack) ?: return false
        val direction = fromIntMinecraftSide(side.get3DDataValue())!!.inverse
        val front = descriptor.getFrontFromPlace(direction, player)
        val v = intArrayOf(descriptor.spawnDeltaX, descriptor.spawnDeltaY, descriptor.spawnDeltaZ)
        front!!.rotateFromXN(v)
        x += v[0]
        y += v[1]
        z += v[2]
        val coord = Coordinate(x, y, z, world)
        var error: String?
        if (descriptor.checkCanPlace(coord, front).also { error = it } != null) {
            sendMessage(player, error)
            return false
        }
        val ghostgroup = descriptor.getGhostGroupFront(front)
        ghostgroup?.plot(coord, coord, descriptor.ghostGroupUuid)
        val node = TransparentNode()
        node.onBlockPlacedBy(coord, front, player, stack)
        val block = this.block.get() as NodeBlock
        world.setBlock(x, y, z, block, node.blockMetadata, 0x03) //caca1.5.1
        block.onBlockPlacedBy(world, BlockPos(x, y, z), direction, player, node.blockMetadata)
        node.checkCanStay(true)
        return true
    }

    override fun handleRenderType(item: ItemStack, type: ItemRenderType): Boolean {
        val d = getDescriptor(item)
        return if (nullCheck(d)) false else d!!.handleRenderType(item, type)
    }

    override fun shouldUseRenderHelper(type: ItemRenderType, item: ItemStack,
                                       helper: ItemRendererHelper): Boolean {
        val descriptor = getDescriptor(item) ?: return false
        return descriptor.shouldUseRenderHelper(type, item, helper)
    }

    fun shouldUseRenderHelperEln(type: ItemRenderType?, item: ItemStack?, helper: ItemRendererHelper?): Boolean {
        val descriptor = getDescriptor(item) ?: return false
        return descriptor.shouldUseRenderHelperEln(type, item, helper)
    }

    override fun renderItem(type: ItemRenderType, item: ItemStack, vararg data: Any) {
        Minecraft.getInstance().profiler.push("TransparentNodeItem")
        if (shouldUseRenderHelperEln(type, item, null)) {
            when (type) {
                ItemRenderType.ENTITY -> GL11.glTranslatef(0.00f, 0.3f, 0.0f)
                ItemRenderType.EQUIPPED_FIRST_PERSON -> GL11.glTranslatef(0.50f, 1f, 0.5f)
                ItemRenderType.EQUIPPED -> GL11.glTranslatef(0.50f, 1f, 0.5f)
                ItemRenderType.FIRST_PERSON_MAP -> {
                }
                ItemRenderType.INVENTORY -> GL11.glRotatef(90f, 0f, 1f, 0f)
                else -> {
                }
            }
        }
        val descriptor = getDescriptor(item)
        if (descriptor != null) {
            descriptor.renderItem(type, item, *data)
        }
        Minecraft.getInstance().profiler.pop()
    }

}
