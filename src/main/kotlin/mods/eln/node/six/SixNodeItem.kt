@file:Suppress("NAME_SHADOWING")
package mods.eln.node.six

import mods.eln.Eln
import mods.eln.generic.DescriptorBlockItem
import mods.eln.generic.GenericItemBlockUsingDamage
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction.Companion.fromFacing
import mods.eln.misc.LRDU
import mods.eln.misc.Utils.sendMessage
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import net.minecraft.world.level.block.Block
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.InteractionResult
import net.minecraft.core.Direction as EnumFacing
import net.minecraft.world.InteractionHand
import net.minecraft.sounds.SoundSource
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.client.itemrender.IItemRenderer.ItemRenderType
import mods.eln.client.itemrender.IItemRenderer.ItemRendererHelper
import mods.eln.client.gl.GL11
import mods.eln.misc.getBlock
import mods.eln.misc.getBlockEntity
import mods.eln.misc.setBlock
import mods.eln.misc.setBlockToAir
import mods.eln.misc.getBlockState
import mods.eln.misc.isReplaceable
import java.util.function.Supplier

/**
 * The six-node element family. One [Placer] item is registered per descriptor (the Flattening);
 * the placement logic is the 1.7.10 `ItemBlock.onItemUse` flow kept here so the Falstad importer
 * and the smoke test can place a stack that is in nobody's hand.
 */
class SixNodeItem(b: Supplier<Block>) : GenericItemBlockUsingDamage<SixNodeDescriptor?>(b, "SixNodeItem"), IItemRenderer {

    /** The registered item of one descriptor: vanilla's use hook, routed through the family. */
    class Placer(family: SixNodeItem, descriptor: SixNodeDescriptor, id: Int, block: Block, properties: Properties) :
        DescriptorBlockItem<SixNodeDescriptor?>(family, descriptor, id, block, properties) {
        override fun useOn(context: UseOnContext): InteractionResult {
            val player = context.player ?: return InteractionResult.PASS
            // BlockPlaceContext resolves the target cell the way 1.7.10 did: into the clicked block
            // when it is replaceable (snow layers, grass, vines), else the neighbour on the clicked face.
            val place = BlockPlaceContext(context)
            val hit = context.clickLocation
            val pos = context.clickedPos
            return (family as SixNodeItem).onItemUse(context.itemInHand, player, context.level, place.clickedPos, context.hand,
                context.clickedFace, (hit.x - pos.x).toFloat(), (hit.y - pos.y).toFloat(), (hit.z - pos.z).toFloat())
        }
    }

    override fun newItem(id: Int, descriptor: SixNodeDescriptor?): Item =
        Placer(this, descriptor!!, id, block.get(), newProperties(descriptor))

    private fun shouldConsumeUtilityCableLength(player: Player): Boolean {
        val creativeFreeLength = Eln.config.getBooleanOrElse("gameplay.cables.creativeFreeLength", true)
        return !(creativeFreeLength && player is ServerPlayer && mods.eln.misc.Utils.isCreative(player))
    }

    /**
     * 1.7.10's `ItemBlock.onItemUse` with the stack passed in explicitly. [pos] is the cell the
     * block goes into (already offset from the clicked block when that is not replaceable).
     */
    fun onItemUse(
        stack: ItemStack, player: Player, world: Level, pos: BlockPos, @Suppress("UNUSED_PARAMETER") hand: InteractionHand,
        side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float
    ): InteractionResult {
        if (stack.isEmpty) return InteractionResult.FAIL
        val descriptor = getDescriptor(stack)
        if (descriptor is UtilityCableDescriptor && !descriptor.hasLengthForPlacement(stack)) {
            sendMessage(player, "Not enough wire length remaining to place another segment")
            return InteractionResult.FAIL
        }
        if (!player.mayUseItemAt(pos, side, stack)) return InteractionResult.FAIL
        if (!canPlaceBlockOnSide(world, pos.relative(side.opposite), side, player, stack)) return InteractionResult.FAIL
        if (world.isClientSide) return InteractionResult.SUCCESS
        if (placeBlockAt(stack, player, world, pos, side, hitX, hitY, hitZ)) {
            val newState = world.getBlockState(pos)
            val sound = newState.getSoundType(world, pos, player)
            world.playSound(
                null, pos, sound.placeSound, SoundSource.BLOCKS,
                (sound.volume + 1.0f) / 2.0f, sound.pitch * 0.8f
            )
            if (descriptor is UtilityCableDescriptor) {
                if (shouldConsumeUtilityCableLength(player)) {
                    descriptor.consumeLengthForPlacement(stack)
                    if (!descriptor.hasLengthForPlacement(stack)) {
                        stack.shrink(1)
                    }
                }
            } else {
                stack.shrink(1)
            }
        }
        return InteractionResult.SUCCESS
    }

    /**
     * Whether the stack can be placed on [side] of the block at [pos] (1.7.10's
     * `canPlaceBlockOnSide`, called with the clicked block).
     */
    fun canPlaceBlockOnSide(world: Level, pos: BlockPos, side: EnumFacing, player: Player, stack: ItemStack): Boolean {
        if (!isStackValidToPlace(stack)) return false
        val vect = intArrayOf(pos.x, pos.y, pos.z)
        fromFacing(side).applyTo(vect, 1)
        val descriptor = getDescriptor(stack) ?: return false
        val supportState = world.getBlockState(pos)
        val support = supportState.block as? mods.eln.generic.SignalWireSupport
        if (support != null && (!support.acceptsSignalWire(supportState, side) ||
            descriptor !is mods.eln.sixnode.electricalcable.ElectricalCableDescriptor || !descriptor.signalWire)) return false
        if (!descriptor.canBePlacedOnSide(player, Coordinate(pos.x, pos.y, pos.z, world), fromFacing(side).inverse)) {
            return false
        }
        // Stacking another face onto an existing six-node is always allowed, whatever vanilla
        // thinks about the block already being there.
        val target = BlockPos(vect[0], vect[1], vect[2])
        if (world.getBlockState(target).block === Eln.sixNodeBlock) return true
        return world.getBlockState(target).canBeReplaced()
    }

