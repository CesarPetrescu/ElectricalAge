package mods.eln

import mods.eln.client.ClientKeyHandler
import mods.eln.client.ClientSetup
import net.neoforged.neoforge.network.handling.IPayloadContext
import mods.eln.item.FalstadImportPacketHandler
import mods.eln.misc.Coordinate
import mods.eln.misc.Utils.println
import mods.eln.misc.Utils.sendPacketToClient
import mods.eln.node.INodeEntity
import mods.eln.node.NodeManager
import mods.eln.sound.SoundClient
import mods.eln.sound.SoundCommand
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.network.Connection
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import mods.eln.misc.getBlockEntity
import mods.eln.misc.dimension

/**
 * The byte protocol, both directions: a leading packet id selects the handler. 1.21: the bytes
 * arrive in [mods.eln.network.ElnNetwork.RawPayload]; the context's player is the sender's
 * ServerPlayer on the server and the local player on the client.
 */
class PacketHandler {
    fun onPayload(data: ByteArray, context: IPayloadContext) {
        val stream = DataInputStream(ByteArrayInputStream(data))
        packetRx(stream, context.connection(), context.player())
    }

    fun packetRx(stream: DataInputStream, manager: Connection, player: Player) {
        try {
            when (stream.readByte()) {
                Eln.packetPlayerKey -> packetPlayerKey(stream, manager, player)
                Eln.packetNodeSingleSerialized -> packetNodeSingleSerialized(stream, manager, player)
                Eln.packetPublishForNode -> packetForNode(stream, manager, player)
                Eln.packetForClientNode -> packetForClientNode(stream, manager, player)
                Eln.packetOpenLocalGui -> packetOpenLocalGui(stream, manager, player)
                Eln.packetPlaySound -> packetPlaySound(stream, manager, player)
                Eln.packetDestroyUuid -> packetDestroyUuid(stream, manager, player)
                Eln.packetClientToServerConnection -> packetNewClient(manager, player)
                Eln.packetServerToClientInfo -> packetServerInfo(stream, manager, player)
                Eln.packetFalstadImport -> packetFalstadImport(stream, manager, player)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun packetNewClient(@Suppress("UNUSED_PARAMETER") manager: Connection, player: Player) {
        val bos = ByteArrayOutputStream(64)
        val stream = DataOutputStream(bos)
        try {
            stream.writeByte(Eln.packetServerToClientInfo.toInt())
            for (c in Eln.instance.configShared) {
                c.serializeConfig(stream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        sendPacketToClient(bos, (player as ServerPlayer))
    }

    private fun packetServerInfo(stream: DataInputStream, @Suppress("UNUSED_PARAMETER") manager: Connection, @Suppress("UNUSED_PARAMETER") player: Player) {
        for (c in Eln.instance.configShared) {
            try {
                c.deserialize(stream)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun packetDestroyUuid(stream: DataInputStream, @Suppress("UNUSED_PARAMETER") manager: Connection, @Suppress("UNUSED_PARAMETER") player: Player) {
        try {
            ClientSetup.uuidManager.kill(stream.readInt())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun packetPlaySound(stream: DataInputStream, @Suppress("UNUSED_PARAMETER") manager: Connection, player: Player) {
        try {
            if (stream.readByte().toInt() != player.dimension) return
            SoundClient.play(SoundCommand.fromStream(stream, player.level()))
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun packetOpenLocalGui(stream: DataInputStream, @Suppress("UNUSED_PARAMETER") manager: Connection, player: Player) {
        try {
            val id = stream.readInt()
            mods.eln.client.ClientGuiHandler.openLocal(id, stream.readInt(), stream.readInt(), stream.readInt())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun packetForNode(stream: DataInputStream, @Suppress("UNUSED_PARAMETER") manager: Connection, player: Player?) {
        try {
            val coordinate = Coordinate(stream.readInt(), stream.readInt(), stream.readInt(), stream.readByte().toInt())
            val node = NodeManager.instance!!.getNodeFromCoordonate(coordinate)
            if (node != null && node.nodeUuid == stream.readUTF()) {
                node.networkUnserialize(stream, player as ServerPlayer?)
            } else {
                println("packetForNode node found")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun packetForClientNode(stream: DataInputStream, @Suppress("UNUSED_PARAMETER") manager: Connection, player: Player) {
        try {
            val x = stream.readInt()
            val y = stream.readInt()
            val z = stream.readInt()
            val dimension = stream.readByte().toInt()
            if (player.dimension == dimension) {
                val entity = player.level().getBlockEntity(x, y, z)
                if (entity != null && entity is INodeEntity) {
                    val node = entity as INodeEntity
                    if (node.nodeUuid == stream.readUTF()) {
                        node.serverPacketUnserialize(stream)
                        if (0 != stream.available()) {
                            println("0 != stream.available()")
                        }
                    } else {
                        println("Wrong node UUID warning")
                        val dataSkipLength = stream.readByte().toInt()
                        for (idx in 0 until dataSkipLength) {
                            stream.readByte()
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun packetNodeSingleSerialized(stream: DataInputStream, @Suppress("UNUSED_PARAMETER") manager: Connection, player: Player) {
        try {
            val x: Int = stream.readInt()
            val y: Int = stream.readInt()
            val z: Int = stream.readInt()
            val dimension: Int = stream.readByte().toInt()
            if (player.dimension == dimension) {
                val entity = player.level().getBlockEntity(x, y, z)
                if (entity != null && entity is INodeEntity) {
                    val node = entity as INodeEntity
                    if (node.nodeUuid == stream.readUTF()) {
                        node.serverPublishUnserialize(stream)
                        if (0 != stream.available()) {
                            println("0 != stream.available()")
                        }
                    } else {
                        println("Wrong node UUID warning")
                        val dataSkipLength = stream.readByte().toInt()
                        for (idx in 0 until dataSkipLength) {
                            stream.readByte()
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun packetPlayerKey(stream: DataInputStream, @Suppress("UNUSED_PARAMETER") manager: Connection, @Suppress("UNUSED_PARAMETER") player: Player?) {
        try {
            val name = stream.readUTF()
            val state = stream.readBoolean()
            ServerKeyHandler.set(name, state)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun packetFalstadImport(stream: DataInputStream, @Suppress("UNUSED_PARAMETER") manager: Connection, player: Player) {
        try {
            val length = stream.readInt()
            val bytes = ByteArray(length)
            stream.readFully(bytes)
            FalstadImportPacketHandler.handle(player as ServerPlayer, bytes)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

}
