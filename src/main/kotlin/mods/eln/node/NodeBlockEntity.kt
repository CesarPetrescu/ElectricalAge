package mods.eln.node

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

import mods.eln.misc.DimensionIds
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import mods.eln.misc.getBlock
import mods.eln.misc.getBlockEntity
import mods.eln.misc.setBlock
import mods.eln.misc.setBlockToAir
import mods.eln.misc.xCoord
import mods.eln.misc.yCoord
import mods.eln.misc.zCoord
import mods.eln.misc.writeToNBT

abstract class NodeBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) : BlockEntity(type, pos, state), ITileEntitySpawnClient, INodeEntity {
    val block: NodeBlock
        get() = blockState.block as NodeBlock

    /** 1.7.10's `worldObj`: the level, which a placed block entity always has. */
    val world: Level
        get() = level!!
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
                world.updateNeighborsAt(blockPos, blockState.block)
            } else {
                redstone = newRedstone
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        if (lastLight != light) {
            lastLight = light
            // the chunk packet carried the light the server had; the publish frame carries changes since
            world.getAuxLightManager(blockPos)?.setLightAt(blockPos, light)
        }
    }

    override fun serverPacketUnserialize(stream: DataInputStream) {}

    abstract fun isProvidingWeakPower(side: Direction?): Int

    var internalNode: Node? = null

    /**
     * Nodes simulate whether or not their chunk is loaded, so a lamp may have changed while its
     * chunk was away: the chunk's light record is brought up to date with the node when it comes back.
     */
    override fun onLoad() {
        if (world.isClientSide) return
        val node = node ?: return
        world.getAuxLightManager(blockPos)?.setLightAt(blockPos, node.lightValue)
    }

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

    /** Read by the block entity renderers (1.21 asks the renderer, not the entity; see SixNodeRender). */
    fun getRenderBoundingBox(): AABB {
        return if (cameraDrawOptimisation()) localRenderBoundingBox() else unoptimizedRenderBoundingBox()
    }

    open fun cameraDrawOptimisation(): Boolean {
        return true
    }

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

    open fun unoptimizedRenderBoundingBox(): AABB {
        return localRenderBoundingBox()
    }

    /** Reads a tile entity from NBT (1.7.10 name; 1.21 calls it loadAdditional). */
    open fun readFromNBT(nbt: CompoundTag) {}

    /** Writes a tile entity to NBT (1.7.10 name; 1.21 calls it saveAdditional). */
    open fun writeToNBT(nbt: CompoundTag): CompoundTag = nbt

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        readFromNBT(tag)
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        writeToNBT(tag)
    }

    // The max draw distance (4096 * 16 in 1.7.10) is the renderer's getViewDistance() now.

    @Suppress("UNUSED_PARAMETER") fun onBlockPlacedBy(front: Direction?, entityLiving: LivingEntity?, metadata: Int) {}
    var updateEntityFirst = true

    /** Ticked by the block's BlockEntityTicker (1.7.10's updateEntity). */
    open fun update() {
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

    override fun onChunkUnloaded() {
        if (world.isClientSide) {
            destructor()
        }
    }

    //client only
    open fun destructor() {
        clientList.remove(this)
    }

    override fun setRemoved() {
        if (level?.isClientSide == true) {
            destructor()
        }
        super.setRemoved()
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
     * channel (see [buildPublishPayload]); vanilla gets no update packet.
     */
    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? = null

    /** The node's publish packet bytes, for the client that just started watching this chunk. */
    fun buildPublishPayload(): ByteArray? {
        val node = node ?: return null
        return node.publishPacket?.toByteArray()
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag = PublishSync.updateTag(buildPublishPayload())

    override fun handleUpdateTag(tag: CompoundTag, registries: HolderLookup.Provider) = PublishSync.handle(tag, this)

    open fun preparePacketForServer(stream: DataOutputStream) {
        try {
            stream.writeByte(Eln.packetPublishForNode.toInt())
            stream.writeInt(xCoord)
            stream.writeInt(yCoord)
            stream.writeInt(zCoord)
            stream.writeByte(DimensionIds.id(world))
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
        val lookupKey = CableRenderLookupKey(DimensionIds.id(world), xCoord, yCoord, zCoord, side, lrdu)
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
    }
}
