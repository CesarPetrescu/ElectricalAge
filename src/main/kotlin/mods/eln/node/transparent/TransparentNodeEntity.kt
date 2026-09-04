package mods.eln.node.transparent

import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import mods.eln.Eln
import mods.eln.cable.CableRenderDescriptor
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.FakeSideInventory.Companion.instance
import mods.eln.misc.LRDU
import mods.eln.node.NodeBlockEntity
import net.minecraft.client.gui.GuiScreen
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.inventory.Container
import net.minecraft.inventory.ISidedInventory
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import mods.eln.misc.xCoord
import mods.eln.misc.yCoord
import mods.eln.misc.zCoord

open class TransparentNodeEntity : NodeBlockEntity(), ISidedInventory {
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

    override fun newContainer(side: Direction, player: EntityPlayer): Container? {
        val n = node as TransparentNode? ?: return null
        return n.newContainer(side, player)
    }

    override fun newGuiDraw(side: Direction, player: EntityPlayer): GuiScreen? {
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

    @SideOnly(Side.CLIENT)
    override fun unoptimizedRenderBoundingBox(): AxisAlignedBB {
        return if (elementRender == null) super.unoptimizedRenderBoundingBox() else elementRender!!.unoptimizedRenderBoundingBox()
    }

    @Suppress("UNUSED_PARAMETER") fun getDamageValue(world: World, x: Int, y: Int, z: Int): Int {
        return if (world.isRemote) {
            elementRenderId.toInt()
        } else 0
    }

    override fun tileEntityNeighborSpawn() {
        if (elementRender != null) elementRender!!.notifyNeighborSpawn()
    }

    fun addCollisionBoxesToList(par5AxisAlignedBB: AxisAlignedBB, list: MutableList<AxisAlignedBB?>, blockCoord: Coordinate?) {
        val desc = if (world.isRemote) {
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
            val bb = Blocks.STONE.defaultState.getCollisionBoundingBox(world, pos)?.offset(pos)
            if (bb != null && par5AxisAlignedBB.intersects(bb)) list.add(bb)
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

    open val sidedInventory: ISidedInventory
        get() {
            if (world.isRemote) {
                if (elementRender == null) return instance
                val i = elementRender!!.inventory
                if (i != null && i is ISidedInventory) {
                    return i
                }
            } else {
                val node = node
                if (node != null && node is TransparentNode) {
                    val i = node.getInventory(null)
                    if (i != null && i is ISidedInventory) {
                        return i
                    }
                }
            }
            return instance
        }

    override fun getSizeInventory(): Int {
        return sidedInventory.sizeInventory
    }

    override fun getStackInSlot(var1: Int): ItemStack {
        return sidedInventory.getStackInSlot(var1)
    }

    override fun isEmpty(): Boolean = sidedInventory.isEmpty

    override fun decrStackSize(var1: Int, var2: Int): ItemStack {
        return sidedInventory.decrStackSize(var1, var2)
    }

    override fun removeStackFromSlot(var1: Int): ItemStack {
        return sidedInventory.removeStackFromSlot(var1)
    }

    override fun setInventorySlotContents(var1: Int, var2: ItemStack) {
        sidedInventory.setInventorySlotContents(var1, var2)
    }

    override fun clear() = sidedInventory.clear()

    override fun getField(id: Int): Int = sidedInventory.getField(id)
    override fun setField(id: Int, value: Int) = sidedInventory.setField(id, value)
    override fun getFieldCount(): Int = sidedInventory.fieldCount

    override fun getName(): String = sidedInventory.name

    override fun hasCustomName(): Boolean = sidedInventory.hasCustomName()

    override fun getDisplayName(): ITextComponent = sidedInventory.displayName

    override fun getInventoryStackLimit(): Int {
        return sidedInventory.inventoryStackLimit
    }

    override fun isUsableByPlayer(var1: EntityPlayer): Boolean {
        return sidedInventory.isUsableByPlayer(var1)
    }

    override fun openInventory(player: EntityPlayer) {
        sidedInventory.openInventory(player)
    }

    override fun closeInventory(player: EntityPlayer) {
        sidedInventory.closeInventory(player)
    }

    override fun isItemValidForSlot(var1: Int, var2: ItemStack): Boolean {
        return sidedInventory.isItemValidForSlot(var1, var2)
    }

    override fun getSlotsForFace(side: EnumFacing): IntArray =
        sidedInventory.getSlotsForFace(side)

    override fun canInsertItem(slot: Int, stack: ItemStack, side: EnumFacing): Boolean =
        sidedInventory.canInsertItem(slot, stack, side)

    override fun canExtractItem(slot: Int, stack: ItemStack, side: EnumFacing): Boolean =
        sidedInventory.canExtractItem(slot, stack, side)
}
