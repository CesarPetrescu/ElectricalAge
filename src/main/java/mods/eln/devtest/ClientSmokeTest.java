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

    private enum Phase { OPEN, JOIN, SETUP, WORLD_SHOT, GUI, GUI_SHOT, INVENTORY, INVENTORY_SHOT, CREATIVE_TAB, CREATIVE_SHOT, DONE }

    private final String save;
    private Phase phase = Phase.OPEN;
    private int wait;

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
            Eln.LOGGER.error("{} FAIL client phase {} threw", PREFIX, phase, t);
            mc.stop();
        }
    }

    private void tick(Minecraft mc) {
        switch (phase) {
            case OPEN -> {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                if (wait++ < 20) return;
                Eln.LOGGER.info("{} opening world '{}'", PREFIX, save);
                mc.createWorldOpenFlows().openWorld(save, () -> {
                    Eln.LOGGER.error("{} FAIL could not open world '{}'", PREFIX, save);
                    mc.stop();
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
                ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                player.setGameMode(GameType.CREATIVE);
                // hover south of the circuit, looking north and down at it (the smoke floor sits well above the flat world's surface)
                player.getAbilities().flying = true;
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
                phase = Phase.WORLD_SHOT;
                wait = 0;
            }
            case WORLD_SHOT -> {
                if (wait++ < 100) return;
                shot(mc, "smoke-world");
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
                Eln.LOGGER.info("{} {} screen open: {}", PREFIX, mc.screen == null ? "FAIL no" : "PASS", mc.screen == null ? null : mc.screen.getClass().getName());
                shot(mc, "smoke-gui");
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
                if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen creative) {
                    var tab = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.stream()
                        .filter(t -> t.getDisplayName().getString().contains("Machines")).findFirst().orElse(null);
                    try {
                        var m = net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", net.minecraft.world.item.CreativeModeTab.class);
                        m.setAccessible(true);
                        m.invoke(creative, tab);
                        Eln.LOGGER.info("{} creative tab '{}' selected", PREFIX, tab == null ? null : tab.getDisplayName().getString());
                    } catch (Exception e) {
                        Eln.LOGGER.error("{} FAIL selecting the creative tab", PREFIX, e);
                    }
                }
                phase = Phase.CREATIVE_SHOT;
                wait = 0;
            }
            case CREATIVE_SHOT -> {
                if (wait++ < 40) return;
                shot(mc, "smoke-creative");
                phase = Phase.DONE;
                wait = 0;
            }
            case DONE -> {
                if (wait++ < 10) return;
                Eln.LOGGER.info("{} client done, stopping", PREFIX);
                mc.stop();
            }
        }
    }

    private void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> Eln.LOGGER.info("{} screenshot {}: {}", PREFIX, name, message.getString()));
    }
}
