package mods.eln.sound

import mods.eln.misc.Coordinate
import net.minecraft.client.audio.*
import net.minecraft.util.ResourceLocation
import net.minecraft.util.SoundCategory

abstract class LoopedSound(val sample: String, val coord: Coordinate,
                           val attentuationType: ISound.AttenuationType = ISound.AttenuationType.LINEAR) : ITickableSound {
    var active = true

    override final fun getSoundLocation() = ResourceLocation(sample)
    override final fun getXPosF() = coord.pos.x.toFloat() + 0.5f
    override final fun getYPosF() = coord.pos.y.toFloat() + 0.5f
    override final fun getZPosF() = coord.pos.z.toFloat() + 0.5f
    override final fun canRepeat() = true
    override final fun getAttenuationType() = attentuationType

    override fun getPitch() = 1f
    override fun getVolume() = 1f
    override fun isDonePlaying() = !active

    override fun getRepeatDelay() = 0
    override fun update() {}

    private var sound: Sound? = null
    private var soundEventAccessor: SoundEventAccessor? = null

    override fun getSound(): Sound {
        return sound!!
    }

    override fun createAccessor(handler: SoundHandler): SoundEventAccessor? {
        this.soundEventAccessor = handler.getAccessor(this.soundLocation)
        if (this.soundEventAccessor == null) {
            this.sound = SoundHandler.MISSING_SOUND
        } else {
            this.sound = this.soundEventAccessor!!.cloneEntry()
        }
        return this.soundEventAccessor
    }

    override fun getCategory() = SoundCategory.BLOCKS
}
