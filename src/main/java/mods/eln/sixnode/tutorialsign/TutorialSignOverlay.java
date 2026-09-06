package mods.eln.sixnode.tutorialsign;

import mods.eln.misc.McBridge;
import net.neoforged.bus.api.SubscribeEvent;
import mods.eln.misc.Utils;
import mods.eln.node.six.SixNodeBlock;
import mods.eln.node.six.SixNodeElementRender;
import mods.eln.node.six.SixNodeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.minecraft.client.gui.GuiGraphics;

public class TutorialSignOverlay {

    TutorialSignRender oldRender = null;

    public TutorialSignOverlay() {
        int i = 0;
        i++;
    }

    @SubscribeEvent
    /** 1.7.10 drew the sign text in the overlay "text" pass; 1.21 draws HUD text on the GuiGraphics after the HUD. */
    public void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (oldRender != null) {
            oldRender.lightInterpol.setTarget(0);
            oldRender = null;
        }

        int px = Mth.floor(player.getX()), py = Mth.floor(player.getY()), pz = Mth.floor(player.getZ());
        int r = 1;
        Level w = player.level();

        TutorialSignRender best = null;
        double bestDistance = 10000;

        for (int x = px - r; x <= px + r; x++) {
            for (int y = py - r; y <= py + r; y++) {
                for (int z = pz - r; z <= pz + r; z++) {
                    if (McBridge.getBlock(w, x, y, z) instanceof SixNodeBlock) {
                        BlockEntity e = McBridge.getBlockEntity(w, x, y, z);
                        if (e instanceof SixNodeEntity) {
                            SixNodeEntity sne = (SixNodeEntity) e;
                            for (SixNodeElementRender render : sne.elementRenderList) {
                                if (render instanceof TutorialSignRender) {
                                    double d = Utils.getLength(player.getX(), player.getY(), player.getZ(), x + 0.5, y + 0.5, z + 0.5);
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
            GuiGraphics graphics = event.getGuiGraphics();
            graphics.pose().pushPose();
            graphics.pose().scale(0.5f, 0.5f, 0.5f);
            int y = 0;
            for (String str : best.texts) {
                graphics.drawString(Minecraft.getInstance().font, str, 10/* event.resolution.getScaledWidth() / 2 - 50*/, 10 + y, 0xFFFFFF, false);
                y += 10;
            }
            graphics.pose().popPose();
        }
    }
}
