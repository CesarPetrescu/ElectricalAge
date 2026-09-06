package mods.eln.entity;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;

public class ReplicatorRender extends MobRenderer {

    private static final ResourceLocation res = new ResourceLocation("eln:textures/entity/replicator.png");

    public ReplicatorRender(EntityRenderDispatcher rendermanagerIn, ModelBase modelbaseIn, float shadowsizeIn) {
        super(rendermanagerIn, modelbaseIn, shadowsizeIn);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return res;
    }
}
