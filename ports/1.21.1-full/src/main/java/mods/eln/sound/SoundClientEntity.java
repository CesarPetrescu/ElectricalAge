package mods.eln.sound;

import mods.eln.client.IUuidEntity;
import mods.eln.misc.Utils;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;

public class SoundClientEntity implements IUuidEntity {

    public SoundInstance sound;
    public SoundEngine sm;

    int borneTimer = 5;

    public SoundClientEntity(SoundEngine sm, SoundInstance sound) {
        this.sound = sound;
        this.sm = sm;
    }

    @Override
    public boolean isAlive() {
        if (borneTimer != 0) {
            borneTimer--;
            return true;
        }
        return sm.isSoundPlaying(sound);
    }

    @Override
    public void kill() {
        Utils.println("Sound deleted");
        sm.stopSound(sound);
    }
}
