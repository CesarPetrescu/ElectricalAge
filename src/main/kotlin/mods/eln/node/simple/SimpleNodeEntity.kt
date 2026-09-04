package mods.eln.node.simple

import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
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
import net.minecraft.client.gui.GuiScreen
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.Container
import net.minecraft.network.Packet
import io.netty.buffer.Unpooled
import net.minecraft.network.PacketBuffer
import net.minecraft.network.play.server.SPacketCustomPayload
import net.minecraft.tileentity.TileEntity
import java.io.DataInputStream
import java.io.IOException
import mods.eln.misc.markBlockForUpdate
import mods.eln.misc.setBlockToAir
import mods.eln.misc.xCoord
import mods.eln.misc.yCoord
import mods.eln.misc.zCoord

abstract class SimpleNodeEntity(override val nodeUuid: String) : TileEntity(), INodeEntity {
    open var node: SimpleNode? = null
        get() {
            if (world.isRemote) {
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
        /*if (!world.isRemote){
			if (getNode() == null) {
				world.setBlockToAir(xCoord, yCoord, zCoord);
			}
		}*/
    }

    fun onBreakBlock() {
        if (!world.isRemote) {
            if (node == null) return
            node!!.onBreakBlock()
        }
    }

    override fun onChunkUnload() {
        super.onChunkUnload()
        if (world.isRemote) {
            destructor()
        }
    }

    // client only
    fun destructor() {}
    override fun invalidate() {
        if (world.isRemote) {
            destructor()
        }
        super.invalidate()
    }

    fun onBlockActivated(entityPlayer: EntityPlayer?, side: Direction?, vx: Float, vy: Float, vz: Float): Boolean {
        if (!world.isRemote) {
            if (node == null) return false
            node!!.onBlockActivated(entityPlayer!!, side!!, vx, vy, vz)
            return true
        }
        return true
    }

    fun onNeighborBlockChange() {
        if (!world.isRemote) {
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
        return SPacketCustomPayload(Eln.channelName, PacketBuffer(Unpooled.wrappedBuffer(payload)))
    }

    open lateinit var sender: NodeEntityClientSender

    init {
        println("NodeUUID: $nodeUuid")
        sender = NodeEntityClientSender(this, nodeUuid)
    }

    //*********************** GUI ***************************
    override fun newContainer(side: Direction, player: EntityPlayer): Container? {
        return null
    }

    @SideOnly(Side.CLIENT)
    override fun newGuiDraw(side: Direction, player: EntityPlayer): GuiScreen? {
        return null
    }
}
