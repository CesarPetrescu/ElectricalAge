package mods.eln.misc

import com.google.common.base.MoreObjects
import net.minecraft.util.math.Vec3d
import java.lang.Float.NEGATIVE_INFINITY
import java.lang.Float.POSITIVE_INFINITY

class BoundingBox(xMin: Float, xMax: Float, yMin: Float, yMax: Float, zMin: Float, zMax: Float) {
    val min: Vec3d = Vec3d(xMin.toDouble(), yMin.toDouble(), zMin.toDouble())
    val max: Vec3d = Vec3d(xMax.toDouble(), yMax.toDouble(), zMax.toDouble())

    fun merge(other: BoundingBox): BoundingBox {
        return BoundingBox(
            min.xCoord.coerceAtMost(other.min.xCoord).toFloat(),
            max.xCoord.coerceAtLeast(other.max.xCoord).toFloat(),
            min.yCoord.coerceAtMost(other.min.yCoord).toFloat(),
            max.yCoord.coerceAtLeast(other.max.yCoord).toFloat(),
            min.zCoord.coerceAtMost(other.min.zCoord).toFloat(),
            max.zCoord.coerceAtLeast(other.max.zCoord).toFloat()
        )
    }

    fun centre(): Vec3d {
        return Vec3d(
            min.xCoord + (max.xCoord - min.xCoord) / 2,
            min.yCoord + (max.yCoord - min.yCoord) / 2,
            min.zCoord + (max.zCoord - min.zCoord) / 2
        )
    }

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
            .add("min", min)
            .add("max", max)
            .toString()
    }

    companion object {
        @JvmStatic
        fun mergeIdentity(): BoundingBox {
            return BoundingBox(POSITIVE_INFINITY, NEGATIVE_INFINITY, POSITIVE_INFINITY, NEGATIVE_INFINITY, POSITIVE_INFINITY, NEGATIVE_INFINITY)
        }
    }

}
