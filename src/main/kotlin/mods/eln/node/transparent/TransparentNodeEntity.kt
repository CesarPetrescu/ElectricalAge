package mods.eln.node.transparent

import mods.eln.Eln
import mods.eln.cable.CableRenderDescriptor
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.FakeSideInventory.Companion.instance
import mods.eln.misc.LRDU
import mods.eln.node.NodeBlockEntity
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.core.Direction as EnumFacing
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import mods.eln.misc.xCoord
import mods.eln.misc.yCoord
import mods.eln.misc.zCoord
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import java.util.function.Supplier

open class TransparentNodeEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) : NodeBlockEntity(type, pos, state), WorldlyContainer {
    constructor(pos: BlockPos, state: BlockState) : this(TYPE.get(), pos, state)

    companion object {
        /** Registered by Eln through ElnRegistry.registerBlockEntity. */
        @JvmField
        var TYPE: Supplier<BlockEntityType<TransparentNodeEntity>> = Supplier { throw IllegalStateException("TransparentNodeEntity type not registered") }
    }

    var elementRender: TransparentNodeElementRender? = null
    var elementRenderId: Short = 0

    override fun getCableRender(side: Direction, lrdu: LRDU): CableRenderDescriptor? {
        return if (elementRender == null) null else elementRender!!.getCableRenderSide(side, lrdu)
    }

    override fun serverPublishUnserialize(stream: DataInputStream) {
        super.serverPublishUnserialize(stream)
        try {
            val id = stream.readShort()
            if (id.toInt() == 0) {
                elementRenderId = 0.toShort()
                elementRender = null
            } else {
                if (id != elementRenderId) {
                    elementRenderId = id
                    val descriptor = Eln.transparentNodeItem.getDescriptor(id.toInt())
                    elementRender = descriptor!!.RenderClass.getConstructor(TransparentNodeEntity::class.java, TransparentNodeDescriptor::class.java).newInstance(this, descriptor) as TransparentNodeElementRender
                }
                elementRender!!.networkUnserialize(stream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: InstantiationException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun newContainer(side: Direction, player: Player): AbstractContainerMenu? {
        val n = node as TransparentNode? ?: return null
        return n.newContainer(side, player)
    }

    override fun newGuiDraw(side: Direction, player: Player): Screen? {
        return elementRender!!.newGuiDraw(side, player)
    }

    override fun preparePacketForServer(stream: DataOutputStream) {
        try {
            super.preparePacketForServer(stream)
            stream.writeShort(elementRenderId.toInt())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun sendPacketToServer(bos: ByteArrayOutputStream?) {
        super.sendPacketToServer(bos)
    }

    override fun cameraDrawOptimisation(): Boolean {
        return if (elementRender == null) super.cameraDrawOptimisation() else elementRender!!.cameraDrawOptimisation()
    }

    override fun unoptimizedRenderBoundingBox(): AABB {
        return if (elementRender == null) super.unoptimizedRenderBoundingBox() else elementRender!!.unoptimizedRenderBoundingBox()
    }

    @Suppress("UNUSED_PARAMETER") fun getDamageValue(world: Level, x: Int, y: Int, z: Int): Int {
        return if (world.isClientSide) {
            elementRenderId.toInt()
        } else 0
    }

    override fun tileEntityNeighborSpawn() {
        if (elementRender != null) elementRender!!.notifyNeighborSpawn()
    }

    fun addCollisionBoxesToList(par5AxisAlignedBB: AABB, list: MutableList<AABB?>, blockCoord: Coordinate?) {
        val desc = if (world.isClientSide) {
            if (elementRender == null) null else elementRender!!.transparentNodeDescriptor
        } else {
            val node = node as TransparentNode?
            if (node == null) null else node.element!!.transparentNodeDescriptor
        }
        val x: Int
        val y: Int
        val z: Int
        if (blockCoord != null) {
            x = blockCoord.x
            y = blockCoord.y
            z = blockCoord.z
        } else {
            x = xCoord
            y = yCoord
            z = zCoord
        }
        if (desc == null) {
            val pos = BlockPos(x, y, z)
            val bb = AABB(pos)
            if (par5AxisAlignedBB.intersects(bb)) list.add(bb)
        } else {
            desc.addCollisionBoxesToList(par5AxisAlignedBB, list, world, x, y, z)
        }
    }

    override fun serverPacketUnserialize(stream: DataInputStream) {
        super.serverPacketUnserialize(stream)
        if (elementRender != null) elementRender!!.serverPacketUnserialize(stream)
    }

    override val nodeUuid: String
        get() = Eln.transparentNodeBlock.nodeUuid

    override fun destructor() {
        if (elementRender != null) elementRender!!.destructor()
        super.destructor()
    }

    override fun clientRefresh(deltaT: Float) {
        if (elementRender != null) {
            elementRender!!.refresh(deltaT)
        }
    }

    override fun isProvidingWeakPower(side: Direction?): Int {
        return 0
    }

    open val sidedInventory: WorldlyContainer
        get() {
            if (world.isClientSide) {
                if (elementRender == null) return instance
                val i = elementRender!!.inventory
                if (i != null && i is WorldlyContainer) {
                    return i
                }
            } else {
                val node = node
                if (node != null && node is TransparentNode) {
                    val i = node.getInventory(null)
                    if (i != null && i is WorldlyContainer) {
                        return i
                    }
                }
            }
            return instance
        }

    override fun getContainerSize(): Int {
        return sidedInventory.containerSize
    }

    override fun getItem(var1: Int): ItemStack {
        return sidedInventory.getItem(var1)
    }

    override fun isEmpty(): Boolean = sidedInventory.isEmpty

    override fun removeItem(var1: Int, var2: Int): ItemStack {
        return sidedInventory.removeItem(var1, var2)
    }

    override fun removeItemNoUpdate(var1: Int): ItemStack {
        return sidedInventory.removeItemNoUpdate(var1)
    }

    override fun setItem(var1: Int, var2: ItemStack) {
        sidedInventory.setItem(var1, var2)
    }

    override fun clearContent() = sidedInventory.clearContent()

    override fun getMaxStackSize(): Int {
        return sidedInventory.maxStackSize
    }

    override fun stillValid(var1: Player): Boolean {
        return sidedInventory.stillValid(var1)
    }

    override fun startOpen(player: Player) {
        sidedInventory.startOpen(player)
    }

    override fun stopOpen(player: Player) {
        sidedInventory.stopOpen(player)
    }

    override fun canPlaceItem(var1: Int, var2: ItemStack): Boolean {
        return sidedInventory.canPlaceItem(var1, var2)
    }

    override fun getSlotsForFace(side: EnumFacing): IntArray =
        sidedInventory.getSlotsForFace(side)

    override fun canPlaceItemThroughFace(slot: Int, stack: ItemStack, side: EnumFacing?): Boolean =
        sidedInventory.canPlaceItemThroughFace(slot, stack, side)

    override fun canTakeItemThroughFace(slot: Int, stack: ItemStack, side: EnumFacing): Boolean =
        sidedInventory.canTakeItemThroughFace(slot, stack, side)
}
