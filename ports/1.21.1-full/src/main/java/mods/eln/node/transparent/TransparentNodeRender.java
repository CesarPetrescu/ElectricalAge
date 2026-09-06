package mods.eln.node.transparent;

import mods.eln.misc.UtilsClient;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import org.lwjgl.opengl.GL11;

public class TransparentNodeRender extends TileEntitySpecialRenderer<TransparentNodeEntity> {
    @Override
    public void render(TransparentNodeEntity tileEntity, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (tileEntity.elementRender == null) return;
        
        GL11.glPushMatrix();
        GL11.glTranslatef((float) x + .5F, (float) y + .5F, (float) z + .5F);
        UtilsClient.glDefaultColor();
        tileEntity.elementRender.draw();
        UtilsClient.glDefaultColor();
        GL11.glPopMatrix();
    }
}
