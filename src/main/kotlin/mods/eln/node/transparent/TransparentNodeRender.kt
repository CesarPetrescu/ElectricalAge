package mods.eln.node.transparent

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import org.lwjgl.opengl.GL11

class TransparentNodeRender : BlockEntityRenderer<TransparentNodeEntity>() {
    override fun render(entity: TransparentNodeEntity, x: Double, y: Double, z: Double, partialTicks: Float, destroyStage: Int, alpha: Float) {
        val tileEntity = entity
        if (tileEntity.elementRender == null) return
        GL11.glPushMatrix()
        GL11.glTranslatef(x.toFloat() + .5f, y.toFloat() + .5f, z.toFloat() + .5f)
        tileEntity.elementRender!!.draw()
        GL11.glPopMatrix()
    }
}
