package mods.eln.packets

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.minecraftforge.fml.common.network.simpleimpl.IMessage

class GenericPacket : IMessage {
    var buf: ByteBuf? = null

    constructor()

    constructor(data: ByteArray) {
        this.buf = Unpooled.wrappedBuffer(data)
    }

    override fun fromBytes(buf: ByteBuf) {
        this.buf = buf.copy()
    }

    override fun toBytes(buf: ByteBuf) {
        this.buf?.let {
            buf.writeBytes(it, it.readerIndex(), it.readableBytes())
        }
    }
}
