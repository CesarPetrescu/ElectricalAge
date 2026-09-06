package mods.eln.ghost

import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import net.minecraft.world.entity.player.Player

interface GhostObserver {
    val ghostObserverCoordonate: Coordinate?
    fun ghostDestroyed(UUID: Int)
    fun ghostBlockActivated(UUID: Int, entityPlayer: Player, side: Direction, vx: Float, vy: Float, vz: Float): Boolean
}
