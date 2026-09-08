package mods.eln.devtest;

import com.google.gson.Gson;
import mods.eln.Eln;
import mods.eln.GuiHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Real server-open protocol, legacy/vanilla controls, panel and inventory bounds and screenshots.
 * Opening a menu or preserving its controls does not establish every control's operational semantics. */
@EventBusSubscriber(modid = Eln.MODID, value = Dist.CLIENT)
public final class AllMenusClientChecks {
    private static final ContractReport REPORT = new ContractReport("all-menus-client");
    private static final List<Target> TARGETS = new ArrayList<>();
    private static final int SHARD = Integer.getInteger("eln.menuShard",0);
    private static int phase, ticks, totalTicks, index, size;
    private static boolean finished;
    private static class Target { String id,kind; int x,y,z,side; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!"menus".equals(System.getProperty("eln.campaign")) || finished) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (++totalTicks > 18000) throw new IllegalStateException("Native all-menu client timed out");
            if (phase == 0) {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay()!=null || ++ticks<20) return;
                Target[] all = new Gson().fromJson(Files.readString(mc.gameDirectory.toPath().resolve("saves/menu-audit/menu-targets.json")),Target[].class);
                for (int i=0;i<all.length;i++) if(i%4==SHARD) TARGETS.add(all[i]);
                if(TARGETS.isEmpty()) throw new AssertionError("No menu targets for shard "+SHARD);
                REPORT.write(false);
                mc.createWorldOpenFlows().openWorld("menu-audit",()->{throw new IllegalStateException("Cannot open menu fixture world");});
                phase=1;ticks=0;
            } else if (phase==1) {
                if(mc.player==null || mc.level==null || mc.screen!=null || ++ticks<40) return;
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                mc.options.guiScale().set(2);mc.getWindow().setWindowed(1280,960);mc.resizeDisplay();
                phase=2;ticks=0;
            } else if(phase==2) {
                if(ticks++==0) {
                    Target target=TARGETS.get(index);
                    System.out.println("NATIVE_MENU_BEGIN "+target.id+" shard="+SHARD);
                    mc.getSingleplayerServer().execute(()->{
                        var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayers().get(0);
                        p.setGameMode(GameType.CREATIVE);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                        p.teleportTo(server.overworld(),target.x+2.0,target.y+1.0,target.z+2.0,135f,15f);
                        p.getAbilities().flying=true;p.onUpdateAbilities();
                    });
                }
                var t=TARGETS.get(index);
                if(ticks<60 || mc.level.getBlockEntity(new BlockPos(t.x,t.y,t.z))==null) return;
                mc.getSingleplayerServer().execute(()->{
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                    GuiHandler.open(p,GuiHandler.nodeBaseOpen+t.side,p.serverLevel(),t.x,t.y,t.z);
                });
                phase=3;ticks=0;size=0;
            } else if(phase==3) {
                if(++ticks<40) return;
                var t=TARGETS.get(index);
                String suffix=size==0?"normal":"minimum";
                check(t.id,"native-screen-open-"+suffix,()->{
                    if(mc.screen==null || mc.screen instanceof TitleScreen) throw new AssertionError("Advertised GUI did not open");
                    System.out.println("NATIVE_MENU_SCREEN "+t.id+" "+mc.screen.getClass().getName()+" "+mc.screen.width+"x"+mc.screen.height);
                });
                check(t.id,"controls-panel-slots-and-resize-"+suffix,()->MenuGeometryChecks.verify(mc.screen,size==0));
                Screenshot.grab(mc.gameDirectory,"menu-"+t.id.replace(':','-')+"-"+suffix+".png",mc.getMainRenderTarget(),m->Eln.LOGGER.info("Native menu screenshot {}",m.getString()));
                if(size++==0) {
                    mc.getWindow().setWindowed(640,480);mc.resizeDisplay();ticks=0;
                } else {phase=4;ticks=0;}
            } else if(phase==4) {
                if(++ticks<20) return;
                if(mc.screen!=null) mc.screen.onClose();
                if(mc.player!=null)mc.player.closeContainer();
                if(++index==TARGETS.size()) {
                    REPORT.write(true);finished=true;
                    if(REPORT.getFailures()>0)System.exit(1);else mc.stop();
                    return;
                }
                mc.getWindow().setWindowed(1280,960);mc.resizeDisplay();phase=2;ticks=0;
            }
        } catch(Throwable error) {
            check(index<TARGETS.size()?TARGETS.get(index).id:"campaign","unexpected-phase-"+phase,()->{throw new AssertionError(error);});
            REPORT.write(false);finished=true;System.exit(1);
        }
    }
    private static void check(String id,String name,Runnable body) {
        REPORT.test(id,name,()->{body.run();return kotlin.Unit.INSTANCE;});REPORT.write(false);
    }
}
