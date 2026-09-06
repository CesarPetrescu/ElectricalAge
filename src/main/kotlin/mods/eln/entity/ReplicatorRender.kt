package mods.eln.entity

import net.minecraft.client.model.Model
import net.minecraft.client.renderer.entity.MobRenderer
import net.minecraft.client.renderer.entity.EntityRenderDispatcher
import net.minecraft.resources.ResourceLocation

/** MobRenderer is generic over its entity and takes the EntityRenderDispatcher since 1.8. */
class ReplicatorRender(manager: EntityRenderDispatcher, model: Model, shadowSize: Float) :
    MobRenderer<ReplicatorEntity>(manager, model, shadowSize) {

    override fun getEntityTexture(entity: ReplicatorEntity): ResourceLocation = RES

    companion object {
        private val RES = ResourceLocation("eln:textures/entity/replicator.png")
    }
}
