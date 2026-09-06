package mods.eln.packets

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import mods.eln.Eln
import net.minecraft.client.Minecraft
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.io.DataInputStream

class GenericPacketHandler : IMessageHandler<GenericPacket, IMessage> {
    override fun onMessage(message: GenericPacket, ctx: MessageContext): IMessage? {
        val buf = message.buf?.retain() ?: return null
        
        if (ctx.side == Side.CLIENT) {
            handleClient(buf)
        } else {
            handleServer(buf, ctx)
        }
        return null
    }

    @SideOnly(Side.CLIENT)
    private fun handleClient(buf: ByteBuf) {
        val minecraft = Minecraft.getMinecraft()
        minecraft.addScheduledTask {
            try {
                val player = minecraft.player
                val connection = minecraft.connection
                if (player != null && connection != null) {
                    val stream = DataInputStream(ByteBufInputStream(buf))
                    Eln.packetHandler.packetRx(stream, connection.networkManager, player)
                }
            } catch (e: Exception) {
                Eln.logger.error("Error handling client packet", e)
            } finally {
                buf.release()
            }
        }
    }

    private fun handleServer(buf: ByteBuf, ctx: MessageContext) {
        val player = ctx.serverHandler.player
        player.serverWorld.addScheduledTask {
            try {
                val stream = DataInputStream(ByteBufInputStream(buf))
                Eln.packetHandler.packetRx(stream, player.connection.networkManager, player)
            } catch (e: Exception) {
                Eln.logger.error("Error handling server packet", e)
            } finally {
                buf.release()
            }
        }
    }
}
