package mods.eln.node.six

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.client.gl.FixedFunction
import mods.eln.client.gl.GL11
import mods.eln.misc.Direction.Companion.fromInt
import mods.eln.misc.UtilsClient.glDefaultColor
import mods.eln.node.NodeRenderSupport
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** Draws the six faces of a six-node through the fixed-function emulator (see mods.eln.client.gl). */
class SixNodeRender : BlockEntityRenderer<SixNodeEntity> {
    override fun render(entity: SixNodeEntity, partialTicks: Float, poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int, packedOverlay: Int) {
        Minecraft.getInstance().profiler.push("SixNode")
        FixedFunction.begin(poseStack, buffer, NodeRenderSupport.neighbourLight(entity), packedOverlay)
        try {
            GL11.glPushMatrix()
            GL11.glTranslatef(.5f, .5f, .5f)
            for ((idx, render) in entity.elementRenderList.withIndex()) {
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
        } finally {
            FixedFunction.finish()
        }
        Minecraft.getInstance().profiler.pop()
    }

    override fun getRenderBoundingBox(entity: SixNodeEntity): AABB = entity.getRenderBoundingBox()

    override fun getViewDistance(): Int = NodeRenderSupport.viewDistance()

    override fun shouldRender(entity: SixNodeEntity, cameraPos: Vec3): Boolean = NodeRenderSupport.shouldRender(entity, cameraPos, viewDistance)
}
