package mods.eln.sixnode.lampsocket

import mods.eln.misc.LRDU
import mods.eln.misc.Obj3D
import kotlin.math.*

object LampConnections {
    const val GLAND_LENGTH = 1.0f / 16
    fun cableEnd(edge: Float): Float = (edge + GLAND_LENGTH).coerceAtMost(0.5f)

    /** Distances from the centre to each housing edge in the cable's unrotated Y/Z plane. */
    fun edges(yMin: Float, yMax: Float, zMin: Float, zMax: Float, angle: Double): FloatArray {
        val a = Math.toRadians(angle)
        val points = listOf(yMin to zMin, yMin to zMax, yMax to zMin, yMax to zMax).map { (y, z) ->
            (y * cos(a) - z * sin(a)) to (y * sin(a) + z * cos(a))
        }
        return floatArrayOf((-points.minOf { it.second }).toFloat(), points.maxOf { it.second }.toFloat(),
            points.maxOf { it.first }.toFloat(), (-points.minOf { it.first }).toFloat())
            .map { it.coerceIn(0.08f, 0.48f) }.toFloatArray()
    }

    fun edges(part: Obj3D.Obj3DPart?, front: LRDU, offset: Double, rotateFront: Boolean = true): FloatArray {
        if (part == null) return FloatArray(4) { 0.15f }
        val rotation = if (rotateFront) when (front) {
            LRDU.Left -> 0.0
            LRDU.Up -> 90.0
            LRDU.Right -> 180.0
            LRDU.Down -> 270.0
        } else 0.0
        return edges(part.yMin, part.yMax, part.zMin, part.zMax, offset + rotation)
    }
}
