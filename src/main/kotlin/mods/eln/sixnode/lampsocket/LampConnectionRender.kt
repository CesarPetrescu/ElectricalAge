package mods.eln.sixnode.lampsocket

import mods.eln.client.gl.GL11
import mods.eln.misc.LRDU
import mods.eln.misc.UtilsClient
import net.minecraft.resources.ResourceLocation

/** Small compression gland: metal collar against the casing and a dark insulated cable entry. */
object LampConnectionRender {
    private val collar = ResourceLocation.withDefaultNamespace("textures/block/iron_block.png")
    private val insulation = ResourceLocation.withDefaultNamespace("textures/block/black_concrete.png")

    fun draw(side: LRDU, edge: Float, width: Float, height: Float) {
        GL11.glPushMatrix()
        val angle = when (side) { LRDU.Right -> 0f; LRDU.Up -> -90f; LRDU.Left -> 180f; LRDU.Down -> 90f }
        GL11.glRotatef(angle, 1f, 0f, 0f)
        val radius = (width / 2 + 0.018f).coerceAtLeast(0.045f)
        val centre = height / 2
        val end = LampConnections.cableEnd(edge)
        val shoulder = edge + (end - edge) * 0.42f
        UtilsClient.bindTexture(collar)
        box((centre - radius).coerceAtLeast(0.002f), centre + radius, -radius, radius, edge - 0.008f, shoulder)
        UtilsClient.bindTexture(insulation)
        val inner = radius * 0.8f
        box((centre - inner).coerceAtLeast(0.003f), centre + inner, -inner, inner, shoulder, end)
        GL11.glPopMatrix()
    }

    private fun box(x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float) {
        val vertices = arrayOf(floatArrayOf(x0,y0,z0), floatArrayOf(x1,y0,z0), floatArrayOf(x1,y1,z0),
            floatArrayOf(x0,y1,z0), floatArrayOf(x0,y0,z1), floatArrayOf(x1,y0,z1),
            floatArrayOf(x1,y1,z1), floatArrayOf(x0,y1,z1))
        val faces = arrayOf(intArrayOf(3,2,1,0), intArrayOf(4,5,6,7), intArrayOf(0,1,5,4),
            intArrayOf(7,6,2,3), intArrayOf(0,4,7,3), intArrayOf(2,6,5,1))
        val normals = arrayOf(floatArrayOf(0f,0f,-1f),floatArrayOf(0f,0f,1f),floatArrayOf(0f,-1f,0f),
            floatArrayOf(0f,1f,0f),floatArrayOf(-1f,0f,0f),floatArrayOf(1f,0f,0f))
        GL11.glBegin(GL11.GL_QUADS)
        for ((index, face) in faces.withIndex()) {
            val normal = normals[index]
            GL11.glNormal3f(normal[0], normal[1], normal[2])
            for ((corner, vertex) in face.withIndex()) {
                GL11.glTexCoord2f(if (corner == 1 || corner == 2) 1f else 0f, if (corner >= 2) 1f else 0f)
                val v = vertices[vertex]
                GL11.glVertex3f(v[0], v[1], v[2])
            }
        }
        GL11.glEnd()
    }
}
