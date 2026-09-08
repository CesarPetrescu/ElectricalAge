package mods.eln.devtest;

import mods.eln.Eln;
import mods.eln.node.transparent.TransparentNodeEntity;
import mods.eln.railroad.OverheadLinesRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** QA only: compare the actual renderer's support predicate and capture native frames.
 * Reflecting this private draw gate is deliberate; the production class and world are not patched. */
@EventBusSubscriber(modid = Eln.MODID, value = Dist.CLIENT)
public final class OverheadOrientationChecks {
    private static final BlockPos POS = new BlockPos(32, 80, 32);
    private static final Direction[] SUPPORTS = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
    private static final ContractReport REPORT = new ContractReport("overhead-orientation-client");
    private static int phase, ticks, totalTicks, index;
    private static boolean finished;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!"overhead".equals(System.getProperty("eln.campaign")) || finished) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (++totalTicks > 4000) throw new IllegalStateException("Native overhead client timeout");
            if (phase == 0) {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null || ++ticks < 20) return;
                REPORT.write(false);
                mc.createWorldOpenFlows().openWorld("overhead-test", () -> { throw new IllegalStateException("Cannot open overhead fixture world"); });
                phase = 1; ticks = 0;
            } else if (phase == 1) {
                if (mc.level == null || mc.player == null || mc.screen != null || ++ticks < 40) return;
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                mc.options.hideGui = true;
                mc.getSingleplayerServer().execute(() -> {
                    var server = mc.getSingleplayerServer(); var world = server.overworld();
                    var player = server.getPlayerList().getPlayers().get(0);
                    player.setGameMode(GameType.CREATIVE);
                    world.setDayTime(6000);
                    world.setChunkForced(POS.getX() >> 4, POS.getZ() >> 4, true);
                    for (var p : BlockPos.betweenClosed(POS.offset(-5,-5,-5), POS.offset(5,5,5))) world.removeBlock(p, false);
                    world.setBlockAndUpdate(POS.east(), Blocks.STONE.defaultBlockState());
                    var stack = Eln.findItemStack("Overhead Lines", 1);
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    player.setYRot(0); player.setXRot(0);
                    boolean placed = Eln.transparentNodeItem.placeBlockAt(stack, player, world, POS, Direction.UP);
                    if (!placed) throw new AssertionError("Overhead fixture placement failed");
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.setGameMode(GameType.SPECTATOR);
                    player.teleportTo(world, POS.getX()+4.5, POS.getY()+2.0, POS.getZ()-4.5, 45f, 12f);
                });
                phase = 2; ticks = 0;
            } else if (phase == 2) {
                if (++ticks < 100) return;
                Direction support = SUPPORTS[index];
                check("support-"+support.getName()+"-fixture", () -> {
                    if (!(mc.level.getBlockEntity(POS) instanceof TransparentNodeEntity entity)
                            || !(entity.getElementRender() instanceof OverheadLinesRender))
                        throw new AssertionError("Actual overhead renderer missing");
                    for (Direction side : Direction.values()) {
                        boolean solid = mc.level.getBlockState(POS.relative(side)).isSolidRender(mc.level, POS.relative(side));
                        if (solid != (side == support)) throw new AssertionError("Unexpected support state "+side+": "+solid);
                    }
                });
                check("support-"+support.getName()+"-native-gantry-visible", () -> {
                    var entity = (TransparentNodeEntity)mc.level.getBlockEntity(POS);
                    var renderer = (OverheadLinesRender)entity.getElementRender();
                    try {
                        var predicate = OverheadLinesRender.class.getDeclaredMethod("hasBlockAnySideNotBottom");
                        predicate.setAccessible(true);
                        boolean visible = (Boolean)predicate.invoke(renderer);
                        System.out.println("OVERHEAD_SUPPORT "+support+" nativeDrawBase="+visible);
                        if (!visible) throw new AssertionError("A solid horizontal support at "+support+" suppresses the native gantry draw call");
                    } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                });
                Screenshot.grab(mc.gameDirectory, "overhead-support-"+support.getName()+".png", mc.getMainRenderTarget(), m -> Eln.LOGGER.info("Overhead orientation screenshot {}", m.getString()));
                phase = 3; ticks = 0;
            } else if (phase == 3) {
                if (++ticks < 20) return;
                if (++index == SUPPORTS.length) {
                    REPORT.write(true); finished = true;
                    if (REPORT.getFailures() > 0) System.exit(1); else mc.stop();
                    return;
                }
                mc.getSingleplayerServer().execute(() -> {
                    var world = mc.getSingleplayerServer().overworld();
                    for (Direction side : Direction.values()) world.removeBlock(POS.relative(side), false);
                    world.setBlockAndUpdate(POS.relative(SUPPORTS[index]), Blocks.STONE.defaultBlockState());
                });
                phase = 2; ticks = 0;
            }
        } catch (Throwable t) {
            check("unexpected-phase-"+phase, () -> { throw new AssertionError(t); });
            REPORT.write(false); finished = true; System.exit(1);
        }
    }
    private static void check(String name, Runnable body) {
        REPORT.test("eln:overhead_lines",name,()->{body.run();return kotlin.Unit.INSTANCE;});
        REPORT.write(false);
    }
}
