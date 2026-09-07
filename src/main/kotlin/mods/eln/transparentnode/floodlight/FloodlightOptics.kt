package mods.eln.transparentnode.floodlight

import mods.eln.misc.Direction
import mods.eln.misc.HybridNodeDirection
import mods.eln.misc.LRDU
import net.minecraft.world.phys.Vec3
import kotlin.math.*

/** One orthonormal mounting frame, including walls and ceilings. No pole singularities. */
object FloodlightOptics {
    private fun vector(direction: HybridNodeDirection): Vec3 = when (direction) {
        HybridNodeDirection.XN -> Vec3(-1.0, 0.0, 0.0)
        HybridNodeDirection.XP -> Vec3(1.0, 0.0, 0.0)
        HybridNodeDirection.YN -> Vec3(0.0, -1.0, 0.0)
        HybridNodeDirection.YP -> Vec3(0.0, 1.0, 0.0)
        HybridNodeDirection.ZN -> Vec3(0.0, 0.0, -1.0)
        HybridNodeDirection.ZP -> Vec3(0.0, 0.0, 1.0)
    }

    fun isMountingPlanePort(axis: HybridNodeDirection, side: Direction, lrdu: LRDU): Boolean =
        side.applyLRDU(lrdu) == axis.inverse.toStandardDirection()

    fun direction(horizontal: Double, vertical: Double, axis: HybridNodeDirection, facing: HybridNodeDirection): Vec3 {
        val up = vector(axis)
        val forward = vector(facing)
        require(abs(up.dot(forward)) < 0.01) { "Floodlight facing must lie in its mounting plane" }
        val h = Math.toRadians(horizontal)
        val v = Math.toRadians(vertical)
        return forward.scale(cos(v) * cos(h))
            .add(up.cross(forward).scale(cos(v) * sin(h)))
            .add(up.scale(sin(v))).normalize()
    }

    /** Each ray carries its flat-ended-cone distance multiplier. Width is the full cone angle. */
    fun rays(horizontal: Double, vertical: Double, width: Double, axis: HybridNodeDirection,
             facing: HybridNodeDirection): List<Pair<Vec3, Double>> {
        val centre = direction(horizontal, vertical, axis, facing)
        val halfWidth = Math.toRadians(width.coerceIn(0.0, 45.0) / 2)
        val result = mutableListOf(centre to 1.0)
        if (halfWidth == 0.0) return result
        // Yaw tangent remains defined even when aiming straight up/down.
        val tangent = direction(horizontal + 90.0, 0.0, axis, facing)
        val bitangent = centre.cross(tangent).normalize()
        val rings = ceil(width.coerceIn(0.0, 45.0) * 4 / 45).toInt()
        for (ring in 1..rings) {
            val angle = halfWidth * ring / rings
            for (sample in 0 until ring * 8) {
                val azimuth = 2 * PI * sample / (ring * 8)
                val offset = tangent.scale(cos(azimuth)).add(bitangent.scale(sin(azimuth)))
                result.add(centre.scale(cos(angle)).add(offset.scale(sin(angle))).normalize() to (1 / cos(angle)))
            }
        }
        return result
    }
}
