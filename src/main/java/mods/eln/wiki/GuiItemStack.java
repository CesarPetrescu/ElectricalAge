package mods.eln.wiki;

import mods.eln.misc.McBridge;
import mods.eln.gui.GuiHelper;
import mods.eln.gui.IGuiObject;
import mods.eln.misc.UtilsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.client.gui.GuiGraphics;
import mods.eln.client.gl.RenderHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import mods.eln.client.gl.GL11;

import java.util.List;

public class GuiItemStack extends Gui implements IGuiObject {

    public GuiItemStack(int x, int y, ItemStack stack, GuiHelper helper) {
        this.getX() = x;
        this.getY() = y;
        h = 18;
        w = 18;
        this.stack = stack;
        this.helper = helper;
    }


    int posX, posY, h, w;
    ItemStack stack;

    public GuiHelper helper;
    static final ResourceLocation slotSkin = ResourceLocation.parse($1);


    @Override
    public void idraw(int x, int y, float f) {
        //RenderHelper.disableStandardItemLighting();
        try {
            GL11.glColor3f(1f, 1f, 1f);
            UtilsClient.bindTexture(slotSkin);
            drawTexturedModalRect(posX - 1, posY - 1, 55, 16, 73 - 55, 34 - 16);

            if (!McBridge.isNothing(stack)) {
                //	RenderHelper.enableStandardItemLighting();
                RenderHelper.enableStandardItemLighting();
                RenderHelper.enableGUIStandardItemLighting();

                UtilsClient.drawItemStack(stack, posX, posY, null, true);

                RenderHelper.disableStandardItemLighting();
                // GL11.glEnable(GL11.GL_LIGHTING);
                //  GL11.glEnable(GL11.GL_DEPTH_TEST);
                //  RenderHelper.enableStandardItemLighting();
            }
        } catch (Exception e) {
            // TODO: handle exception
        }


    }
    /*    GL11.glDisable(GL12.GL_RESCALE_NORMAL);
    RenderHelper.disableStandardItemLighting();
    GL11.glDisable(GL11.GL_LIGHTING);
  //  GL11.glDisable(GL11.GL_DEPTH_TEST);
    RenderHelper.enableGUIStandardItemLighting();
    
    short short1 = 240;
    short short2 = 240;
    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float)short1 / 1.0F, (float)short2 / 1.0F);

    GL11.glEnable(GL11.GL_LIGHTING);
    
    
  //  RenderHelper.enableStandardItemLighting();
  //  RenderHelper.disableStandardItemLighting();
 //   RenderHelper.enableStandardItemLighting();
 /*  GL11.glDepthFunc(GL11.GL_GREATER);
    GL11.glDisable(GL11.GL_LIGHTING);
    GL11.glDepthMask(false);
 //   par2TextureManager.func_110577_a(field_110798_h);
    this.zLevel -= 50.0F;
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_DST_COLOR, GL11.GL_DST_COLOR);
    GL11.glColor4f(0.5F, 0.25F, 0.8F, 1.0F);
  //  this.renderGlint(par4 * 431278612 + par5 * 32178161, par4 - 2, par5 - 2, 20, 20);
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glDepthMask(true);
    this.zLevel += 50.0F;
    GL11.glEnable(GL11.GL_LIGHTING);
    GL11.glDepthFunc(GL11.GL_LEQUAL);
	*/

    //RenderHelper.enableGUIStandardItemLighting();
    //RenderHelper.enableStandardItemLighting();
    //RenderHelper.disableStandardItemLighting();
    // RenderHelper.enableGUIStandardItemLighting();

    @Override
    public void idraw2(int x, int y) {
        if (McBridge.isNothing(stack)) return;
        if ((x >= posX && y >= posY && x < posX + w && y < posY + h)) {
            int px, py;
            px = posX;
            py = posY;
            List list = stack.getTooltip(Minecraft.getInstance().player, TooltipFlag.TooltipFlags.NORMAL);
            helper.drawHoveringText(list, x, y, Minecraft.getInstance().font);
        }
    }

    @Override
    public boolean ikeyTyped(char key, int code) {

        return false;
    }

    @Override
    public void imouseClicked(int x, int y, int code) {
        if (x >= posX && y >= posY && x < posX + w && y < posY + h) {
            if (!McBridge.isNothing(stack)) {
                UtilsClient.clientOpenGui(new ItemDefault(stack, helper.screen));
            }
			/*if(observer != null){
				observer.guiObjectEvent(this);
			}*/
        }

    }

    @Override
    public void imouseMove(int x, int y) {


    }

    @Override
    public void imouseMovedOrUp(int x, int y, int witch) {


    }

    @Override
    public void translate(int x, int y) {

        this.getX() += x;
        this.getY() += y;
    }


    @Override
    public int getYMax() {

        return posY + h;
    }


}
