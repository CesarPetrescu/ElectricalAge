package mods.eln.ghost;

import mods.eln.misc.Coordinate;
import mods.eln.misc.Direction;
import net.minecraft.world.entity.player.Player;

public interface GhostObserver {

    public abstract Coordinate getGhostObserverCoordinate();

    public abstract void ghostDestroyed(int UUID);

    public abstract boolean ghostBlockActivated(int UUID, Player entityPlayer, Direction side, float vx, float vy, float vz);
}
