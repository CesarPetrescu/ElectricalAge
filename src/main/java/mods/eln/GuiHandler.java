package mods.eln;

import mods.eln.item.WireSnipsContainer;
import mods.eln.misc.Direction;
import mods.eln.misc.McBridge;
import mods.eln.misc.Utils;
import mods.eln.node.INodeEntity;
import mods.eln.registration.ElnRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.IContainerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * Opens the mod's GUIs. Up to 1.12 this was Forge's {@code IGuiHandler}: an integer id plus block
 * coordinates, resolved to a container on the server and to a screen on the client. 1.21 opens
 * menus through one registered {@link MenuType} and a data buffer, which carries the same four
 * numbers; the two resolvers below are unchanged. Element screens build their own container in
 * their constructor (the 1.7.10 design), so the client-side factory constructs the screen, keeps it
 * in {@link #pendingScreen} and hands vanilla the menu it made; the screen factory then returns
 * that screen (see {@code mods.eln.client.ClientGuiHandler}). GUIs without a container (pure
 * {@code GuiScreenEln}) are opened through the byte protocol's packetOpenLocalGui, as before.
 */
public class GuiHandler {

    public static final int genericOpen = 5977;
    public static final int wireSnipsOpen = 5978;
    public static final int nodeBaseOpen = 6935;

    /** The one menu type; the buffer selects the actual container. */
    public static Supplier<MenuType<AbstractContainerMenu>> MENU;

    /**
     * The container id vanilla assigned to the menu being built on this thread. Electrical Age's 37
     * containers are constructed by their screens without an id, so BasicContainer reads it here.
     */
    public static int pendingContainerId;

    public static void register() {
        MENU = ElnRegistry.registerMenu("eln_menu", (IContainerFactory<AbstractContainerMenu>) (id, inv, buf) ->
            mods.eln.client.ClientGuiHandler.createClientMenu(id, inv, buf));
    }

    public static INodeEntity getNodeEntity(Level world, int x, int y, int z) {
        BlockEntity e = McBridge.getBlockEntity(world, x, y, z);
        if (e == null || false == e instanceof INodeEntity) return null;
        return (INodeEntity) e;
    }

    /** 1.7.10's {@code player.openGui(Eln.instance, id, world, x, y, z)}, server side. */
    public static void open(Player player, int id, Level world, int x, int y, int z) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        // Only menus with a container go through vanilla; the rest is a client-only screen. The
        // probe container is discarded: containers are plain objects, the id-bearing one is built
        // in createMenu once vanilla has assigned the id.
        if (getServerGuiElement(id, player, world, x, y, z) != null) {
            {
                serverPlayer.openMenu(new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.empty();
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                        pendingContainerId = containerId;
                        return (AbstractContainerMenu) getServerGuiElement(id, p, world, x, y, z);
                    }
                }, buf -> {
                    buf.writeInt(id);
                    buf.writeInt(x);
                    buf.writeInt(y);
                    buf.writeInt(z);
                });
                return;
            }
        }
        sendOpenLocalGui(serverPlayer, id, x, y, z);
    }

    private static void sendOpenLocalGui(ServerPlayer player, int id, int x, int y, int z) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream stream = new DataOutputStream(bos);
            stream.writeByte(Eln.packetOpenLocalGui);
            stream.writeInt(id);
            stream.writeInt(x);
            stream.writeInt(y);
            stream.writeInt(z);
            Utils.sendPacketToClient(bos, player);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // returns an instance of the Container you made earlier
    public static Object getServerGuiElement(int id, Player player, Level world, int x, int y, int z) {
        if (id == wireSnipsOpen) return new WireSnipsContainer(player);
        INodeEntity nodeEntity = getNodeEntity(world, x, y, z);
        if (nodeEntity == null) return null;
        Direction side = Direction.fromInt(id - nodeBaseOpen);
        return nodeEntity.newContainer(side, player);
    }
}
