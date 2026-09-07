package mods.eln.lighting

import mods.eln.client.gl.MeshNormals
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeshNormalsTest {
    @Test fun cachedRotationAlsoRotatesLightingNormals() {
        val recorded = Matrix4f().rotateY((Math.PI / 2).toFloat())
        val normal = MeshNormals.transform(MeshNormals.matrix(Matrix3f(), recorded), 0f, 0f, 1f)
        assertEquals(1f, normal.x, 1e-6f)
        assertEquals(0f, normal.y, 1e-6f)
        assertEquals(0f, normal.z, 1e-6f)
    }

    @Test fun nonUniformScaleKeepsNormalsPerpendicularToTheSurface() {
        val outer = Matrix4f().rotateX(.4f).scale(2f, 1f, .5f)
        val recorded = Matrix4f().rotateY(.8f).scale(.7f, 3f, 1.2f)
        val model = Matrix4f(outer).mul(recorded)
        val matrix = MeshNormals.matrix(Matrix3f(outer).invert().transpose(), recorded)
        val normal = MeshNormals.transform(matrix, 0f, 0f, 1f)
        assertEquals(0f, normal.dot(model.transformDirection(Vector3f(1f, 0f, 0f))), 1e-6f)
        assertEquals(0f, normal.dot(model.transformDirection(Vector3f(0f, 1f, 0f))), 1e-6f)
        assertEquals(1f, normal.length(), 1e-6f)
    }

    @Test fun collapsedOrDegenerateFacesNeverProduceInvalidShaderNormals() {
        val matrix = MeshNormals.matrix(Matrix3f(), Matrix4f().scale(0f))
        for (normal in listOf(Vector3f(), Vector3f(Float.NaN, 0f, 0f), Vector3f(0f, 1f, 0f))) {
            val result = MeshNormals.transform(matrix, normal.x, normal.y, normal.z)
            assertTrue(result.isFinite)
            assertEquals(1f, result.length(), 1e-6f)
        }
    }
}
