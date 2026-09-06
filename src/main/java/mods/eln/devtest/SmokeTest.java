package mods.eln.devtest;

import mods.eln.Eln;
import mods.eln.misc.Coordinate;
import mods.eln.misc.Direction;
import mods.eln.node.NodeBase;
import mods.eln.node.NodeManager;
import mods.eln.node.six.SixNode;
import mods.eln.node.six.SixNodeElement;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * In-world smoke test for the port, driven from a headless dedicated server.
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
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new SmokeTest(mode));
        Eln.logger.info("{} armed, mode={}", PREFIX, mode);
    }

    @SubscribeEvent
    public void onTick(ServerTickEvent.Post event) {
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

    private ServerLevel world() {
        return ServerLifecycleHooks.getCurrentServer().overworld();
    }

    private void place() {
        ServerLevel world = world();
        FakePlayer player = FakePlayerFactory.getMinecraft(world);

        // A known-good floor: stone blocks with air above them.
        for (int dx = 0; dx <= 4; dx++) {
            world.setBlock(new BlockPos(X + dx, GROUND, Z), Blocks.STONE.defaultBlockState(), 3);
            for (int dy = 1; dy <= 2; dy++) {
                world.setBlock(new BlockPos(X + dx, GROUND + dy, Z), Blocks.AIR.defaultBlockState(), 3);
            }
        }

        placeSixNode(world, player, "Electrical Source", X, Z);
        placeSixNode(world, player, "Low Voltage Cable", X + 1, Z);
        placeSixNode(world, player, "Low Voltage Cable", X + 2, Z);
        placeSixNode(world, player, "Creative Power Resistor", X + 3, Z, 90.0f);
        // The source drives against ground, so the far side of the load needs a return path or
        // nothing flows and the resistor reads 0 V across a floating terminal.
        placeSixNode(world, player, "Ground Cable", X + 4, Z);

        // A transparent node (a machine with a container GUI) two blocks south, on its own stone.
        world.setBlock(new BlockPos(X + 2, GROUND, Z + 2), Blocks.STONE.defaultBlockState(), 3);
        placeTransparentNode(world, player, "48V Macerator", X + 2, Z + 2);

        // The creative source defaults to 0 V; readConfigTool is the public setter.
        SixNodeElement source = element(world, X, Z);
        if (source == null) {
            Eln.logger.error("{} FAIL no source element after placement", PREFIX);
            return;
        }
        CompoundTag cfg = new CompoundTag();
        cfg.putDouble("voltage", 50.0);
        if (source instanceof mods.eln.item.IConfigurable) {
            ((mods.eln.item.IConfigurable) source).readConfigTool(cfg, player);
        }
        Eln.logger.info("{} placed source and cable, source set to 50 V", PREFIX);
        countOres(world);
    }

    /** World generation is data now: the chunk the circuit sits in must contain the mod's ores. */
    private void countOres(ServerLevel world) {
        if (world.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource) {
            Eln.logger.info("{} SKIP ore count: flat world (set level-type=minecraft:normal in run/server/server.properties to check world generation)", PREFIX);
            return;
        }
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        int cx = X >> 4, cz = Z >> 4;
        for (int x = cx << 4; x < (cx << 4) + 16; x++)
            for (int z = cz << 4; z < (cz << 4) + 16; z++)
                for (int y = world.getMinBuildHeight(); y < 96; y++) {
                    BlockState state = world.getBlockState(new BlockPos(x, y, z));
                    if (state.getBlock() instanceof mods.eln.ore.OreBlock)
                        counts.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(), 1, Integer::sum);
                }
        Eln.logger.info("{} {} ore blocks in chunk ({},{}): {}", PREFIX, counts.isEmpty() ? "FAIL no" : "PASS", cx, cz, counts);
    }

    private void placeSixNode(ServerLevel world, FakePlayer player, String descriptorName, int x, int z) {
        placeSixNode(world, player, descriptorName, x, z, 0.0f);
    }

    /**
     * A two-terminal element wires through front.left()/front.right(), and the front comes from the
     * placing player's look direction - so the yaw decides which way the terminals face.
     */
    private void placeSixNode(ServerLevel world, FakePlayer player, String descriptorName, int x, int z, float yaw) {
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        ItemStack stack = Eln.findItemStack(descriptorName, 1);
        if (stack == null || stack.isEmpty()) {
            Eln.logger.error("{} FAIL no item stack named '{}'", PREFIX, descriptorName);
            return;
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
        // Right-click the top face of the stone: the six node lands on the block above, YN face.
        // onItemUse takes the cell the block goes into (BlockPlaceContext has resolved it by then).
        InteractionResult result = Eln.sixNodeItem.onItemUse(
            stack, player, world, new BlockPos(x, GROUND + 1, z), InteractionHand.MAIN_HAND,
            net.minecraft.core.Direction.UP, 0.5f, 1.0f, 0.5f);
        BlockState placed = world.getBlockState(new BlockPos(x, GROUND + 1, z));
        Eln.logger.info("{} place '{}' at ({},{},{}) -> {} block={}",
            PREFIX, descriptorName, x, GROUND + 1, z, result, BuiltInRegistries.BLOCK.getKey(placed.getBlock()));
    }

    /** A transparent node stands on the block; the item's placement path creates node and block. */
    private void placeTransparentNode(ServerLevel world, FakePlayer player, String descriptorName, int x, int z) {
        player.setYRot(0f);
        player.setYHeadRot(0f);
        ItemStack stack = Eln.findItemStack(descriptorName, 1);
        if (stack == null || stack.isEmpty()) {
            Eln.logger.error("{} FAIL no item stack named '{}'", PREFIX, descriptorName);
            return;
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
        boolean placed = Eln.transparentNodeItem.placeBlockAt(stack, player, world, new BlockPos(x, GROUND + 1, z), net.minecraft.core.Direction.UP);
        BlockState state = world.getBlockState(new BlockPos(x, GROUND + 1, z));
        Eln.logger.info("{} place '{}' at ({},{},{}) -> {} block={}", PREFIX, descriptorName, x, GROUND + 1, z, placed, BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    private void orient(ServerLevel world, int x, int z, mods.eln.misc.LRDU front) {
        SixNodeElement element = element(world, x, z);
        if (element == null) return;
        element.front = front;
        if (element.sixNode != null) element.sixNode.reconnect();
        element.needPublish();
        Eln.logger.info("{} oriented ({},{},{}) front={}", PREFIX, x, GROUND + 1, z, front);
    }

    private SixNodeElement element(ServerLevel world, int x, int z) {
        NodeBase node = NodeManager.instance.getNodeFromCoordonate(
            new Coordinate(x, GROUND + 1, z, world));
        if (!(node instanceof SixNode)) return null;
        return ((SixNode) node).getElement(Direction.YN);
    }

    private void verify() {
        ServerLevel world = world();
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
        runConsoleCommands(world);
    }

    /** The /eln console, as the server console would run it; its output lands in the log. */
    private void runConsoleCommands(ServerLevel world) {
        var server = world.getServer();
        var source = server.createCommandSourceStack();
        for (String command : new String[]{"eln", "eln ls", "eln version", "eln zoneDump 4"}) {
            Eln.logger.info("{} running /{}", PREFIX, command);
            server.getCommands().performPrefixedCommand(source, command);
        }
    }

    private void shutdown() {
        Eln.logger.info("{} done, stopping server", PREFIX);
        ServerLifecycleHooks.getCurrentServer().halt(false);
    }
}
