package mods.eln.node

import mods.eln.Eln
import mods.eln.misc.UtilsClient
import net.minecraft.world.level.block.entity.BlockEntity
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import mods.eln.misc.xCoord
import mods.eln.misc.yCoord
import mods.eln.misc.zCoord

class NodeEntityClientSender(private val e: BlockEntity, private val nodeUuid: String) {
    fun preparePacketForServer(stream: DataOutputStream) {
        try {
            stream.writeByte(Eln.packetPublishForNode.toInt())
            stream.writeInt(e.xCoord)
            stream.writeInt(e.yCoord)
            stream.writeInt(e.zCoord)
            stream.writeByte(mods.eln.misc.DimensionIds.id(e.level!!))
            stream.writeUTF(nodeUuid)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun sendPacketToServer(bos: ByteArrayOutputStream?) {
        UtilsClient.sendPacketToServer(bos!!)
    }

    fun clientSendBoolean(id: Byte, value: Boolean) {
        try {
            val bos = ByteArrayOutputStream()
            val stream = DataOutputStream(bos)
            preparePacketForServer(stream)
            stream.writeByte(id.toInt())
            stream.writeByte(if (value) 1 else 0)
            sendPacketToServer(bos)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun clientSendId(id: Byte) {
        try {
            val bos = ByteArrayOutputStream()
            val stream = DataOutputStream(bos)
            preparePacketForServer(stream)
            stream.writeByte(id.toInt())
            sendPacketToServer(bos)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun clientSendString(id: Byte, str: String) {
        try {
            val bos = ByteArrayOutputStream()
            val stream = DataOutputStream(bos)
            preparePacketForServer(stream)
            stream.writeByte(id.toInt())
            stream.writeUTF(str)
            sendPacketToServer(bos)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun clientSendFloat(id: Byte, str: Float) {
        try {
            val bos = ByteArrayOutputStream()
            val stream = DataOutputStream(bos)
            preparePacketForServer(stream)
            stream.writeByte(id.toInt())
            stream.writeFloat(str)
            sendPacketToServer(bos)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun clientSendInt(id: Byte, str: Int) {
        try {
            val bos = ByteArrayOutputStream()
            val stream = DataOutputStream(bos)
            preparePacketForServer(stream)
            stream.writeByte(id.toInt())
            stream.writeInt(str)
            sendPacketToServer(bos)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun clientSendDouble(id: Byte, num: Double) {
        try {
            val bos = ByteArrayOutputStream()
            val stream = DataOutputStream(bos)
            preparePacketForServer(stream)
            stream.writeByte(id.toInt())
            stream.writeDouble(num)
            sendPacketToServer(bos)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
