package mods.eln.sound

import mods.eln.misc.Coordinate
import net.minecraft.client.audio.ISound
import net.minecraft.client.audio.ITickableSound
import net.minecraft.client.audio.PositionedSound
import net.minecraft.util.ResourceLocation
import net.minecraft.util.SoundCategory

/**
 * 1.12.2: [ISound] grew [ISound.getSound]/[ISound.createAccessor]/[ISound.getCategory], which need the
 * sound handler's registry lookup. Vanilla's [PositionedSound] already implements those, so we extend it
 * instead of re-implementing the accessor plumbing; the position stays live-read from [coord].
 */
abstract class LoopedSound(val sample: String, val coord: Coordinate,
                           val attentuationType: ISound.AttenuationType = ISound.AttenuationType.LINEAR) :
    PositionedSound(ResourceLocation(sample), SoundCategory.BLOCKS), ITickableSound {
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
