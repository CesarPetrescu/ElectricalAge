package mods.eln.entity

import net.minecraft.client.model.Model
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.world.entity.Entity

class ReplicatorModel : Model() {
    private val head: ModelPart
    private val body: ModelPart
    private val rearEnd: ModelPart
    private val leg8: ModelPart
    private val leg6: ModelPart
    private val leg4: ModelPart
    private val leg2: ModelPart
    private val leg7: ModelPart
    private val leg5: ModelPart
    private val leg3: ModelPart
    private val leg1: ModelPart

    init {
        textureWidth = 64
        textureHeight = 64

        head = ModelPart(this, 32, 4)
        head.addBox(-4.0f, -4.0f, -8.0f, 8, 8, 8)
        head.setRotationPoint(0.0f, 20.0f, -3.0f)
        head.setTextureSize(64, 64)
        head.mirror = true
        setRotation(head, 0.0f, 0.0f, 0.0f)

        body = ModelPart(this, 0, 0)
        body.addBox(-3.0f, -3.0f, -3.0f, 6, 6, 6)
        body.setRotationPoint(0.0f, 20.0f, 0.0f)
        body.setTextureSize(64, 64)
        body.mirror = true
        setRotation(body, 0.0f, 0.0f, 0.0f)

        rearEnd = ModelPart(this, 0, 12)
        rearEnd.addBox(-5.0f, -4.0f, -6.0f, 10, 8, 12)
        rearEnd.setRotationPoint(0.0f, 20.0f, 9.0f)
        rearEnd.setTextureSize(64, 64)
        rearEnd.mirror = true
        setRotation(rearEnd, 0.0f, 0.0f, 0.0f)

        leg8 = ModelPart(this, 18, 0)
        leg8.addBox(-1.0f, -1.0f, -1.0f, 16, 2, 2)
        leg8.setRotationPoint(4.0f, 20.0f, -1.0f)
        leg8.setTextureSize(64, 64)
        leg8.mirror = true
        setRotation(leg8, 0.0f, 0.5759587f, 0.1919862f)

        leg6 = ModelPart(this, 18, 0)
        leg6.addBox(-1.0f, -1.0f, -1.0f, 16, 2, 2)
        leg6.setRotationPoint(4.0f, 20.0f, 0.0f)
        leg6.setTextureSize(64, 64)
        leg6.mirror = true
        setRotation(leg6, 0.0f, 0.2792527f, 0.1919862f)

        leg4 = ModelPart(this, 18, 0)
        leg4.addBox(-1.0f, -1.0f, -1.0f, 16, 2, 2)
        leg4.setRotationPoint(4.0f, 20.0f, 1.0f)
        leg4.setTextureSize(64, 64)
        leg4.mirror = true
        setRotation(leg4, 0.0f, -0.2792527f, 0.1919862f)

        leg2 = ModelPart(this, 18, 0)
        leg2.addBox(-1.0f, -1.0f, -1.0f, 16, 2, 2)
        leg2.setRotationPoint(4.0f, 20.0f, 2.0f)
        leg2.setTextureSize(64, 64)
        leg2.mirror = true
        setRotation(leg2, 0.0f, -0.5759587f, 0.1919862f)

        leg7 = ModelPart(this, 18, 0)
        leg7.addBox(-15.0f, -1.0f, -1.0f, 16, 2, 2)
        leg7.setRotationPoint(-4.0f, 20.0f, -1.0f)
        leg7.setTextureSize(64, 64)
        leg7.mirror = true
        setRotation(leg7, 0.0f, -0.5759587f, -0.1919862f)

        leg5 = ModelPart(this, 18, 0)
        leg5.addBox(-15.0f, -1.0f, -1.0f, 16, 2, 2)
        leg5.setRotationPoint(-4.0f, 20.0f, 0.0f)
        leg5.setTextureSize(64, 64)
        leg5.mirror = true
        setRotation(leg5, 0.0f, -0.2792527f, -0.1919862f)

        leg3 = ModelPart(this, 18, 0)
        leg3.addBox(-15.0f, -1.0f, -1.0f, 16, 2, 2)
        leg3.setRotationPoint(-4.0f, 20.0f, 1.0f)
        leg3.setTextureSize(64, 64)
        leg3.mirror = true
        setRotation(leg3, 0.0f, 0.2792527f, -0.1919862f)

        leg1 = ModelPart(this, 18, 0)
        leg1.addBox(-15.0f, -1.0f, -1.0f, 16, 2, 2)
        leg1.setRotationPoint(-4.0f, 20.0f, 2.0f)
        leg1.setTextureSize(64, 64)
        leg1.mirror = true
        setRotation(leg1, 0.0f, 0.5759587f, -0.1919862f)
    }

    override fun render(entity: Entity, f: Float, f1: Float, f2: Float, f3: Float, f4: Float, f5: Float) {
        super.render(entity, f, f1, f2, f3, f4, f5)
        head.render(f5)
        body.render(f5)
        rearEnd.render(f5)
        leg8.render(f5)
        leg6.render(f5)
        leg4.render(f5)
        leg2.render(f5)
        leg7.render(f5)
        leg5.render(f5)
        leg3.render(f5)
        leg1.render(f5)
    }

    private fun setRotation(model: ModelPart, x: Float, y: Float, z: Float) {
        model.rotateAngleX = x
        model.rotateAngleY = y
        model.rotateAngleZ = z
    }
}
