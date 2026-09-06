package mods.eln.packets

import io.netty.buffer.ByteBuf
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.network.ByteBufUtils

class SixNodeWailaResponsePacket : TransparentNodeResponsePacket {
    lateinit var side: Direction

    constructor() {}

    constructor(coord: Coordinate, side: Direction, itemStack: ItemStack, data: Map<String, String>) : super(data, coord, itemStack) {
        this.side = side
    }

    override fun fromBytes(buf: ByteBuf?) {
        super.fromBytes(buf)
        side = Direction.fromInt(buf?.readInt() ?: 0)
    }

    override fun toBytes(buf: ByteBuf?) {
        super.toBytes(buf)
        buf?.writeInt(side.int)
    }
}
