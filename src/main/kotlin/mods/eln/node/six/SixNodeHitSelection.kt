package mods.eln.node.six

import mods.eln.misc.Direction

/** A full-cube hit belongs to the volumetric descriptor, not necessarily its mounting face. */
object SixNodeHitSelection {
    fun bodySide(hitSide: Direction, isBody: (Direction) -> Boolean): Direction? =
        if (isBody(hitSide)) hitSide else Direction.values().firstOrNull(isBody)
}
