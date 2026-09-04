package mods.eln.entity

import net.minecraft.client.model.ModelBase
import net.minecraft.client.renderer.entity.RenderLiving
import net.minecraft.client.renderer.entity.RenderManager
import net.minecraft.util.ResourceLocation

/** RenderLiving is generic over its entity and takes the RenderManager since 1.8. */
class ReplicatorRender(manager: RenderManager, model: ModelBase, shadowSize: Float) :
    RenderLiving<ReplicatorEntity>(manager, model, shadowSize) {

    override fun getEntityTexture(entity: ReplicatorEntity): ResourceLocation = RES

    companion object {
        private val RES = ResourceLocation("eln:textures/entity/replicator.png")
    }
}
