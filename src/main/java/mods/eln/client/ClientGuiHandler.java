package mods.eln.client;

import mods.eln.Eln;
import mods.eln.GuiHandler;
import mods.eln.item.WireSnipsGui;
import mods.eln.misc.BasicContainer;
import mods.eln.misc.Direction;
import mods.eln.misc.Utils;
import mods.eln.misc.UtilsClient;
import mods.eln.node.INodeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** The client half of {@link GuiHandler}: screen resolution, the menu factory and the screen registration. */
@EventBusSubscriber(modid = Eln.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientGuiHandler {
    private ClientGuiHandler() {
    }

    private static Screen pendingScreen;

    // returns an instance of the Gui you made earlier
    public static Object getClientGuiElement(int id, Player player, Level world, int x, int y, int z) {
        if (id == GuiHandler.genericOpen) {
            return UtilsClient.guiLastOpen;
        }
        if (id == GuiHandler.wireSnipsOpen) {
            return new WireSnipsGui(player);
        }

        if (id >= GuiHandler.nodeBaseOpen && id <= GuiHandler.nodeBaseOpen + 5) {
            INodeEntity nodeEntity = GuiHandler.getNodeEntity(world, x, y, z);
            if (nodeEntity == null) return null;
            Direction side = Direction.fromInt(id - GuiHandler.nodeBaseOpen);
            Utils.println(String.format("Opening GUI at %d,%d,%d", x, y, z));
            return nodeEntity.newGuiDraw(side, player);
        }

        return null;
    }

    /** Opens a screen that has no server-side container (packetOpenLocalGui). */
    public static void openLocal(int id, int x, int y, int z) {
        Minecraft mc = Minecraft.getInstance();
        Object gui = getClientGuiElement(id, mc.player, mc.level, x, y, z);
        if (gui instanceof Screen screen) mc.setScreen(screen);
    }

    /** The IContainerFactory of the mod's menu type: builds the screen, keeps it, returns its menu. */
    public static AbstractContainerMenu createClientMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        int id = buf.readInt(), x = buf.readInt(), y = buf.readInt(), z = buf.readInt();
        GuiHandler.pendingContainerId = containerId;
        Minecraft mc = Minecraft.getInstance();
        Object gui = getClientGuiElement(id, mc.player, mc.level, x, y, z);
        if (gui instanceof AbstractContainerScreen<?> screen) {
            pendingScreen = screen;
            return screen.getMenu();
        }
        pendingScreen = null;
        // No screen on this side: an empty menu keeps vanilla's bookkeeping consistent.
        return new BasicContainer(mc.player, new SimpleContainer(0), new Slot[0]);
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        if (net.neoforged.fml.ModList.get().isLoaded("create")) mods.eln.integration.create.CreateAdapterClient.screens(event);
        event.<AbstractContainerMenu, AbstractContainerScreen<AbstractContainerMenu>>register(GuiHandler.MENU.get(), (menu, inventory, title) -> {
            Screen s = pendingScreen;
            pendingScreen = null;
            if (s instanceof AbstractContainerScreen<?> screen && screen.getMenu() == menu) {
                @SuppressWarnings("unchecked")
                AbstractContainerScreen<AbstractContainerMenu> typed = (AbstractContainerScreen<AbstractContainerMenu>) screen;
                return typed;
            }
            return new mods.eln.gui.GuiContainerEln(menu) {
                @Override
                protected mods.eln.gui.GuiHelperContainer newHelper() {
                    return new mods.eln.gui.HelperStdContainer(this);
                }
            };
        });
    }
}
