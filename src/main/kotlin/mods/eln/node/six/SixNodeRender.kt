package mods.eln.node.six

import mods.eln.misc.Direction.Companion.fromInt
import mods.eln.misc.UtilsClient.glDefaultColor
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import org.lwjgl.opengl.GL11

class SixNodeRender : BlockEntityRenderer<SixNodeEntity>() {
    override fun render(entity: SixNodeEntity, x: Double, y: Double, z: Double, partialTicks: Float, destroyStage: Int, alpha: Float) {
        Minecraft.getInstance().profiler.startSection("SixNode")
        val tileEntity = entity
        GL11.glPushMatrix()
        GL11.glTranslatef(x.toFloat() + .5f, y.toFloat() + .5f, z.toFloat() + .5f)
        for ((idx, render) in tileEntity.elementRenderList.withIndex()) {
            if (render != null) {
                glDefaultColor()
                GL11.glPushMatrix()
                fromInt(idx)!!.glRotateXnRef()
                GL11.glTranslatef(-0.5f, 0f, 0f)
                render.draw()
                GL11.glPopMatrix()
            }
        }
        GL11.glPopMatrix()
        Minecraft.getInstance().profiler.endSection()
    }
}
