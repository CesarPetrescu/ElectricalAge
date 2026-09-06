package mods.eln.sixnode.tutorialsign;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import mods.eln.misc.Utils;
import mods.eln.node.six.SixNodeBlock;
import mods.eln.node.six.SixNodeElementRender;
import mods.eln.node.six.SixNodeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;

public class TutorialSignOverlay {

    TutorialSignRender oldRender = null;

    public TutorialSignOverlay() {
        int i = 0;
        i++;
    }

    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        LocalPlayer player = mc.player;

        if (oldRender != null) {
            oldRender.lightInterpol.setTarget(0);
            oldRender = null;
        }

        int px = Mth.floor(player.posX), py = Mth.floor(player.posY), pz = Mth.floor(player.posZ);
        int r = 1;
        Level w = player.world;

        TutorialSignRender best = null;
        double bestDistance = 10000;

        for (int x = px - r; x <= px + r; x++) {
            for (int y = py - r; y <= py + r; y++) {
                for (int z = pz - r; z <= pz + r; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (w.getBlockState(pos).getBlock() instanceof SixNodeBlock) {
                        BlockEntity e = w.getTileEntity(pos);
                        if (e instanceof SixNodeEntity) {
                            SixNodeEntity sne = (SixNodeEntity) e;
                            for (SixNodeElementRender render : sne.elementRenderList) {
                                if (render instanceof TutorialSignRender) {
                                    double d = Utils.getLength(player.posX, player.posY, player.posZ, x + 0.5, y + 0.5, z + 0.5);
                                    if (d < bestDistance) {
                                        bestDistance = d;
                                        best = (TutorialSignRender) render;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (best != null) {
            oldRender = best;
            oldRender.lightInterpol.setTarget(1f);
            GL11.glPushMatrix();
            GL11.glScalef(0.5f, 0.5f, 0.5f);
            int y = 0;
            for (String str : best.texts) {
                Minecraft.getMinecraft().fontRenderer.drawString(str, 10/* event.resolution.getScaledWidth() / 2 - 50*/, 10 + y, 0xFFFFFF);
                y += 10;
            }
            GL11.glPopMatrix();
        }
    }
}
