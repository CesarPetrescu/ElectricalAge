package mods.eln.node.transparent

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.client.gl.FixedFunction
import mods.eln.client.gl.GL11
import mods.eln.node.NodeRenderSupport
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** Draws a transparent node's element through the fixed-function emulator (see mods.eln.client.gl). */
class TransparentNodeRender : BlockEntityRenderer<TransparentNodeEntity> {
    override fun render(entity: TransparentNodeEntity, partialTicks: Float, poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int, packedOverlay: Int) {
        val elementRender = entity.elementRender ?: return
        FixedFunction.begin(poseStack, buffer, packedLight, packedOverlay)
        try {
            GL11.glPushMatrix()
            GL11.glTranslatef(.5f, .5f, .5f)
            elementRender.draw()
            GL11.glPopMatrix()
        } finally {
            FixedFunction.end()
        }
    }

    override fun getRenderBoundingBox(entity: TransparentNodeEntity): AABB = entity.getRenderBoundingBox()

    override fun getViewDistance(): Int = NodeRenderSupport.viewDistance()

    override fun shouldRender(entity: TransparentNodeEntity, cameraPos: Vec3): Boolean = NodeRenderSupport.shouldRender(entity, cameraPos, viewDistance)
}
