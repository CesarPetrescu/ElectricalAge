package mods.eln.node.six

import mods.eln.misc.Direction

/** Resolve a full-cube hit without hiding surface parts that share the six-node. */
object SixNodeHitSelection {
    fun bodySide(hitSide: Direction, isBody: (Direction) -> Boolean): Direction? =
        bodySide(hitSide, isBody, isBody)

    fun bodySide(hitSide: Direction, isBody: (Direction) -> Boolean, isEnabled: (Direction) -> Boolean): Direction? {
        val body = Direction.values().firstOrNull(isBody) ?: return null
        return if (isEnabled(hitSide)) hitSide else body
    }
}
