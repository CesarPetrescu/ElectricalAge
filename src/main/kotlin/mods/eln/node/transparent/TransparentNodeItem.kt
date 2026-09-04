@file:Suppress("NAME_SHADOWING")
package mods.eln.node.transparent

import mods.eln.generic.GenericItemBlockUsingDamage
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction.Companion.fromIntMinecraftSide
import mods.eln.misc.Utils.sendMessage
import mods.eln.misc.Utils.nullCheck
import mods.eln.node.NodeBlock
import net.minecraft.block.Block
import net.minecraft.client.Minecraft
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.client.itemrender.IItemRenderer.ItemRenderType
import mods.eln.client.itemrender.IItemRenderer.ItemRendererHelper
import net.minecraft.block.state.IBlockState
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import org.lwjgl.opengl.GL11
import mods.eln.misc.setBlock

class TransparentNodeItem(b: Block?) : GenericItemBlockUsingDamage<TransparentNodeDescriptor?>(b), IItemRenderer {
    override fun placeBlockAt(
        stack: ItemStack, player: EntityPlayer, world: World, pos: BlockPos,
        side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float, newState: IBlockState
    ): Boolean {
        var x = pos.x
        var y = pos.y
        var z = pos.z
        val metadata = this.block.getMetaFromState(newState)
        if (world.isRemote) return false
        val descriptor = getDescriptor(stack)
        val direction = fromIntMinecraftSide(side.index)!!.inverse
        val front = descriptor!!.getFrontFromPlace(direction, player)
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
        world.setBlock(x, y, z, Block.getBlockFromItem(this), node.blockMetadata, 0x03) //caca1.5.1
        (Block.getBlockFromItem(this) as NodeBlock).onBlockPlacedBy(world, BlockPos(x, y, z), direction, player, metadata)
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
        Minecraft.getMinecraft().profiler.startSection("TransparentNodeItem")
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
        Minecraft.getMinecraft().profiler.endSection()
    }

    init {
        setHasSubtypes(true)
        translationKey = "TransparentNodeItem"
    }
}
