package mods.eln.devtest;

import mods.eln.Eln;
import mods.eln.misc.Coordinate;
import mods.eln.misc.Direction;
import mods.eln.node.NodeBase;
import mods.eln.node.NodeManager;
import mods.eln.node.six.SixNode;
import mods.eln.node.six.SixNodeElement;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * In-world smoke test for the 1.12.2 port, driven from a headless dedicated server.
 *
 * Placement of an Eln block cannot be reproduced with /setblock: the node behind the tile entity is
 * created by the item's use path, and a tile entity without a node is removed again on the next
 * tick. So this drives the real path with a FakePlayer.
 *
 * Two modes, run as two separate server launches against the same world, so persistence crosses a
 * real save/load rather than an in-memory round trip:
 *
 *     ./gradlew runServer -PsmokeTest=place     places a source + cable, ticks, reads the meters
 *     ./gradlew runServer -PsmokeTest=verify    re-reads them after the restart
 *
 * Never active without -Deln.smokeTest.
 */
public final class SmokeTest {

    private static final String PREFIX = "SMOKE";
    /** Far from spawn so the generated terrain does not interfere. */
    private static final int X = 512, Z = 512, GROUND = 64;

    private final boolean placing;
    private int ticks = 0;

    private SmokeTest(String mode) {
        this.placing = "place".equals(mode);
    }

