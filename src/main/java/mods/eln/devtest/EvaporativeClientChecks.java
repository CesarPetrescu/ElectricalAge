package mods.eln.devtest;

import mods.eln.Eln;
import mods.eln.GuiHandler;
import mods.eln.node.transparent.TransparentNodeEntity;
import mods.eln.transparentnode.evaporative.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Opt-in real-client interaction checks: native renderer, menu packets, controls and screenshots. */
public final class EvaporativeClientChecks {
    private final ContractReport report = new ContractReport("evaporative-client");
    private int phase, ticks, totalTicks;
    private float previousAngle;
    private boolean finished;
    public static void register() { NeoForge.EVENT_BUS.register(new EvaporativeClientChecks()); }
    @SubscribeEvent public void tick(ClientTickEvent.Post event) {
        if (finished) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (++totalTicks > 6000) throw new IllegalStateException("Timed out waiting for native client");
            if (phase == 0) {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay()!=null) return;
                if (++ticks < 20) return;
                report.write(false);
                mc.createWorldOpenFlows().openWorld("evaporative", () -> { throw new IllegalStateException("Could not open fixture world"); });
                phase=1; ticks=0;
            } else if (phase == 1) {
                if (mc.player==null || mc.level==null || mc.screen!=null) return;
                if (++ticks < 40) return;
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                // Use a typical scaled viewport for native layout validation. This is a test display setting, not a global gameplay change.
                mc.options.guiScale().set(2); mc.resizeDisplay();
                mc.getSingleplayerServer().execute(() -> {
                    var server=mc.getSingleplayerServer(); var p=server.getPlayerList().getPlayers().get(0);
                    p.setGameMode(GameType.CREATIVE);
                    p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                    server.overworld().setDayTime(6000);
                    EvaporativeSmokeTest.prepareClient(server.overworld());
                    EvaporativeSmokeTest.prepareGallery(server.overworld());
                    p.teleportTo(p.serverLevel(),28.7,80.1,35.4,-132f,12f);
                });
                phase=2; ticks=0;
            } else if (phase == 2) {
                if (mc.player.getAbilities().mayfly && !mc.player.getAbilities().flying) {
                    mc.player.getAbilities().flying=true; mc.player.onUpdateAbilities();
                }
                if (++ticks<100) return;
                check("native-model-parts-and-live-fan", () -> {
                    var renderer=renderer(mc);
                    for(String name:new String[]{"main","rotor","pad_wet","pad_dry","water"}) {
                        if (Eln.obj.getObj("evaporativecooler").getPart(name)==null) throw new AssertionError("Missing model part "+name);
                    }
                    if (value(renderer,"targetSpeed")<.9f || value(renderer,"waterLevel")<=0) throw new AssertionError("No real fan/water update");
                    previousAngle=value(renderer,"angle");
                });
                shot(mc,"evaporative-world"); phase=3; ticks=0;
            } else if (phase == 3) {
                if (++ticks<13) return;
                check("animated-fan-advances-between-rendered-frames", () -> {
                    if(Math.abs(value(renderer(mc),"angle")-previousAngle)<.01) throw new AssertionError("Fan angle is static");
                });
                shot(mc,"evaporative-world-spin");
                mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().getPlayerList().getPlayers().get(0)
                    .setItemInHand(InteractionHand.MAIN_HAND,Eln.findItemStack(EvaporativeSmokeTest.NAME,1)));
                phase=9; ticks=0;
            } else if (phase == 9) {
                if (++ticks<30) return;
                check("native-held-item-model", () -> {
                    if(mc.player.getMainHandItem().isEmpty()) throw new AssertionError("No real held item for rendering");
                });
                shot(mc,"evaporative-held-model");
                mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().getPlayerList().getPlayers().get(0)
                    .setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY));
                phase=10; ticks=0;
            } else if (phase == 10) {
                if (++ticks<20) return;
                BlockPos pos=EvaporativeSmokeTest.ACTIVE;
                var hit=new BlockHitResult(new Vec3(pos.getX(),pos.getY()+.5,pos.getZ()+.5),Direction.WEST,pos,false);
                mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);
                phase=4; ticks=0;
            } else if (phase == 4) {
                if (++ticks<50) return;
                check("native-menu-open-and-server-telemetry", () -> {
                    var menu=menu(mc);
                    for (var child : mc.screen.children()) {
                        if (child instanceof Button b && (b.getX()<0 || b.getY()<0 || b.getX()+b.getWidth()>mc.screen.width || b.getY()+b.getHeight()>mc.screen.height))
                            throw new AssertionError("UI button clipped outside the viewport");
                    }
                    if (menu.getValues().get(0)!=3 || menu.getValues().get(9)<=0 || menu.getValues().get(10)<100 || menu.getValues().get(15)<500)
                        throw new AssertionError("Telemetry not synchronized from live machine");
                });
                shot(mc,"evaporative-ui-wet");
                press(mc,"Dry"); phase=5; ticks=0;
            } else if (phase == 5) {
                if (++ticks<30) return;
                check("dry-button-roundtrip-disables-water", () -> {
                    var d=menu(mc).getValues();
                    if(d.get(0)!=1 || d.get(15)!=0 || d.get(10)<100) throw new AssertionError("Dry mode did not reach server");
                });
                shot(mc,"evaporative-ui-dry");
                press(mc,"+5"); press(mc,"-10"); phase=6; ticks=0;
            } else if (phase == 6) {
                if (++ticks<30) return;
                check("target-and-fan-buttons-roundtrip", () -> {
                    var d=menu(mc).getValues();
                    if(d.get(1)!=45 || d.get(2)!=90) throw new AssertionError("Target/fan changes not synchronized");
                });
                // Use the visible redstone button, not a direct state mutation.
                mc.screen.children().stream().filter(x->x instanceof Button b && b.getMessage().getString().startsWith("Redstone:"))
                    .map(x->(Button)x).findFirst().orElseThrow().onPress();
                phase=7; ticks=0;
            } else if (phase == 7) {
                if (++ticks<30) return;
                check("redstone-menu-interlock-roundtrip", () -> {
                    var d=menu(mc).getValues();
                    if(d.get(3)!=1 || d.get(4)!=EvaporativeStatus.REDSTONE) throw new AssertionError("No redstone interlock");
                });
                shot(mc,"evaporative-ui-interlock");
                // Unknown command must not mutate any configured field, even via native menu packet.
                mc.gameMode.handleInventoryButtonClick(menu(mc).containerId,9999);
                phase=8; ticks=0;
            } else if (phase == 8) {
                if (++ticks<20) return;
                check("invalid-menu-command-ignored", () -> {
                    var d=menu(mc).getValues();
                    if(d.get(0)!=1 || d.get(1)!=45 || d.get(2)!=90 || d.get(3)!=1) throw new AssertionError("Unknown command changed state");
                });
                press(mc,"Details"); phase=11; ticks=0;
            } else if (phase == 11) {
                if (++ticks<20) return;
                check("details-preserves-menu-and-settings", () -> {
                    var d=menu(mc).getValues();
                    if(d.get(0)!=1 || d.get(1)!=45 || d.get(2)!=90 || d.get(3)!=1) throw new AssertionError("Page navigation changed server state");
                    requireButton(mc,"Back");
                });
                shot(mc,"evaporative-ui-details"); press(mc,"Back"); phase=12; ticks=0;
            } else if (phase == 12) {
                if (++ticks<20) return;
                check("back-restores-native-controls", () -> { requireButton(mc,"Dry"); requireButton(mc,"Details"); });
                mc.getWindow().setWindowed(640,480); mc.options.guiScale().set(2); mc.resizeDisplay();
                phase=13; ticks=0;
            } else if (phase == 13) {
                if (++ticks<40) return;
                check("minimum-320x240-scaled-viewport", () -> checkLayout(mc));
                shot(mc,"evaporative-ui-small");
                mc.options.guiScale().set(1); mc.resizeDisplay();phase=14;ticks=0;
            } else if (phase == 14) {
                if (++ticks<30) return;
                check("gui-scale-one", () -> checkLayout(mc));shot(mc,"evaporative-ui-scale1");
                mc.getWindow().setWindowed(1280,960);mc.options.guiScale().set(3);mc.resizeDisplay();phase=15;ticks=0;
            } else if (phase == 15) {
                if (++ticks<40) return;
                check("gui-scale-three", () -> checkLayout(mc));shot(mc,"evaporative-ui-scale3");
                var b=requireButton(mc,"+1");
                mc.screen.mouseClicked(b.getX()+b.getWidth()/2.0,b.getY()+b.getHeight()/2.0,0);
                mc.screen.mouseReleased(b.getX()+b.getWidth()/2.0,b.getY()+b.getHeight()/2.0,0);
                phase=16;ticks=0;
            } else if (phase == 16) {
                if (++ticks<20) return;
                check("actual-mouse-hit-after-resize", () -> {
                    if(menu(mc).getValues().get(1)!=46) throw new AssertionError("Resized button hitbox missed command");
                });
                mc.player.closeContainer();
                teleport(mc,45.5,81.8,37.5,-141f,21f);phase=17;ticks=0;
            } else if (phase == 17) {
                if (++ticks<70) return;
                check("all-three-native-family-renderers", () -> {
                    for(int x:new int[]{48,50,52}) {
                        if(!(mc.level.getBlockEntity(new BlockPos(x,80,32)) instanceof TransparentNodeEntity e) || e.getElementRender()==null)
                            throw new AssertionError("Gallery renderer missing at "+x);
                    }
                });
                shot(mc,"evaporative-family");teleport(mc,45,83,31,-139f,25f);phase=18;ticks=0;
            } else if (phase == 18) {
                if (++ticks<60) return;
                check("four-orientations-and-reservoir-levels", () -> {
                    for(int i=0;i<4;i++) {
                        var entity=(TransparentNodeEntity)mc.level.getBlockEntity(new BlockPos(48+i*2,80,24));
                        if(!(entity.getElementRender() instanceof EvaporativeCoolerRender r) || Math.abs(value(r,"waterLevel")-(i+1)*.25f)>.01f)
                            throw new AssertionError("Orientation/level gallery missing state");
                    }
                });
                shot(mc,"evaporative-orientations");finish(mc);
            }
        } catch (Throwable t) {
            check("client-phase-"+phase, () -> { throw new AssertionError(t); });
            finish(mc);
        }
    }
    private void check(String name,Runnable body) { report.test("EC-240",name,()->{body.run();return kotlin.Unit.INSTANCE;}); report.write(false); }
    private EvaporativeCoolerRender renderer(Minecraft mc) {
        if (mc.level.getBlockEntity(EvaporativeSmokeTest.ACTIVE) instanceof TransparentNodeEntity entity && entity.getElementRender() instanceof EvaporativeCoolerRender r) return r;
        throw new AssertionError("Native evaporative renderer missing");
    }
    private float value(Object object,String name) {
        try { var f=object.getClass().getDeclaredField(name); f.setAccessible(true); return f.getFloat(object); }
        catch(ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private EvaporativeCoolerMenu menu(Minecraft mc) {
        if(mc.screen instanceof EvaporativeCoolerScreen s && mc.player.containerMenu==s.getMenu()) return s.getMenu();
        throw new AssertionError("Expected native EC-240 screen, got "+mc.screen);
    }
    private Button requireButton(Minecraft mc,String label) {
        return mc.screen.children().stream().filter(x->x instanceof Button).map(x->(Button)x)
            .filter(x->x.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private void press(Minecraft mc,String label) {
        var b=requireButton(mc,label);
        if(!b.active) throw new AssertionError("Disabled button "+label);b.onPress();
    }
    private void checkLayout(Minecraft mc) {
        menu(mc);
        if(mc.screen.width<248 || mc.screen.height<228) throw new AssertionError("Panel exceeds viewport");
        for(var child:mc.screen.children()) {
            if(child instanceof Button b && (b.getX()<0 || b.getY()<0 || b.getX()+b.getWidth()>mc.screen.width || b.getY()+b.getHeight()>mc.screen.height))
                throw new AssertionError("Clipped button "+b.getMessage().getString());
        }
        requireButton(mc,"Details");requireButton(mc,"+5");requireButton(mc,"-10");
    }
    private void teleport(Minecraft mc,double x,double y,double z,float yaw,float pitch) {
        mc.getSingleplayerServer().execute(() -> {
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
            p.teleportTo(p.serverLevel(),x,y,z,yaw,pitch);
        });
    }
    private void shot(Minecraft mc,String name) { Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),m->Eln.LOGGER.info("EC-240 screenshot {}",m.getString())); }
    private void finish(Minecraft mc) { finished=true; report.write(true); if(report.getFailures()>0) System.exit(1); else mc.stop(); }
}
