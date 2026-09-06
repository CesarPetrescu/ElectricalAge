package mods.eln.sound

import mods.eln.client.ClientProxy
import mods.eln.client.SoundLoader
import mods.eln.misc.Utils.TraceRayWeightOpaque
import mods.eln.misc.Utils.println
import mods.eln.misc.Utils.traceRay
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.sounds.SoundEvent
import kotlin.math.pow
import kotlin.math.sqrt

object SoundClient {
    fun play(p: SoundCommand) {
        ClientProxy.soundClientEventListener.currentUuid = p.uuid

        val player: Player = Minecraft.getInstance().player
        if (p.level!!.dimension() != player.dimension) return
        val distance = sqrt((p.x - player.x).pow(2.0) + (p.y - player.y).pow(2.0) + (p.z - player.z).pow(2.0))
        if (distance >= p.rangeMax) return
        var distanceFactor = 1f
        if (distance > p.rangeNominal) {
            distanceFactor = ((p.rangeMax - distance) / (p.rangeMax - p.rangeNominal)).toFloat()
        }

        val blockFactor = traceRay(
            p.level!!,
            player.x,
            player.y,
            player.z,
            p.x,
            p.y,
            p.z,
            TraceRayWeightOpaque()
        ) * p.blockFactor

        val trackCount = SoundLoader.getTrackCount(p.track)

        if (trackCount == 1) {
            val temp = 1.0f / (1 + blockFactor)
            p.volume *= temp.pow(2.0f)
            p.volume *= distanceFactor
            if (p.volume <= 0) return

            p.level!!.playSound(
                player.x + 2 * (p.x - player.x) / distance,
                player.y + 2 * (p.y - player.y) / distance,
                player.z + 2 * (p.z - player.z) / distance,
                soundEvent(p.track!!),
                SoundSource.BLOCKS,
                p.volume,
                p.pitch,
                false
            )
        } else {
            for (idx in 0 until trackCount) {
                var bandVolume = p.volume
                bandVolume *= distanceFactor

                bandVolume -= (((trackCount - 1 - idx) / (trackCount - 1f) + 0.2) * blockFactor).toFloat()
                println(bandVolume)
                p.level!!.playSound(
                    player.x + 2 * (p.x - player.x) / distance,
                    player.y + 2 * (p.y - player.y) / distance,
                    player.z + 2 * (p.z - player.z) / distance,
                    soundEvent(p.track + "_" + idx + "x"),
                    SoundSource.BLOCKS,
                    bandVolume,
                    p.pitch,
                    false
                )
            }
        }

        ClientProxy.soundClientEventListener.currentUuid = null
    }

    /**
     * 1.12.2 plays SoundEvents, not sound names. This is a purely client-side play (the server
     * already told us which track), so the client sound handler only needs the sounds.json key;
     * a registered event is not required for this path.
     */
    private fun soundEvent(track: String): SoundEvent =
        SoundEvent.REGISTRY.getObject(ResourceLocation(track)) ?: SoundEvent(ResourceLocation(track))
}