    public static void registerIfRequested() {
        String mode = System.getProperty("eln.smokeTest");
        if (mode == null) return;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new SmokeTest(mode));
        Eln.logger.info("{} armed, mode={}", PREFIX, mode);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        // 20 ticks of settling before touching the world, then let the simulator run before reading.
        if (ticks == 20) {
            try {
                if (placing) place();
            } catch (Throwable t) {
                Eln.logger.error("{} FAIL placement threw", PREFIX, t);
                shutdown();
            }
        } else if (ticks == 80) {
            try {
                verify();
            } catch (Throwable t) {
                Eln.logger.error("{} FAIL verification threw", PREFIX, t);
            }
            shutdown();
        }
    }

    private WorldServer world() {
        return FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
    }

    private void place() {
        WorldServer world = world();
        FakePlayer player = FakePlayerFactory.getMinecraft(world);

        // A known-good floor: two stone blocks with air above them.
        for (int dx = 0; dx <= 4; dx++) {
            world.setBlockState(new BlockPos(X + dx, GROUND, Z), Blocks.STONE.getDefaultState());
            for (int dy = 1; dy <= 2; dy++) {
                world.setBlockToAir(new BlockPos(X + dx, GROUND + dy, Z));
            }
        }

        placeSixNode(world, player, "Electrical Source", X, Z);
        placeSixNode(world, player, "Low Voltage Cable", X + 1, Z);
        placeSixNode(world, player, "Low Voltage Cable", X + 2, Z);
        placeSixNode(world, player, "Creative Power Resistor", X + 3, Z, 90.0f);
        // The source drives against ground, so the far side of the load needs a return path or
        // nothing flows and the resistor reads 0 V across a floating terminal.
        placeSixNode(world, player, "Ground Cable", X + 4, Z);

        // The creative source defaults to 0 V; readConfigTool is the public setter.
        SixNodeElement source = element(world, X, Z);
        if (source == null) {
            Eln.logger.error("{} FAIL no source element after placement", PREFIX);
            return;
        }
        NBTTagCompound cfg = new NBTTagCompound();
        cfg.setDouble("voltage", 50.0);
        if (source instanceof mods.eln.item.IConfigurable) {
            ((mods.eln.item.IConfigurable) source).readConfigTool(cfg, player);
        }
        Eln.logger.info("{} placed source and cable, source set to 50 V", PREFIX);
    }

    private void placeSixNode(WorldServer world, FakePlayer player, String descriptorName, int x, int z) {
        placeSixNode(world, player, descriptorName, x, z, 0.0f);
    }

    /**
     * A two-terminal element wires through front.left()/front.right(), and the front comes from the
     * placing player's look direction - so the yaw decides which way the terminals face.
     */
    private void placeSixNode(WorldServer world, FakePlayer player, String descriptorName, int x, int z, float yaw) {
        player.rotationYaw = yaw;
        player.rotationYawHead = yaw;
        ItemStack stack = Eln.findItemStack(descriptorName, 1);
        if (stack == null || stack.isEmpty()) {
            Eln.logger.error("{} FAIL no item stack named '{}'", PREFIX, descriptorName);
            return;
        }
        player.setHeldItem(EnumHand.MAIN_HAND, stack.copy());
        // Right-click the top face of the stone: the six node lands on the block above, YN face.
        EnumActionResult result = Eln.sixNodeItem.onItemUse(
            stack, player, world, new BlockPos(x, GROUND, z), EnumHand.MAIN_HAND,
            EnumFacing.UP, 0.5f, 1.0f, 0.5f);
        IBlockState placed = world.getBlockState(new BlockPos(x, GROUND + 1, z));
        Eln.logger.info("{} place '{}' at ({},{},{}) -> {} block={}",
            PREFIX, descriptorName, x, GROUND + 1, z, result, placed.getBlock().getRegistryName());
    }

    private void orient(WorldServer world, int x, int z, mods.eln.misc.LRDU front) {
        SixNodeElement element = element(world, x, z);
        if (element == null) return;
        element.front = front;
        if (element.sixNode != null) element.sixNode.reconnect();
        element.needPublish();
        Eln.logger.info("{} oriented ({},{},{}) front={}", PREFIX, x, GROUND + 1, z, front);
    }

    private SixNodeElement element(WorldServer world, int x, int z) {
        NodeBase node = NodeManager.instance.getNodeFromCoordonate(
            new Coordinate(x, GROUND + 1, z, world));
        if (!(node instanceof SixNode)) return null;
        return ((SixNode) node).getElement(Direction.YN);
    }

    private void verify() {
        WorldServer world = world();
        SixNodeElement source = element(world, X, Z);
        SixNodeElement cable = element(world, X + 1, Z);
        SixNodeElement cable2 = element(world, X + 2, Z);
        SixNodeElement load = element(world, X + 3, Z);
        SixNodeElement ground = element(world, X + 4, Z);
        if (cable2 != null) Eln.logger.info("{} cable2 meter: {}", PREFIX, cable2.multiMeterString());
        if (ground != null) Eln.logger.info("{} ground meter: {}", PREFIX, ground.multiMeterString());

        if (source == null || cable == null || load == null) {
            Eln.logger.error("{} FAIL node missing after {}: source={} cable={} load={}",
                PREFIX, placing ? "placement" : "restart", source, cable, load);
            return;
        }

        String sourceMeter = source.multiMeterString();
        String cableMeter = cable.multiMeterString();
        String loadMeter = load.multiMeterString();
        Eln.logger.info("{} source meter: {}", PREFIX, sourceMeter);
        Eln.logger.info("{} cable  meter: {}", PREFIX, cableMeter);
        Eln.logger.info("{} load   meter: {}", PREFIX, loadMeter);

        // Voltage on the cable proves the nodes connected and the MNA solver ran against the live
        // world; current proves the load is actually drawing through it.
        boolean energised = cableMeter != null && cableMeter.contains("V") && !cableMeter.contains(" 0V");
        boolean current = cableMeter != null && !cableMeter.contains("I 0A");
        Eln.logger.info("{} {} nodes present, energised={} current flowing={}",
            PREFIX, (energised && current) ? "PASS" : "FAIL", energised, current);
    }

    private void shutdown() {
        Eln.logger.info("{} done, stopping server", PREFIX);
        FMLCommonHandler.instance().getMinecraftServerInstance().initiateShutdown();
    }
}
