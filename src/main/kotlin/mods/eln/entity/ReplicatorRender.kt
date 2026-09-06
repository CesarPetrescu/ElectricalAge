package mods.eln.entity

import net.minecraft.client.model.SilverfishModel
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.MobRenderer
import net.minecraft.resources.ResourceLocation

/** Upstream draws the replicator with vanilla's silverfish model and its own texture. */
class ReplicatorRender(context: EntityRendererProvider.Context, model: SilverfishModel<ReplicatorEntity>, shadowSize: Float) :
    MobRenderer<ReplicatorEntity, SilverfishModel<ReplicatorEntity>>(context, model, shadowSize) {

    override fun getTextureLocation(entity: ReplicatorEntity): ResourceLocation = RES

    companion object {
        private val RES = ResourceLocation.fromNamespaceAndPath("eln", "textures/entity/replicator.png")
    }
}
