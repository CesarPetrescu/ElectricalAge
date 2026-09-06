package mods.eln.sound

import mods.eln.misc.Coordinate
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.resources.sounds.TickableSoundInstance
import net.minecraft.client.resources.sounds.AbstractSoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource

/**
 * 1.12.2: [SoundInstance] grew [SoundInstance.getSound]/[SoundInstance.createAccessor]/[SoundInstance.getCategory], which need the
 * sound handler's registry lookup. Vanilla's [AbstractSoundInstance] already implements those, so we extend it
 * instead of re-implementing the accessor plumbing; the position stays live-read from [coord].
 */
abstract class LoopedSound(val sample: String, val coord: Coordinate,
                           val attentuationType: SoundInstance.AttenuationType = SoundInstance.AttenuationType.LINEAR) :
    AbstractSoundInstance(ResourceLocation(sample), SoundSource.BLOCKS), TickableSoundInstance {
    var active = true

    init {
        repeat = true
        attenuationType = attentuationType
    }

    final override fun getXPosF() = coord.x.toFloat() + 0.5f
    final override fun getYPosF() = coord.y.toFloat() + 0.5f
    final override fun getZPosF() = coord.z.toFloat() + 0.5f

    override fun getPitch() = 1f
    override fun getVolume() = 1f
    override fun isDonePlaying() = !active

    override fun update() {}
}
