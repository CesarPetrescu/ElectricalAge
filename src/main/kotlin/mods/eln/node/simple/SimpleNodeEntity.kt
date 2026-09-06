package mods.eln.node.simple

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.Direction.Companion.fromInt
import mods.eln.misc.Utils.fatal
import mods.eln.misc.Utils.println
import mods.eln.node.INodeEntity
import mods.eln.node.NodeEntityClientSender
import mods.eln.node.NodeManager
import mods.eln.node.simple.DescriptorManager.get
import mods.eln.server.DelayedBlockRemove.Companion.add
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.network.protocol.Packet
import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.play.server.SPacketCustomPayload
import net.minecraft.world.level.block.entity.BlockEntity
import java.io.DataInputStream
import java.io.IOException
import mods.eln.misc.markBlockForUpdate
import mods.eln.misc.setBlockToAir
import mods.eln.misc.xCoord
import mods.eln.misc.yCoord
import mods.eln.misc.zCoord

abstract class SimpleNodeEntity(override val nodeUuid: String) : BlockEntity(), INodeEntity {
    open var node: SimpleNode? = null
        get() {
            if (world.isClientSide) {
                fatal()
                return null
            }
            if (world == null) return null
            if (field == null) {
                field = NodeManager.instance!!.getNodeFromCoordonate(Coordinate(xCoord, yCoord, zCoord, world)) as SimpleNode?
                if (field == null) {
                    add(Coordinate(xCoord, yCoord, zCoord, world))
                    return null
                }
            }
            return field
        }

    //***************** Wrapping **************************
    /*
	public void onBlockPlacedBy(Direction front, EntityLivingBase entityLiving, int metadata) {
	
	}
*/
    fun onBlockAdded() {
        /*if (!world.isClientSide){
			if (getNode() == null) {
				world.setBlockToAir(xCoord, yCoord, zCoord);
			}
		}*/
    }

    fun onBreakBlock() {
        if (!world.isClientSide) {
            if (node == null) return
            node!!.onBreakBlock()
        }
    }

    override fun onChunkUnload() {
        super.onChunkUnload()
        if (world.isClientSide) {
            destructor()
        }
    }

    // client only
    fun destructor() {}
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
        return true
    }

    fun onNeighborBlockChange() {
        if (!world.isClientSide) {
            if (node == null) return
            node!!.onNeighborBlockChange()
        }
    }

    //***************** Descriptor **************************
    val descriptor: Any?
        get() {
            val b = getBlockType() as SimpleNodeBlock
            return get<Any>(b.descriptorKey)
        }

    //***************** Network **************************
    var front: Direction? = null
    override fun serverPublishUnserialize(stream: DataInputStream) {
        try {
            if (front !== fromInt(stream.readByte().toInt()).also { front = it }) {
                world.markBlockForUpdate(xCoord, yCoord, zCoord)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun serverPacketUnserialize(stream: DataInputStream) {}
    /** See NodeBlockEntity.buildPublishPayload: the node payload is not an NBT sync. */
    fun buildPublishPayload(): SPacketCustomPayload? {
        val node = node ?: return null
        val payload = node.publishPacket?.toByteArray() ?: return null
        return SPacketCustomPayload(Eln.channelName, FriendlyByteBuf(Unpooled.wrappedBuffer(payload)))
    }

    open lateinit var sender: NodeEntityClientSender

    init {
        println("NodeUUID: $nodeUuid")
        sender = NodeEntityClientSender(this, nodeUuid)
    }

    //*********************** GUI ***************************
    override fun newContainer(side: Direction, player: Player): AbstractContainerMenu? {
        return null
    }

    @OnlyIn(Dist.CLIENT)
    override fun newGuiDraw(side: Direction, player: Player): Screen? {
        return null
    }
}
