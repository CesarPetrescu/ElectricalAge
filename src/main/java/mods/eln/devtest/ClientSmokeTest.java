package mods.eln.devtest;

import mods.eln.Eln;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client half of the smoke test: `-Deln.smokeClient=<save>` opens that singleplayer world
 * (a copy of the dedicated server's smoke world), walks the player up to the circuit the server
 * test placed, and screenshots the world, an element GUI and the inventory before exiting.
 *
 *     cp -r run/server/world run/client/saves/smoke
 *     DISPLAY=:99 ./gradlew runClient -PsmokeClient=smoke
 *
 * The screenshots land in run/client/screenshots; a render-path exception crashes the game, which
 * is the other kind of evidence.
 */
public final class ClientSmokeTest {
    private static final String PREFIX = "SMOKE";
    private static final int X = 512, Z = 512, GROUND = 64;
    /** The server test's mechanical rows (SmokeTest.MECH_Z, LARGE_Z). */
    private static final int MECH_Z = Z + 14, LARGE_Z = Z + 21, GALLERY_Z = Z + 28;

    private enum Phase { OPEN, JOIN, SETUP, WORLD_SHOT, NIGHT_SHOT, GRID_SHOT, MECH_SHOT, MECH_SPIN_SHOT, TACHOMETER_GUI, TACHOMETER_GUI_SHOT, LARGE_SHOT, GALLERY_SHOT, THIRD_PERSON_SHOT, HAND_THIRD_SHOT, HAND_FIRST_SHOT, HAND_CABLE_SHOT, GUI, GUI_SHOT, MACHINE_GUI, MACHINE_GUI_SHOT, INVENTORY, INVENTORY_SHOT, CREATIVE_TAB, CREATIVE_SHOT, CREATIVE_TAB_POWER, CREATIVE_POWER_SHOT, ADAPTER_VIEW, ADAPTER_GUI, ADAPTER_GUI_SHOT, ADAPTER_DETAILS, DONE }

    private final String save;
    private Phase phase = Phase.OPEN;
    private int wait;
    private int failures;

    /** One check: a PASS/FAIL log line, and the run's exit status. */
    private boolean check(boolean ok, String what, Object... args) {
        if (!ok) failures++;
        Object[] all = new Object[args.length + 2];
        all[0] = PREFIX;
        all[1] = ok ? "PASS" : "FAIL";
        System.arraycopy(args, 0, all, 2, args.length);
        if (ok) Eln.LOGGER.info("{} {} " + what, all); else Eln.LOGGER.error("{} {} " + what, all);
        return ok;
    }

    private void fail(String what, Object... args) {
        check(false, what, args);
    }

    private ClientSmokeTest(String save) {
        this.save = save;
    }

    public static void registerIfRequested() {
        String save = System.getProperty("eln.smokeClient");
        if (save == null) return;
        NeoForge.EVENT_BUS.register(new ClientSmokeTest(save));
        Eln.LOGGER.info("{} client armed, save={}", PREFIX, save);
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        try {
            tick(mc);
        } catch (Throwable t) {
            fail("client phase {} threw", phase, t);
            System.exit(1);
        }
    }

    private void tick(Minecraft mc) {
        switch (phase) {
            case OPEN -> {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                if (wait++ < 20) return;
                Eln.LOGGER.info("{} opening world '{}'", PREFIX, save);
                mc.createWorldOpenFlows().openWorld(save, () -> {
                    fail("could not open world '{}'", save);
                    System.exit(1);
                });
                phase = Phase.JOIN;
                wait = 0;
            }
            case JOIN -> {
                if (mc.player == null || mc.level == null || mc.screen != null) return;
                if (wait++ < 40) return;
                phase = Phase.SETUP;
            }
            case SETUP -> {
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                var server = mc.getSingleplayerServer();
                server.execute(() -> setup(server));
                phase = Phase.WORLD_SHOT;
                wait = 0;
            }
            case WORLD_SHOT -> {
                // the client owns its flight state; keep it hovering once the server has made it creative
                if (mc.player.getAbilities().mayfly && !mc.player.getAbilities().flying) {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                }
                if (wait++ < 100) return;
                shot(mc, "smoke-world");
                // the same view at midnight: the lit lamp socket and the spot it projects are the block light
                var server = mc.getSingleplayerServer();
                server.execute(() -> server.overworld().setDayTime(18000));
                phase = Phase.NIGHT_SHOT;
                wait = 0;
            }
            case NIGHT_SHOT -> {
                if (wait++ < 60) return;
                shot(mc, "smoke-night");
                // from above the -PsmokeTest=all grid, when the world has one: every descriptor's renderer in one frame
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    server.overworld().setDayTime(6000);
                    ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                    player.teleportTo(player.serverLevel(), X + 45, GROUND + 16, Z + 2, 180f, 40f);
                });
                phase = Phase.GRID_SHOT;
                wait = 0;
            }
            case GRID_SHOT -> {
                if (wait++ < 80) return;
                shot(mc, "smoke-all");
                // the mechanical row (motor, joint, tachometer, flywheel, generator) from the south, at shaft height
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                    player.teleportTo(player.serverLevel(), X + 2.5, GROUND + 2.0, MECH_Z + 4.5, 180f, 12f);
                });
                phase = Phase.MECH_SHOT;
                wait = 0;
            }
            case MECH_SHOT -> {
                if (wait++ < 60) return;
                shot(mc, "smoke-mech");
                mechAngle = shaftAngle(mc, X, GROUND + 1, MECH_Z);
                phase = Phase.MECH_SPIN_SHOT;
                wait = 0;
            }
            case MECH_SPIN_SHOT -> {
                // a second later: the client-side shaft angle must have moved on (the renderer integrates the published speed)
                if (wait++ < 20) return;
                double angle = shaftAngle(mc, X, GROUND + 1, MECH_Z);
                double rads = shaftRads(mc, X, GROUND + 1, MECH_Z);
                check(rads > 10 && !Double.isNaN(mechAngle) && angle != mechAngle, "shaft motor render turns: {} rad/s, angle {} -> {}", rads, mechAngle, angle);
                // the generator at the end of the line: its render got the speed and the power it publishes (its first LED lights)
                if (shaftRender(mc, X + 4, GROUND + 1, MECH_Z) instanceof mods.eln.mechanical.GeneratorRender generator) {
                    var led = generator.getLedColors()[0];
                    check(generator.getRads() > 10 && !led.equals(java.awt.Color.black), "generator render: {} rad/s, first LED {}", generator.getRads(), led);
                } else fail("no generator render at the end of the shaft line");
                shot(mc, "smoke-mech-spin");
                phase = Phase.TACHOMETER_GUI;
                wait = 0;
            }
            case TACHOMETER_GUI -> {
                if (wait++ < 10) return;
                // right-click the tachometer: a shaft element's own screen (container-less, opened over the byte protocol)
                BlockPos pos = new BlockPos(X + 2, GROUND + 1, MECH_Z);
                BlockHitResult hit = new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 1.0), Direction.SOUTH, pos, false);
                var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                Eln.LOGGER.info("{} right-click on the tachometer -> {}", PREFIX, result);
                phase = Phase.TACHOMETER_GUI_SHOT;
                wait = 0;
            }
            case TACHOMETER_GUI_SHOT -> {
                if (wait++ < 40) return;
                check(mc.screen instanceof mods.eln.mechanical.TachometerGui, "tachometer screen open: {}", mc.screen == null ? null : mc.screen.getClass().getName());
                shot(mc, "smoke-tachometer-gui");
                if (mc.screen != null) mc.screen.onClose();
                // the large row: a large shaft motor, a joint at shaft height, a large generator
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                    player.teleportTo(player.serverLevel(), X + 2.5, GROUND + 3.5, LARGE_Z + 7.5, 180f, 15f);
                });
                phase = Phase.LARGE_SHOT;
                wait = 0;
            }
            case LARGE_SHOT -> {
                if (wait++ < 60) return;
                shot(mc, "smoke-mech-large");
                double rads = shaftRads(mc, X, GROUND + 1, LARGE_Z);
                check(rads > 10, "large shaft motor render turns: {} rad/s", rads);
                // the gallery of the other shaft machines, and the large turbines behind it
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                    player.teleportTo(player.serverLevel(), X + 5.5, GROUND + 3.0, GALLERY_Z + 7.5, 180f, 14f);
                });
                phase = Phase.GALLERY_SHOT;
                wait = 0;
            }
            case GALLERY_SHOT -> {
                if (wait++ < 60) return;
                shot(mc, "smoke-mech-gallery");
                // back at the circuit; third person from behind, with a macerator and a cable lying on the floor: the in-hand and on-ground item transforms
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                    player.teleportTo(player.serverLevel(), X + 2.5, GROUND + 1.5, Z + 4.5, 180f, 25f);
                    dropItems(server);
                });
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                phase = Phase.THIRD_PERSON_SHOT;
                wait = 0;
            }
            case THIRD_PERSON_SHOT -> {
                if (wait++ < 60) return;
                shot(mc, "smoke-third-person");
                // the machine in hand, seen from the front, then through the player's eyes, then a cable
                mc.player.getInventory().selected = 3;
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                phase = Phase.HAND_THIRD_SHOT;
                wait = 0;
            }
            case HAND_THIRD_SHOT -> {
                if (wait++ < 40) return;
                shot(mc, "smoke-hand-third");
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                phase = Phase.HAND_FIRST_SHOT;
                wait = 0;
            }
            case HAND_FIRST_SHOT -> {
                if (wait++ < 40) return;
                shot(mc, "smoke-hand-first");
                mc.player.getInventory().selected = 0;
                phase = Phase.HAND_CABLE_SHOT;
                wait = 0;
            }
            case HAND_CABLE_SHOT -> {
                if (wait++ < 40) return;
                shot(mc, "smoke-hand-cable");
                phase = Phase.GUI;
                wait = 0;
            }
            case GUI -> {
                if (wait++ < 10) return;
                // right-click the creative resistor (a six-node on the top face of the stone)
                BlockPos pos = new BlockPos(X + 3, GROUND + 1, Z);
                BlockHitResult hit = new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5), Direction.UP, pos, false);
                var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                Eln.LOGGER.info("{} right-click on the resistor -> {}", PREFIX, result);
                phase = Phase.GUI_SHOT;
                wait = 0;
            }
            case GUI_SHOT -> {
                if (wait++ < 40) return;
                check(mc.screen != null, "screen open: {}", mc.screen == null ? null : mc.screen.getClass().getName());
                shot(mc, "smoke-gui");
                if (mc.screen != null) mc.screen.onClose();
                phase = Phase.MACHINE_GUI;
                wait = 0;
            }
            case MACHINE_GUI -> {
                if (wait++ < 10) return;
                // right-click the macerator (a transparent node with a container GUI)
                BlockPos pos = new BlockPos(X + 2, GROUND + 1, Z + 2);
                BlockHitResult hit = new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 1.0), Direction.SOUTH, pos, false);
                var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                Eln.LOGGER.info("{} right-click on the macerator -> {}", PREFIX, result);
                phase = Phase.MACHINE_GUI_SHOT;
                wait = 0;
            }
            case MACHINE_GUI_SHOT -> {
                if (wait++ < 40) return;
                check(mc.screen != null, "machine screen open: {}", mc.screen == null ? null : mc.screen.getClass().getName());
                shot(mc, "smoke-machine-gui");
                if (mc.screen != null) mc.screen.onClose();
                phase = Phase.INVENTORY;
                wait = 0;
            }
            case INVENTORY -> {
                if (wait++ < 10) return;
                mc.setScreen(new InventoryScreen(mc.player));
                phase = Phase.INVENTORY_SHOT;
                wait = 0;
            }
            case INVENTORY_SHOT -> {
                if (wait++ < 40) return;
                shot(mc, "smoke-inventory");
                phase = Phase.CREATIVE_TAB;
                wait = 0;
            }
            case CREATIVE_TAB -> {
                if (wait++ < 10) return;
                // the creative inventory (a creative player's inventory screen) on one of the mod's tabs
                selectCreativeTab(mc, "Processing");
                phase = Phase.CREATIVE_SHOT;
                wait = 0;
            }
            case CREATIVE_SHOT -> {
                if (wait++ < 40) return;
                shot(mc, "smoke-creative");
                phase = Phase.CREATIVE_TAB_POWER;
                wait = 0;
            }
            case CREATIVE_TAB_POWER -> {
                if (wait++ < 10) return;
                // the tab with the shaft machines: their inventory icons are their models
                selectCreativeTab(mc, CREATIVE_CATEGORIES[creativeCategory]);
                phase = Phase.CREATIVE_POWER_SHOT;
                wait = 0;
            }
            case CREATIVE_POWER_SHOT -> {
                if (wait++ < 40) return;
                shot(mc, "smoke-creative-category-" + creativeCategory);
                creativeCategory++;
                phase = creativeCategory < CREATIVE_CATEGORIES.length ? Phase.CREATIVE_TAB_POWER : (net.neoforged.fml.ModList.get().isLoaded("create") ? Phase.ADAPTER_VIEW : Phase.DONE);
                wait = 0;
            }
            case ADAPTER_VIEW -> {
                if (wait == 0) {
                    mc.setScreen(null);
                    mc.getSingleplayerServer().execute(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        player.teleportTo(player.serverLevel(), 643.5, 69, 654.5, 180f, 35f);
                    });
                }
                if (wait++ < 100) return;
                shot(mc, "smoke-create-adapters");
                phase = Phase.ADAPTER_GUI;
                wait = 0;
            }
            case ADAPTER_GUI -> {
                if (wait == 0) {
                    mc.setScreen(null);
                    mc.getSingleplayerServer().execute(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                        player.teleportTo(player.serverLevel(), 640.5, 65, 642.5 + adapterIndex * 8, 180f, 20f);
                    });
                }
                if (wait++ < 40) return;
                var pos = new BlockPos(640, 65, 640 + adapterIndex * 8);
                mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(pos.getCenter(), net.minecraft.core.Direction.SOUTH, pos, false));
                phase = Phase.ADAPTER_GUI_SHOT;
                wait = 0;
            }
            case ADAPTER_GUI_SHOT -> {
                if (wait++ < 40) return;
                check(mc.player.containerMenu instanceof mods.eln.integration.create.CreateAdapterMenu, "Create adapter configuration screen opened");
                shot(mc, "smoke-create-adapter-menu-" + adapterIndex);
                adapterIndex++;
                mc.player.closeContainer();
                phase = adapterIndex < 2 ? Phase.ADAPTER_GUI : Phase.ADAPTER_DETAILS;
                wait = 0;
            }
            case ADAPTER_DETAILS -> {
                if (wait == 0) {
                    mc.setScreen(null);
                    mc.getSingleplayerServer().execute(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                        int index = adapterViewIndex % Direction.values().length;
                        var face = Direction.values()[index];
                        var center = new Vec3(652.5, 69.5, 638.5 + index * 5);
                        if (adapterViewIndex >= Direction.values().length) {
                            face = face.getOpposite();
                            // Expose the input stub after the powered topology checks have finished.
                            player.serverLevel().removeBlock(BlockPos.containing(center).relative(face), false);
                        }
                        var camera = center.add(Vec3.atLowerCornerOf(face.getNormal()).scale(2))
                            .add(face.getAxis().isVertical() ? new Vec3(2, -1, 2) : new Vec3(1, 1, 1));
                        player.teleportTo(player.serverLevel(), camera.x, camera.y, camera.z, 0f, 0f);
                        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, center);
                        player.serverLevel().setDayTime(1000);
                    });
                }
                if (wait++ < 60) return;
                shot(mc, "smoke-create-" + (adapterViewIndex < Direction.values().length ? "port-" : "input-")
                    + Direction.values()[adapterViewIndex % Direction.values().length].getName());
                if (++adapterViewIndex == Direction.values().length * 2) phase = Phase.DONE;
                wait = 0;
            }
            case DONE -> {
                if (wait++ < 10) return;
                // the game's own exit is System.exit(0); a failed check leaves through exit 1 so a script can tell
                if (failures > 0) {
                    Eln.LOGGER.error("{} {} check(s) FAILED, stopping", PREFIX, failures);
                    System.exit(1);
                }
                Eln.LOGGER.info("{} all checks passed, stopping", PREFIX);
                mc.stop();
            }
        }
    }

    /** Server thread: creative, flying, at the circuit, looking north and down at it, the mod's items in the hotbar. */
    private void setup(net.minecraft.server.MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;   // the smoke floor sits well above the flat world's surface
        player.onUpdateAbilities();
        player.teleportTo(player.serverLevel(), X + 2.5, GROUND + 1.5, Z + 4.5, 180f, 25f);
        for (int i = 0; i < 9; i++) player.getInventory().setItem(i, ItemStack.EMPTY);
        String[] hotbar = {"Low Voltage Cable", "Electrical Source", "Creative Power Resistor", "48V Macerator", "Copper Ingot", "Copper Cable", "Small Solar Panel", "Signal Cable", "Wrench"};
        for (int i = 0; i < hotbar.length; i++) {
            ItemStack stack = Eln.findItemStack(hotbar[i], 1);
            if (stack != null) player.getInventory().setItem(i, stack);
        }
        player.inventoryMenu.broadcastChanges();
        Eln.LOGGER.info("{} player placed at the circuit", PREFIX);
    }

    /** Server thread: a macerator and a cable lying on two stone blocks beside the circuit. */
    private void dropItems(net.minecraft.server.MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        var level = player.serverLevel();
        level.setBlock(new BlockPos(X, GROUND, Z + 2), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(X + 4, GROUND, Z + 2), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        for (var drop : new Object[][]{{"48V Macerator", X + 0.5}, {"Low Voltage Cable", X + 4.5}}) {
            var entity = new net.minecraft.world.entity.item.ItemEntity(level, (double) drop[1], GROUND + 1.2, Z + 2.5, Eln.findItemStack((String) drop[0], 1));
            entity.setDeltaMovement(0, 0, 0);
            entity.setPickUpDelay(10000);
            level.addFreshEntity(entity);
        }
    }

    private void selectCreativeTab(Minecraft mc, String name) {
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen creative)) return;
        var tab = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.stream()
            .filter(t -> t.getDisplayName().getString().equals("ELN - " + name)).findFirst().orElse(null);
        try {
            if (tab == null) throw new IllegalStateException("Missing creative category: " + name);
            var pagesField = net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.class.getDeclaredField("pages");
            pagesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var pages = (java.util.List<net.neoforged.neoforge.client.gui.CreativeTabsScreenPage>) pagesField.get(creative);
            pages.stream().filter(page -> page.getVisibleTabs().contains(tab)).findFirst().ifPresent(creative::setCurrentPage);
            var m = net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", net.minecraft.world.item.CreativeModeTab.class);
            m.setAccessible(true);
            m.invoke(creative, tab);
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 0, 0);
            Eln.LOGGER.info("{} creative tab '{}' selected", PREFIX, tab == null ? null : tab.getDisplayName().getString());
        } catch (Exception e) {
            fail("selecting the creative tab", e);
        }
    }

    private double mechAngle = Double.NaN;
    private int adapterViewIndex = 0;
    private int creativeCategory = 0;
    private int adapterIndex = 0;
    private static final String[] CREATIVE_CATEGORIES = {"Wires & Cables", "Signals & Control", "Power", "Mechanics", "Processing", "Lighting", "Materials", "Tools & Armor", "Creative Only"};

    private mods.eln.mechanical.ShaftRender shaftRender(Minecraft mc, int x, int y, int z) {
        var entity = mc.level.getBlockEntity(new BlockPos(x, y, z));
        if (entity instanceof mods.eln.node.transparent.TransparentNodeEntity node && node.getElementRender() instanceof mods.eln.mechanical.ShaftRender shaft) return shaft;
        return null;
    }

    /** The client-side shaft angle of the shaft element at (x, y, z), NaN when there is no such render. */
    private double shaftAngle(Minecraft mc, int x, int y, int z) {
        var render = shaftRender(mc, x, y, z);
        return render == null ? Double.NaN : render.getAngle();
    }

    /** The shaft speed the client last received for the shaft element at (x, y, z), NaN when there is no such render. */
    private double shaftRads(Minecraft mc, int x, int y, int z) {
        var render = shaftRender(mc, x, y, z);
        return render == null ? Double.NaN : render.getRads();
    }

    private void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> Eln.LOGGER.info("{} screenshot {}: {}", PREFIX, name, message.getString()));
    }
}
