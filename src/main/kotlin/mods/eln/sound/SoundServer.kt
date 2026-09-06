package mods.eln.sound

import mods.eln.Eln
import mods.eln.misc.DimensionIds
import mods.eln.misc.Utils.sendPacketToClient
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException

object SoundServer {
    fun play(p: SoundCommand) {
        val bos = ByteArrayOutputStream(64)
        val stream = DataOutputStream(bos)
        try {
            val world = p.world ?: return
            stream.writeByte(Eln.packetPlaySound.toInt())
            stream.writeByte(DimensionIds.id(world))
            p.writeTo(stream)
            val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() ?: return
            for (player in server.playerList.players) {
                if (player.level() === world && player.distanceToSqr(p.x, p.y, p.z) < (p.rangeMax + 2.0) * (p.rangeMax + 2.0)) {
                    sendPacketToClient(bos, player)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
