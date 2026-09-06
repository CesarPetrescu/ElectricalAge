package mods.eln.sound;

import net.neoforged.bus.api.SubscribeEvent;
import mods.eln.client.UuidManager;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;

public class SoundClientEventListener {

    UuidManager uuidManager;
    ArrayList<Integer> currentUuid = null;

    public SoundClientEventListener(UuidManager uuidManager) {
        this.uuidManager = uuidManager;
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void event(PlaySoundSourceEvent e) {
        if (currentUuid == null) return;
        uuidManager.add(currentUuid, new SoundClientEntity(e.getManager(), e.getSound()));
    }

    static class KillSound {
        public SoundInstance sound;
        public SoundEngine sm;

        public void kill() {
            sm.stopSound(sound);
        }
    }
}
