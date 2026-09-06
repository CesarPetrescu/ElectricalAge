package mods.eln.sound

import mods.eln.client.ClientSetup
import mods.eln.client.SoundLoader
import mods.eln.misc.Utils.TraceRayWeightOpaque
import mods.eln.misc.Utils.println
import mods.eln.misc.Utils.traceRay
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import kotlin.math.pow
import kotlin.math.sqrt

object SoundClient {
    fun play(p: SoundCommand) {
        ClientSetup.soundClientEventListener.currentUuid = p.uuid

        val player = Minecraft.getInstance().player ?: return
        val world = p.world ?: return
        if (world.dimension() != player.level().dimension()) return
        val distance = sqrt((p.x - player.x).pow(2.0) + (p.y - player.y).pow(2.0) + (p.z - player.z).pow(2.0))
        if (distance >= p.rangeMax) return
        var distanceFactor = 1f
        if (distance > p.rangeNominal) {
            distanceFactor = ((p.rangeMax - distance) / (p.rangeMax - p.rangeNominal)).toFloat()
        }

        val blockFactor = traceRay(
            world,
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

            world.playLocalSound(
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
                world.playLocalSound(
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

        ClientSetup.soundClientEventListener.currentUuid = null
    }

    /**
     * The client plays SoundEvents, not sound names. This is a purely client-side play (the
     * server already told us which track), and the sound engine resolves the event's location in
     * sounds.json itself, so the event need not be registered.
     */
    private fun soundEvent(track: String): SoundEvent = SoundEvent.createVariableRangeEvent(ResourceLocation.parse(track))
}
