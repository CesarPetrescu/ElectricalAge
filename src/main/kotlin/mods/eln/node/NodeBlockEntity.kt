package mods.eln.node

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import mods.eln.Eln
import mods.eln.cable.CableRenderDescriptor
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.Utils
import mods.eln.misc.Utils.fatal
import mods.eln.misc.Utils.notifyNeighbor
import mods.eln.misc.Utils.println
import mods.eln.misc.UtilsClient

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.play.server.SPacketCustomPayload
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import io.netty.buffer.Unpooled
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.util.ITickable
import net.minecraft.world.phys.AABB
import net.minecraft.world.level.LightLayer
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import mods.eln.misc.getBlock
import mods.eln.misc.getTileEntity
import mods.eln.misc.setBlock
import mods.eln.misc.setBlockToAir
import mods.eln.misc.xCoord
import mods.eln.misc.yCoord
import mods.eln.misc.zCoord

abstract class NodeBlockEntity : BlockEntity(), ITickable, ITileEntitySpawnClient, INodeEntity {
    val block: NodeBlock
        get() = getBlockType() as NodeBlock
    var redstone = false
    var lastLight = 0xFF
    var firstUnserialize = true
    override fun serverPublishUnserialize(stream: DataInputStream) {
        var light = 0
        try {
            if (firstUnserialize) {
                firstUnserialize = false
                notifyNeighbor(this)
            }
            val b = stream.readByte()
            light = b.toInt() and 0xF
            val newRedstone = b.toInt() and 0x10 != 0
            if (redstone != newRedstone) {
                redstone = newRedstone
                world.updateNeighborsAt(pos, blockType, false)
            } else {
                redstone = newRedstone
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        /*	if(lastLight == 0xFF) //boot trololol
        {
			lastLight = 15;
			world.checkLightFor(LightLayer.BLOCK, pos);
		}*/if (lastLight != light) {
            lastLight = light
            world.checkLightFor(LightLayer.BLOCK, pos)
        }
    }

    override fun serverPacketUnserialize(stream: DataInputStream) {}

    abstract fun isProvidingWeakPower(side: Direction?): Int

    var internalNode: Node? = null

    val node: Node?
        get() {
            if (world.isClientSide) {
                fatal()
            }
            if (internalNode == null) {
                val nodeFromCoordonate = NodeManager.instance!!.getNodeFromCoordonate(Coordinate(xCoord, yCoord, zCoord, world))
                if (nodeFromCoordonate is Node) {
                    internalNode = nodeFromCoordonate
                } else if (nodeFromCoordonate != null) {
                    Utils.println("WARN: getNode found non-Node ${nodeFromCoordonate.javaClass.simpleName} at ${Coordinate(xCoord, yCoord, zCoord, world)}")
                }
            }
            return internalNode
        }

    override fun newContainer(side: Direction, player: Player): AbstractContainerMenu? {
        return null
    }

    override fun newGuiDraw(side: Direction, player: Player): Screen? {
        // Debugging tip: If the GUI isn't working, but you can see it trying to open in the client debug log,
        // check that you have the renderer (client) class set correctly in the descriptor
        return null
    }

    @OnlyIn(Dist.CLIENT)
    override fun getRenderBoundingBox(): AABB {
        return if (cameraDrawOptimisation()) localRenderBoundingBox() else unoptimizedRenderBoundingBox()
    }

    open fun cameraDrawOptimisation(): Boolean {
        return true
    }

    @OnlyIn(Dist.CLIENT)
    protected fun localRenderBoundingBox(): AABB {
        return AABB(
            (xCoord - 1).toDouble(),
            (yCoord - 1).toDouble(),
            (zCoord - 1).toDouble(),
            (xCoord + 1).toDouble(),
            (yCoord + 1).toDouble(),
            (zCoord + 1).toDouble()
        )
    }

    @OnlyIn(Dist.CLIENT)
    open fun unoptimizedRenderBoundingBox(): AABB {
        return localRenderBoundingBox()
    }

    val lightValue: Int
        get() = if (world.isClientSide) {
            if (lastLight == 0xFF) {
                0
            } else lastLight
        } else {
            node?.lightValue?: 0
        }

    /**
     * Reads a tile entity from NBT.
     */
    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
    }

    /**
     * Writes a tile entity to NBT.
     */
    override fun writeToNBT(nbt: CompoundTag): CompoundTag {
        return super.writeToNBT(nbt)
    }

    //max draw distance
    @OnlyIn(Dist.CLIENT)
    override fun getMaxRenderDistanceSquared(): Double {
        return 4096.0 * 4 * 4
    }

    @Suppress("UNUSED_PARAMETER") fun onBlockPlacedBy(front: Direction?, entityLiving: LivingEntity?, metadata: Int) {}
    var updateEntityFirst = true

    /**
     * 1.8 replaced BlockEntity.canUpdate()/updateEntity() with the ITickable interface, which a
     * tile entity only implements when it actually ticks.
     */
    override fun update() {
        if (updateEntityFirst) {
            updateEntityFirst = false
            if (!world.isClientSide) {
                // world.setBlock(xCoord, yCoord, zCoord, 0);
            } else {
                clientList.add(this)
            }
        }
    }

    fun onBlockAdded() {
        if (!world.isClientSide && node == null) {
            world.setBlockToAir(xCoord, yCoord, zCoord)
        }
    }

    fun onBreakBlock() {
        if (!world.isClientSide) {
            if (node == null) return
            node!!.onBreakBlock()
        }
    }

    override fun onChunkUnload() {
        if (world.isClientSide) {
            destructor()
        }
    }

    //client only
    open fun destructor() {
        clientList.remove(this)
    }

    override fun invalidate() {
        if (world.isClientSide) {
            destructor()
        }
        super.invalidate()
    }

    fun onBlockActivated(entityPlayer: Player?, side: Direction?, vx: Float, vy: Float, vz: Float): Boolean {
        if (!world.isClientSide) {
            if (node == null) return false
            node!!.onBlockActivated(entityPlayer!!, side!!, vx, vy, vz)
            return true
        }
        //if(entityPlayer.getMainHandItem().getItem() instanceof ItemBlock)
        run { return true }
        //return true;
    }

    fun onNeighborBlockChange() {
        if (!world.isClientSide) {
            if (node == null) return
            node!!.onNeighborBlockChange()
        }
    }

    /**
     * The node's publish payload is not a vanilla NBT sync, so it travels on the mod's own
     * channel. 1.8 narrowed the tile-entity sync packet to ClientboundBlockEntityDataPacket, and
     * SPacketCustomPayload now takes a FriendlyByteBuf, so the bytes are wrapped here.
     */
    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket? {
        val node = node
        if (node == null) {
            println("ASSERT NULL NODE getUpdatePacket() nodeblock entity")
            return null
        }
        return null
    }

    fun buildPublishPayload(): SPacketCustomPayload? {
        val node = node ?: return null
        val payload = node.publishPacket?.toByteArray() ?: return null
        return SPacketCustomPayload(Eln.channelName, FriendlyByteBuf(Unpooled.wrappedBuffer(payload)))
    }

    open fun preparePacketForServer(stream: DataOutputStream) {
        try {
            stream.writeByte(Eln.packetPublishForNode.toInt())
            stream.writeInt(xCoord)
            stream.writeInt(yCoord)
            stream.writeInt(zCoord)
            stream.writeByte(world.dimension())
            stream.writeUTF(nodeUuid)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    open fun sendPacketToServer(bos: ByteArrayOutputStream?) {
        UtilsClient.sendPacketToServer(bos!!)
    }

    open fun getCableRender(side: Direction, lrdu: LRDU): CableRenderDescriptor? {
        return null
    }

    fun getAdjacentCableRender(side: Direction, lrdu: LRDU): CableRenderDescriptor? {
        val lookupKey = CableRenderLookupKey(world.dimension(), xCoord, yCoord, zCoord, side, lrdu)
        val activeLookups = adjacentCableRenderLookups.get()
        if (!activeLookups.add(lookupKey)) return null

        try {
            findWrappedAdjacentCableRender(side, lrdu)?.let { return it }
            return findDirectAdjacentCableRender(side, lrdu)
        } finally {
            activeLookups.remove(lookupKey)
            if (activeLookups.isEmpty()) {
                adjacentCableRenderLookups.remove()
            }
        }
    }

    private fun findWrappedAdjacentCableRender(side: Direction, lrdu: LRDU): CableRenderDescriptor? {
        val emptyBlockCoord = intArrayOf(xCoord, yCoord, zCoord)
        side.applyTo(emptyBlockCoord, 1)
        val block = world.getBlock(emptyBlockCoord[0], emptyBlockCoord[1], emptyBlockCoord[2])
        if (!NodeBase.isBlockWrappable(block, world, xCoord, yCoord, zCoord)) return null

        val elementSide = side.applyLRDU(lrdu)
        val otherBlockCoord = intArrayOf(emptyBlockCoord[0], emptyBlockCoord[1], emptyBlockCoord[2])
        elementSide.applyTo(otherBlockCoord, 1)

        val tileEntity = world.getBlockEntity(otherBlockCoord[0], otherBlockCoord[1], otherBlockCoord[2]) as? NodeBlockEntity
            ?: return null
        val otherDirection = elementSide.inverse
        val otherLRDU = otherDirection.getLRDUGoingTo(side)?.inverse() ?: return null
        return tileEntity.getCableRender(otherDirection, otherLRDU)
    }

    private fun findDirectAdjacentCableRender(side: Direction, lrdu: LRDU): CableRenderDescriptor? {
        val otherBlockCoord = intArrayOf(xCoord, yCoord, zCoord)
        side.applyTo(otherBlockCoord, 1)
        val tileEntity = world.getBlockEntity(otherBlockCoord[0], otherBlockCoord[1], otherBlockCoord[2]) as? NodeBlockEntity
            ?: return null
        return tileEntity.getCableRender(side.inverse, lrdu.inverseIfLR())
    }

    open fun getCableDry(side: Direction?, lrdu: LRDU?): Int {
        return 0
    }

    fun canConnectRedstone(@Suppress("UNUSED_PARAMETER") xn: Direction?): Boolean {
        return if (world.isClientSide) redstone else {
            if (node == null) false else node!!.canConnectRedstone()
        }
    }

    open fun clientRefresh(deltaT: Float) {}

    private data class CableRenderLookupKey(
        val dimension: Int,
        val x: Int,
        val y: Int,
        val z: Int,
        val side: Direction,
        val lrdu: LRDU
    )

    companion object {
        private val adjacentCableRenderLookups = object : ThreadLocal<MutableSet<CableRenderLookupKey>>() {
            override fun initialValue(): MutableSet<CableRenderLookupKey> = HashSet()
        }

        @JvmField
        //val clientList = LinkedList<NodeBlockEntity>()
        val clientList = LinkedBlockingQueue<NodeBlockEntity>()
        fun getEntity(x: Int, y: Int, z: Int): NodeBlockEntity? {
            var entity: BlockEntity?
            if (Minecraft.getInstance().level.getBlockEntity(x, y, z).also { entity = it } != null) {
                if (entity is NodeBlockEntity) {
                    return entity as NodeBlockEntity?
                }
            }
            return null
        }
    }
}
