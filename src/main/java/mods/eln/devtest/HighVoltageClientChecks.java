package mods.eln.devtest;

import com.google.gson.Gson;
import mods.eln.Eln;
import mods.eln.GuiHandler;
import mods.eln.gui.GuiTextFieldEln;
import mods.eln.misc.Coordinate;
import mods.eln.node.NodeManager;
import mods.eln.node.transparent.TransparentNode;
import mods.eln.node.transparent.TransparentNodeElement;
import mods.eln.transparentnode.*;
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor;
import mods.eln.sixnode.electricalcable.UtilityCableMaterial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Opt-in native UI tests: real clicks/typing, packet round trips and server-thread observations.
 * Reflection only locates existing widgets; it never invokes a production packet handler.
 * The old world is a disposable copy. These tests do not replace powered-circuit acceptance.
 */
@EventBusSubscriber(modid = Eln.MODID, value = Dist.CLIENT)
public final class HighVoltageClientChecks {
    private static final String CAMPAIGN = "hv-native-controls";
    private static final Set<String> REQUIRED = Set.of(
        "eln:variable_dc-dc_converter", "eln:one-way_dc-dc_converter",
        "eln:one-way_boost_vdc_dc_converter", "eln:one-way_buck_vdc_dc_converter",
        "eln:one-way_boost_buck_vdc_dc_converter", "eln:isolation_transformer", "eln:wire_insulator");
    private static final ContractReport REPORT = new ContractReport(CAMPAIGN);
    private static final List<Target> TARGETS = new ArrayList<>();
    private static final List<Step> STEPS = new ArrayList<>();
    private static int phase, ticks, totalTicks, targetIndex, stepIndex;
    private static boolean finished;
    private static CompletableFuture<Void> serverCheck;
    private static class Target { String id, kind; int x,y,z,side; }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private record Step(String name, Action action, BooleanSupplier ready, Action serverAssertion) {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!CAMPAIGN.equals(System.getProperty("eln.campaign")) || finished) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (++totalTicks > 12000) throw new AssertionError("HV native interaction timeout");
            if (phase == 0) {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay()!=null || ++ticks<20) return;
                Target[] all = new Gson().fromJson(Files.readString(mc.gameDirectory.toPath()
                    .resolve("saves/menu-audit/menu-targets.json")), Target[].class);
                for (Target t: all) if (REQUIRED.contains(t.id)) TARGETS.add(t);
                if (TARGETS.size()!=7 || TARGETS.stream().map(t->t.id).distinct().count()!=7)
                    throw new AssertionError("Missing native HV GUI targets");
                REPORT.write(false);
                mc.createWorldOpenFlows().openWorld("menu-audit",()->{throw new AssertionError("Cannot open archived GUI world");});
                nextPhase(1);
            } else if (phase == 1) {
                if (mc.player==null || mc.level==null || mc.screen!=null || ++ticks<40) return;
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                mc.options.guiScale().set(2);
                mc.getWindow().setWindowed(1280,960); mc.resizeDisplay();
                nextPhase(2);
            } else if (phase == 2) {
                if (ticks++==0) {
                    mc.getSingleplayerServer().execute(()->{
                        var server=mc.getSingleplayerServer(); var p=server.getPlayerList().getPlayers().get(0); var t=target();
                        p.setGameMode(GameType.CREATIVE);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                        p.teleportTo(server.overworld(),t.x+2.0,t.y+1.0,t.z+2.0,135f,15f);
                        p.getAbilities().flying=true;p.onUpdateAbilities();
                        if (t.id.equals("eln:wire_insulator")) {
                            var e=(WireMachineElement)element();
                            var d=UtilityCableDescriptor.allDescriptors().stream().filter(w->
                                w.material==UtilityCableMaterial.COPPER && w.sizeLabel.equals("2 AWG") && !w.insulated && !w.melted)
                                .findFirst().orElseThrow();
                            var stack=d.newItemStack(1); d.setRemainingLengthMeters(stack,16.0);
                            e.getInventory().setItem(0,stack);e.getInventory().setChanged();
                            e.setSelectedOption(0);e.needPublish();
                        }
                    });
                }
                var t=target();
                if (ticks<60 || mc.level.getBlockEntity(new BlockPos(t.x,t.y,t.z))==null) return;
                open();nextPhase(3);
            } else if (phase == 3) {
                if (++ticks<40 || !correctScreen()) return;
                MenuGeometryChecks.verify(mc.screen,true);
                screenshot("normal");
                prepareSteps();stepIndex=0;nextPhase(4);
            } else if (phase == 4) {
                var step=STEPS.get(stepIndex);
                if (ticks++==0) {serverCheck=null;step.action.run();}
                if (ticks>200) throw new AssertionError("No server/UI acknowledgement: "+step.name);
                if (ticks<25 || !step.ready.getAsBoolean()) return;
                if (serverCheck==null) {
                    serverCheck=CompletableFuture.runAsync(()->{
                        try {step.serverAssertion.run();} catch(Exception e) {throw new RuntimeException(e);}
                    },mc.getSingleplayerServer());
                    return;
                }
                if (!serverCheck.isDone()) return;
                serverCheck.join();
                record(step.name,()->{});
                if (++stepIndex==STEPS.size()) nextPhase(5);else ticks=0;
            } else if (phase == 5) {
                if (ticks++==0) {mc.getWindow().setWindowed(640,480);mc.resizeDisplay();}
                if (ticks<25) return;
                record("native-controls-survive-minimum-viewport",()->MenuGeometryChecks.verify(mc.screen,false));
                screenshot("minimum");nextPhase(6);
            } else if (phase == 6) {
                if (++ticks<10) return;
                close();
                if (++targetIndex==TARGETS.size()) {finish();return;}
                mc.getWindow().setWindowed(1280,960);mc.resizeDisplay();nextPhase(2);
            }
        } catch(Throwable e) {
            record("unexpected-phase-"+phase+"-step-"+stepIndex,()->{throw new AssertionError(e);});
            finish();
        }
    }
    private static void prepareSteps() {
        STEPS.clear();
        if (target().id.equals("eln:wire_insulator")) {
            double[] volts={600.0,1000.0,5000.0,20000.0,40000.0,150000.0};
            STEPS.add(new Step("all-HV-insulation-choices-present",()->{},()->{
                var options=wireRender().renderOptions();
                return options.size()==6 && options.get(5).getDescriptor().insulationVoltageRating==150000.0;
            },()->check(WireProduction.INSTANCE.insulatorOptions(((WireMachineElement)element()).getInventory().getItem(0)).size()==6,"Server HV choices")));
            for(int i=1;i<volts.length;i++) {
                final int choice=i; final double voltage=volts[i];
                STEPS.add(new Step("native-select-insulation-"+(int)voltage,()->click(widget(screen(),"next")),
                    ()->wireRender().getSelectedOption()==choice && wireRender().renderOptions().get(choice).getDescriptor().insulationVoltageRating==voltage,
                    ()->{var e=(WireMachineElement)element();check(e.getSelectedOption()==choice,"Server selected insulation");
                        check(WireProduction.INSTANCE.insulatorOptions(e.getInventory().getItem(0)).get(choice).insulationVoltageRating==voltage,"Server insulation voltage");}));
            }
            STEPS.add(new Step("native-previous-insulation",()->click(widget(screen(),"previous")),
                ()->wireRender().getSelectedOption()==4,()->check(((WireMachineElement)element()).getSelectedOption()==4,"Previous packet")));
            addReopenStep("insulation-choice-survives-reopen",()->wireRender().getSelectedOption()==4,
                ()->check(((WireMachineElement)element()).getSelectedOption()==4,"Persisted insulation choice"));
            return;
        }
        STEPS.add(new Step("legacy-mapping-visible-before-explicit-upgrade",()->{},
            ()->clientControl().getVersion()==1 && "SIGNAL".equals(clientControl().getMode()),
            ()->check(serverControl().getVersion()==1,"Server legacy mapping")));
        STEPS.add(new Step("native-upgrade-explicitly-disables-output",()->click(widget(controls(),"upgrade")),
            ()->matches(clientControl(),2,"RATIO",1.0,false),()->check(matches(serverControl(),2,"RATIO",1.0,false),"Server upgrade state")));
        STEPS.add(new Step("native-enable-packet-round-trip",()->click(widget(controls(),"enable")),
            ()->matches(clientControl(),2,"RATIO",1.0,true),()->check(matches(serverControl(),2,"RATIO",1.0,true),"Server enable")));
        boolean variable=target().id.contains("vdc") || target().id.equals("eln:variable_dc-dc_converter");
        if(variable) {
            addTypedStep("native-ratio-text-entry","16", "RATIO",16.0);
            addTypedStep("nonfinite-text-does-not-change-setting","NaN","RATIO",16.0);
            addTypedStep("out-of-range-ratio-rejected-by-server","99999","RATIO",16.0);
            STEPS.add(new Step("native-switch-to-voltage-mode",()->click(widget(controls(),"mode")),
                ()->matches(clientControl(),2,"VOLTAGE",800.0,true),()->check(matches(serverControl(),2,"VOLTAGE",800.0,true),"Server voltage mode")));
            addTypedStep("native-3200V-text-packet-round-trip","3200","VOLTAGE",3200.0);
        }
        final String mode=variable?"VOLTAGE":"RATIO";final double value=variable?3200.0:1.0;
        addReopenStep("native-control-values-survive-reopen",()->matches(clientControl(),2,mode,value,true),
            ()->check(matches(serverControl(),2,mode,value,true),"Reopened server settings"));
        STEPS.add(new Step("native-disable-packet-round-trip",()->click(widget(controls(),"enable")),
            ()->matches(clientControl(),2,mode,value,false),()->check(matches(serverControl(),2,mode,value,false),"Server disabled")));
    }
    private static void addTypedStep(String name,String text,String mode,double value) {
        STEPS.add(new Step(name,()->type(text),()->matches(clientControl(),2,mode,value,true),
            ()->check(matches(serverControl(),2,mode,value,true),"Server value after "+name)));
    }
    private static void addReopenStep(String name,BooleanSupplier ready,Action serverAssertion) {
        STEPS.add(new Step(name,()->{close();open();},()->correctScreen() && ready.getAsBoolean(),serverAssertion));
    }
    private static boolean matches(DcDcControl c,int version,String mode,double value,boolean enabled) {
        return c.getVersion()==version && mode.equals(c.getMode()) && c.getValue()==value && c.getEnabled()==enabled;
    }
    private static Target target(){return TARGETS.get(targetIndex);}
    private static Screen screen(){return Minecraft.getInstance().screen;}
    private static boolean correctScreen(){return target().id.equals("eln:wire_insulator") ? screen() instanceof WireMachineGui : screen() instanceof OneWayDcDcGui || screen() instanceof VariableDcDcGui;}
    private static Object controls(){return field(screen(),"controls");}
    private static DcDcControl clientControl(){
        if(screen() instanceof OneWayDcDcGui s)return s.getRender().getSettings();
        return ((VariableDcDcGui)screen()).getRender().getSettings();
    }
    private static WireMachineRender wireRender(){return (WireMachineRender)field(screen(),"render");}
    private static TransparentNodeElement element(){
        var t=target();var w=Minecraft.getInstance().getSingleplayerServer().overworld();
        return ((TransparentNode)NodeManager.instance.getNodeFromCoordonate(new Coordinate(t.x,t.y,t.z,w))).element;
    }
    private static DcDcControl serverControl(){
        var e=element();return e instanceof OneWayDcDcElement c?c.getSettings():((VariableDcDcElement)e).getSettings();
    }
    private static Object field(Object o,String name){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static AbstractWidget widget(Object o,String name){return (AbstractWidget)field(o,name);}
    private static void click(AbstractWidget w){
        check(w.visible && w.active,"Native control must be visible and active");
        double x=w.getX()+w.getWidth()/2.0,y=w.getY()+w.getHeight()/2.0;
        screen().mouseClicked(x,y,0);screen().mouseReleased(x,y,0);
    }
    private static void type(String text){
        var f=(GuiTextFieldEln)widget(controls(),"value");click(f);check(f.isFocused(),"Text input not focused");
        for(int i=0;i<150;i++)screen().keyPressed(GLFW.GLFW_KEY_BACKSPACE,0,0);
        for(char c:text.toCharArray())screen().charTyped(c,0);
        check(text.equals(f.getText()),"Native typing mismatch: "+f.getText());
        screen().keyPressed(GLFW.GLFW_KEY_ENTER,0,0);
    }
    private static void open(){
        var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var t=target();
            GuiHandler.open(p,GuiHandler.nodeBaseOpen+t.side,p.serverLevel(),t.x,t.y,t.z);
        });
    }
    private static void close(){var mc=Minecraft.getInstance();if(mc.screen!=null)mc.screen.onClose();if(mc.player!=null)mc.player.closeContainer();}
    private static void screenshot(String suffix){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"hv-controls-"+target().id.replace(':','-')+"-"+suffix+".png",mc.getMainRenderTarget(),m->Eln.LOGGER.info("HV native screenshot {}",m.getString()));}
    private static void nextPhase(int value){phase=value;ticks=0;}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void record(String name,Action body){REPORT.test(targetIndex<TARGETS.size()?target().id:CAMPAIGN,name,()->{try{body.run();}catch(Exception e){throw new RuntimeException(e);}return kotlin.Unit.INSTANCE;});REPORT.write(false);}
    private static void finish(){finished=true;REPORT.write(true);if(REPORT.getFailures()>0)System.exit(1);else Minecraft.getInstance().stop();}
}
