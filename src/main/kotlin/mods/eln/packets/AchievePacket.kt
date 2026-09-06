package mods.eln.packets

import mods.eln.network.ByteBufUtils
import mods.eln.network.IMessage
import io.netty.buffer.ByteBuf

class AchievePacket : IMessage {

    var text: String?

    @Suppress("unused") // This actually is used by Forge and will crash if you delete it
    constructor() {
        text = null
    }

    constructor(text: String?) {
        this.text = text
    }

    override fun fromBytes(buf: ByteBuf) {
        text = ByteBufUtils.readUTF8String(buf)
    }

    override fun toBytes(buf: ByteBuf) {
        ByteBufUtils.writeUTF8String(buf, text)
    }
}
