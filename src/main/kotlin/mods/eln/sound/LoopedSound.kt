package mods.eln.sound

import mods.eln.misc.Coordinate
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource

/**
 * A looping block sound whose position is read live from [coord]. Vanilla's
 * [AbstractTickableSoundInstance] carries the accessor plumbing; the loop ends when [active] drops.
 */
abstract class LoopedSound(val sample: String, val coord: Coordinate,
                           attenuation: SoundInstance.Attenuation = SoundInstance.Attenuation.LINEAR) :
    AbstractTickableSoundInstance(SoundEvent.createVariableRangeEvent(ResourceLocation.parse(sample)), SoundSource.BLOCKS, SoundInstance.createUnseededRandom()) {
    var active = true

    init {
        looping = true
        this.attenuation = attenuation
        x = coord.x.toDouble() + 0.5
        y = coord.y.toDouble() + 0.5
        z = coord.z.toDouble() + 0.5
    }

    override fun getPitch() = 1f
    override fun getVolume() = 1f

    override fun tick() {
        if (!active) stop()
    }
}
