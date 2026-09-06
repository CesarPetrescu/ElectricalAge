@file:Suppress("NAME_SHADOWING")
package mods.eln.node.six

import mods.eln.Eln
import mods.eln.generic.GenericItemBlockUsingDamage
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction.Companion.fromFacing
import mods.eln.misc.Direction.Companion.fromIntMinecraftSide
import mods.eln.misc.LRDU
import mods.eln.misc.Utils.sendMessage
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import net.minecraft.world.level.block.Block
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.InteractionResult
import net.minecraft.core.Direction as EnumFacing
import net.minecraft.world.InteractionHand
import net.minecraft.sounds.SoundSource
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.client.itemrender.IItemRenderer.ItemRenderType
import mods.eln.client.itemrender.IItemRenderer.ItemRendererHelper
import org.lwjgl.opengl.GL11
import mods.eln.misc.getBlock
import mods.eln.misc.getBlockMetadata
import mods.eln.misc.getTileEntity
import mods.eln.misc.setBlock
import mods.eln.misc.setBlockToAir
import mods.eln.misc.getBlockState
import mods.eln.misc.isReplaceable

class SixNodeItem(b: Block?) : GenericItemBlockUsingDamage<SixNodeDescriptor?>(b), IItemRenderer {
    private fun shouldConsumeUtilityCableLength(player: Player): Boolean {
        val creativeFreeLength = Eln.config.getBooleanOrElse("gameplay.cables.creativeFreeLength", true)
        return !(creativeFreeLength && player is ServerPlayer && mods.eln.misc.Utils.isCreative(player))
    }

    override fun getMetadata(damageValue: Int): Int {
        return damageValue
    }

    /**
     * Callback for item usage. If the item does something special on right clicking, he will have one of those. Return True if something happen and false if it don't. This is for ITEMS, not BLOCKS
     */
    override fun onItemUse(
        player: Player, world: Level, posIn: BlockPos, hand: InteractionHand,
        facing: EnumFacing, hitX: Float, hitY: Float, hitZ: Float
    ): InteractionResult = onItemUse(player.getItemInHand(hand), player, world, posIn, hand, facing, hitX, hitY, hitZ)

    /**
     * 1.7.10 passed the stack in; 1.12.2 reads it from the hand. Programmatic placement (the Falstad
     * importer) still needs to place a stack that is not in anyone's hand, so the body takes it explicitly.
     */
    fun onItemUse(
        stack: ItemStack, player: Player, world: Level, posIn: BlockPos, hand: InteractionHand,
        facing: EnumFacing, hitX: Float, hitY: Float, hitZ: Float
    ): InteractionResult {
        var pos = posIn
        var side = facing
        val state = world.getBlockState(pos)
        val block = state.block
        // A snow layer one deep is placed *into*, not on top of - the same special case vanilla
        // ItemBlock makes, and the reason this override exists rather than calling super.
        if (block === Blocks.SNOW_LAYER && block.getMetaFromState(state) and 0x7 < 1) {
            side = EnumFacing.UP
        } else if (block !== Blocks.VINE && block !== Blocks.TALLGRASS && block !== Blocks.DEADBUSH &&
            !block.isReplaceable(world, pos)
        ) {
            pos = pos.offset(facing)
        }
        if (stack.isEmpty) return InteractionResult.FAIL
        val descriptor = getDescriptor(stack)
        if (descriptor is UtilityCableDescriptor && !descriptor.hasLengthForPlacement(stack)) {
            sendMessage(player, "Not enough wire length remaining to place another segment")
            return InteractionResult.FAIL
        }
        if (!player.canPlayerEdit(pos, side, stack)) return InteractionResult.FAIL
        if (pos.y == 255 && this.block.defaultState.material.isSolid) return InteractionResult.FAIL
        val meta = getMetadata(stack.metadata)
        val newState = this.block.getStateForPlacement(world, pos, side, hitX, hitY, hitZ, meta, player, hand)
        if (placeBlockAt(stack, player, world, pos, side, hitX, hitY, hitZ, newState)) {
            val sound = this.block.getSoundType(newState, world, pos, player)
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
     * Returns true if the given ItemBlock can be placed on the given side of the given block position.
     */
    override fun canPlaceBlockOnSide(
        world: Level, pos: BlockPos, side: EnumFacing, player: Player, stack: ItemStack
    ): Boolean {
        if (!isStackValidToPlace(stack)) return false
        val vect = intArrayOf(pos.x, pos.y, pos.z)
        fromFacing(side).applyTo(vect, 1)
        val descriptor = getDescriptor(stack) ?: return false
        if (!descriptor.canBePlacedOnSide(player, Coordinate(pos.x, pos.y, pos.z, world), fromFacing(side).inverse)) {
            return false
        }
        // Stacking another face onto an existing six-node is always allowed, whatever vanilla
        // thinks about the block already being there.
        if (world.getBlockState(BlockPos(vect[0], vect[1], vect[2])).block === Eln.sixNodeBlock) return true
        return super.canPlaceBlockOnSide(world, pos, side, player, stack)
    }

    fun isStackValidToPlace(stack: ItemStack?): Boolean {
        val descriptor = getDescriptor(stack)
        return descriptor != null
    }

    override fun placeBlockAt(
        stack: ItemStack, player: Player, world: Level, pos: BlockPos,
        side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float, newState: BlockState
    ): Boolean {
        if (world.isClientSide) return false
        if (!isStackValidToPlace(stack)) return false
        val x = pos.x; val y = pos.y; val z = pos.z
        val metadata = this.block.getMetaFromState(newState)
        val direction = fromFacing(side).inverse
        val blockOld = world.getBlock(x, y, z)
        val block = Block.getBlockFromItem(this) as SixNodeBlock
        if (blockOld === Blocks.AIR || blockOld.isReplaceable(world, pos)) {
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
            val sixNode = (world.getBlockEntity(x, y, z) as SixNodeEntity).node as SixNode?
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
        Minecraft.getInstance().profiler.startSection("SixNodeItem")
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
        Minecraft.getInstance().profiler.endSection()
    }

    init {
        setHasSubtypes(true)
        translationKey = "SixNodeItem"
    }
}
