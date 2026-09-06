package mods.eln.node.six;

import mods.eln.misc.Direction;
import mods.eln.misc.UtilsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import org.lwjgl.opengl.GL11;

public class SixNodeRender extends TileEntitySpecialRenderer<SixNodeEntity> {

    @Override
    public void render(SixNodeEntity tileEntity, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        Minecraft.getMinecraft().profiler.startSection("SixNode");

        GL11.glPushMatrix();
        GL11.glTranslatef((float) x + .5F, (float) y + .5F, (float) z + .5F);

        int idx = 0;
        for (SixNodeElementRender render : tileEntity.elementRenderList) {
            if (render != null) {
                UtilsClient.glDefaultColor();
                GL11.glPushMatrix();
                Direction.fromInt(idx).glRotateXnRef();
                GL11.glTranslatef(-0.5F, 0f, 0f);
                render.draw();
                GL11.glPopMatrix();
            }
            idx++;
        }
        UtilsClient.glDefaultColor();
        GL11.glPopMatrix();
        Minecraft.getMinecraft().profiler.endSection();
    }
}
