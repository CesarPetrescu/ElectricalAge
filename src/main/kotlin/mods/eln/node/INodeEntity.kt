package mods.eln.node

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import mods.eln.misc.Direction
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import java.io.DataInputStream
import java.io.IOException
import net.minecraft.nbt.CompoundTag

interface INodeEntity {
    val nodeUuid: String
    fun serverPublishUnserialize(stream: DataInputStream)
    fun serverPacketUnserialize(stream: DataInputStream)

    @OnlyIn(Dist.CLIENT)
    fun newGuiDraw(side: Direction, player: Player): Screen?
    fun newContainer(side: Direction, player: Player): AbstractContainerMenu?
}

/**
 * The node's publish frame rides in the block entity's chunk-sync tag, so a client that loads
 * the chunk gets the node state the way 1.7.10's description packet delivered it (a vanilla
 * NBT sync would say nothing: the state lives in the node, not the tile). Later changes go
 * over the mod's channel as before.
 */
internal object PublishSync {
    const val KEY = "elnPublish"

    fun updateTag(payload: ByteArray?): CompoundTag {
        val tag = CompoundTag()
        if (payload != null) tag.putByteArray(KEY, payload)
        return tag
    }

    /** Reads the frame [mods.eln.node.NodeBase.publishPacket] wrote (id, coordinate, uuid, data) into [entity]. */
    fun handle(tag: CompoundTag, entity: INodeEntity) {
        val bytes = tag.getByteArray(KEY)
        if (bytes.isEmpty()) return
        try {
            val stream = DataInputStream(java.io.ByteArrayInputStream(bytes))
            stream.readByte()
            stream.readInt(); stream.readInt(); stream.readInt()
            stream.readByte()
            if (stream.readUTF() == entity.nodeUuid) entity.serverPublishUnserialize(stream)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
