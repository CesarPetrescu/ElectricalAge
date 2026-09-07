package mods.eln.client.gl

import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.abs

/** The normal transform must include the transform recorded inside a display list. */
object MeshNormals {
    @JvmStatic fun matrix(outer: Matrix3f, recorded: Matrix4f?): Matrix3f {
        val result = Matrix3f(outer)
        if (recorded != null) {
            val local = Matrix3f(recorded)
            // Collapsed animation geometry has no surface normal; do not inject NaNs into the shader.
            if (abs(local.determinant()) > 1e-12f) result.mul(local.invert().transpose())
        }
        return result
    }

    @JvmStatic fun transform(matrix: Matrix3f, x: Float, y: Float, z: Float, normal: Vector3f = Vector3f()): Vector3f {
        normal.set(x, y, z).mul(matrix)
        return if (normal.isFinite && normal.lengthSquared() > 1e-12f) normal.normalize() else normal.set(0f, 1f, 0f)
    }
}
