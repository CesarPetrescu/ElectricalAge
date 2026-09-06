package mods.eln.sound

import net.neoforged.bus.api.SubscribeEvent
import mods.eln.client.UuidManager
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.SoundManager
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent
import net.neoforged.neoforge.common.NeoForge

class SoundClientEventListener(var uuidManager: UuidManager) {
    @JvmField
    var currentUuid: ArrayList<Int>? = null

    init {
        NeoForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    @Suppress("DEPRECATION")
    fun event(e: PlaySoundSourceEvent) {
        if (currentUuid == null) return
        uuidManager.add(currentUuid!!, SoundClientEntity(e.manager, e.sound))
    }

    internal class KillSound {
        var sound: SoundInstance? = null
        var sm: SoundManager? = null

        fun kill() {
            sm!!.stopSound(sound)
        }
    }
}