    fun isStackValidToPlace(stack: ItemStack?): Boolean {
        val descriptor = getDescriptor(stack)
        return descriptor != null
    }

    /** Server side: creates the node, then the block (1.7.10's `placeBlockAt`). */
    fun placeBlockAt(stack: ItemStack, player: Player, world: Level, pos: BlockPos, side: EnumFacing, @Suppress("UNUSED_PARAMETER") hitX: Float, @Suppress("UNUSED_PARAMETER") hitY: Float, @Suppress("UNUSED_PARAMETER") hitZ: Float): Boolean {
        if (world.isClientSide) return false
        if (!isStackValidToPlace(stack)) return false
        val x = pos.x; val y = pos.y; val z = pos.z
        val metadata = 0
        val direction = fromFacing(side).inverse
        val blockOld = world.getBlock(x, y, z)
        val block = this.block.get() as SixNodeBlock
        if (blockOld === Blocks.AIR || blockOld.isReplaceable(world, x, y, z)) {
            val coord = Coordinate(x, y, z, world)
            val descriptor = getDescriptor(stack)
            var error: String?
            if (descriptor!!.checkCanPlace(coord, direction, LRDU.Up).also { error = it } != null) {
                sendMessage(player, error)
                return false
            }
            if (block.getIfOtherBlockIsSolid(world, x, y, z, direction)) {
                val ghostgroup = descriptor.getGhostGroup(direction, LRDU.Up)
                ghostgroup?.plot(coord, coord, descriptor.ghostGroupUuid)
                val sixNode = SixNode()
                sixNode.onBlockPlacedBy(Coordinate(x, y, z, world), direction, player, stack)
                sixNode.createSubBlock(stack, direction, player)
                world.setBlock(x, y, z, block, metadata, 0x03)
                block.getIfOtherBlockIsSolid(world, x, y, z, direction)
                block.onBlockPlacedBy(world, pos, fromFacing(side).inverse, player, metadata)
                return true
            }
        } else if (blockOld === block) {
            val sixNode = (world.getBlockEntity(x, y, z) as SixNodeEntity).node as SixNode?
            if (sixNode == null) {
                world.setBlockToAir(x, y, z)
                return false
            }
            if (!sixNode.getSideEnable(direction) && block.getIfOtherBlockIsSolid(world, x, y, z, direction)) {
                sixNode.createSubBlock(stack, direction, player)
                block.onBlockPlacedBy(world, pos, fromFacing(side).inverse, player, metadata)
                return true
            }
        } else {
            val sixNode = (world.getBlockEntity(x, y, z) as? SixNodeEntity)?.node as SixNode?
            if (sixNode == null) {
                world.setBlockToAir(x, y, z)
                return false
            }
        }
        return false
    }

    override fun handleRenderType(item: ItemStack, type: ItemRenderType): Boolean {
        val descriptor = getDescriptor(item) ?: return false
        return descriptor.handleRenderType(item, type)
    }

    override fun shouldUseRenderHelper(type: ItemRenderType, item: ItemStack, helper: ItemRendererHelper): Boolean {
        if (!isStackValidToPlace(item)) return false
        val descriptor = getDescriptor(item) ?: return false
        return descriptor.shouldUseRenderHelper(type, item, helper)
    }

    fun shouldUseRenderHelperEln(type: ItemRenderType?, item: ItemStack?, helper: ItemRendererHelper?): Boolean {
        if (!isStackValidToPlace(item)) return false
        val descriptor = getDescriptor(item) ?: return false
        return descriptor.shouldUseRenderHelperEln(type, item, helper)
    }

    override fun renderItem(type: ItemRenderType, item: ItemStack, vararg data: Any) {
        if (!isStackValidToPlace(item)) return
        Minecraft.getInstance().profiler.push("SixNodeItem")
        if (shouldUseRenderHelperEln(type, item, null)) {
            when (type) {
                ItemRenderType.ENTITY -> GL11.glRotatef(90f, 0f, 0f, 1f)
                ItemRenderType.EQUIPPED_FIRST_PERSON -> {
                    GL11.glRotatef(160f, 0f, 1f, 0f)
                    GL11.glTranslatef(-0.70f, 1f, -0.7f)
                    GL11.glScalef(1.8f, 1.8f, 1.8f)
                    GL11.glRotatef(-90f, 1f, 0f, 0f)
                }
                ItemRenderType.EQUIPPED -> {
                    GL11.glRotatef(180f, 0f, 1f, 0f)
                    GL11.glTranslatef(-0.70f, 1f, -0.7f)
                    GL11.glScalef(1.5f, 1.5f, 1.5f)
                }
                ItemRenderType.FIRST_PERSON_MAP -> {
                }
                ItemRenderType.INVENTORY -> {
                    GL11.glRotatef(-90f, 0f, 1f, 0f)
                    GL11.glRotatef(-90f, 1f, 0f, 0f)
                }
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
