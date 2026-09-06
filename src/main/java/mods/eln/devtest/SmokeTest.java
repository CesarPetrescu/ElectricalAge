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
    /** The lamp row: north of the circuit, south of the -PsmokeTest=all grid. */
    private static final int LAMP_Z = Z - 3;

    private final boolean placing;
    private int ticks = 0;

    private final boolean everything;

    private SmokeTest(String mode) {
        this.placing = "place".equals(mode) || "all".equals(mode);
        this.everything = "all".equals(mode);
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
                forceLoad(world());
                if (placing) place();
                if (everything) placeEverything();
            } catch (Throwable t) {
                fail("placement threw", t);
                shutdown();
            }
        } else if (ticks == 80) {
            try {
                verify();
            } catch (Throwable t) {
                fail("verification threw", t);
            }
            shutdown();
        }
    }

    private ServerLevel world() {
        return ServerLifecycleHooks.getCurrentServer().overworld();
    }

    private int failures;

    /** One check: a PASS/FAIL log line, and the run's exit status. */
    private boolean check(boolean ok, String what, Object... args) {
        if (!ok) failures++;
        Object[] all = new Object[args.length + 2];
        all[0] = PREFIX;
        all[1] = ok ? "PASS" : "FAIL";
        System.arraycopy(args, 0, all, 2, args.length);
        if (ok) Eln.logger.info("{} {} " + what, all); else Eln.logger.error("{} {} " + what, all);
        return ok;
    }

    private void fail(String what, Object... args) {
        check(false, what, args);
    }

    /**
     * Nobody is online, so without this the chunks the circuit sits in drop out one tick after
     * each block access, and nothing in them ticks: the light block entity, the nodes' entities.
     * Forced chunks (what /forceload does; the choice is saved with the world) are entity-ticking.
     */
    private void forceLoad(ServerLevel world) {
        int minX = (X - 8) >> 4, maxX = (X + 8 + 16 * 6) >> 4;
        int minZ = (Z - 8 - 27 * 6) >> 4, maxZ = (Z + 8) >> 4;
        for (int cx = minX; cx <= maxX; cx++)
            for (int cz = minZ; cz <= maxZ; cz++)
                world.setChunkForced(cx, cz, true);
        Eln.logger.info("{} forced chunks ({},{})..({},{})", PREFIX, minX, minZ, maxX, maxZ);
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

        setVoltage(world, player, X, Z, 50.0);
        Eln.logger.info("{} placed source and cable, source set to 50 V", PREFIX);

        // A lamp on its own row north of the circuit: source -> MV cable -> classic lamp socket with
        // a 120 V bulb and a cable in its slots. Verified: the socket's block light (a node's light
        // is live data, kept in the chunk's auxiliary light manager) and the light block it projects.
        for (int dx = 0; dx <= 2; dx++) world.setBlock(new BlockPos(X + dx, GROUND, LAMP_Z), Blocks.STONE.defaultBlockState(), 3);
        placeSixNode(world, player, "Electrical Source", X, LAMP_Z);
        placeSixNode(world, player, "Medium Voltage Cable", X + 1, LAMP_Z);
        placeSixNode(world, player, "Classic Lamp Socket", X + 2, LAMP_Z);
        setVoltage(world, player, X, LAMP_Z, 120.0);
        if (element(world, X + 2, LAMP_Z) instanceof mods.eln.sixnode.lampsocket.LampSocketElement socket) {
            socket.getInventory().setItem(mods.eln.sixnode.lampsocket.LampSocketContainer.LAMP_SLOT_ID, Eln.findItemStack("120V Incandescent Light Bulb", 1));
            socket.getInventory().setItem(mods.eln.sixnode.lampsocket.LampSocketContainer.CABLE_SLOT_ID, Eln.findItemStack("Medium Voltage Cable", 1));
            socket.setPoweredByLampSupply(false);   // a socket is born wireless; this one is cabled
            socket.inventoryChange(socket.getInventory());
            Eln.logger.info("{} lamp socket loaded with a bulb and a cable", PREFIX);
        } else {
            fail("no lamp socket element after placement");
        }

        // The computer probe at the end of the lamp row: a node block on its own, a peripheral with CC: Tweaked.
        world.setBlock(new BlockPos(X + 4, GROUND, LAMP_Z), Blocks.STONE.defaultBlockState(), 3);
        placeBlockItem(world, player, "elnprobe", X + 4, LAMP_Z);
        // and the energy exporter beside it: the other single-node block, a textured cube too
        world.setBlock(new BlockPos(X + 6, GROUND, LAMP_Z), Blocks.STONE.defaultBlockState(), 3);
        placeBlockItem(world, player, "energyconverter", X + 6, LAMP_Z);
        countOres(world);
    }

    /** A plain block item (the single-node blocks) placed on top of the stone at (x, GROUND, z) through its own use path. */
    private void placeBlockItem(ServerLevel world, FakePlayer player, String id, int x, int z) {
        var item = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Eln.MODID, id));
        ItemStack stack = new ItemStack(item);
        player.setYRot(0f);
        player.setYHeadRot(0f);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(x + 0.5, GROUND + 1.0, z + 0.5),
            net.minecraft.core.Direction.UP, new BlockPos(x, GROUND, z), false);
        var result = item.useOn(new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        BlockState placed = world.getBlockState(new BlockPos(x, GROUND + 1, z));
        Eln.logger.info("{} place '{}' at ({},{},{}) -> {} block={}", PREFIX, id, x, GROUND + 1, z, result, BuiltInRegistries.BLOCK.getKey(placed.getBlock()));
    }

    /** The creative source defaults to 0 V; readConfigTool is the public setter. */
    private void setVoltage(ServerLevel world, FakePlayer player, int x, int z, double voltage) {
        SixNodeElement source = element(world, x, z);
        if (!(source instanceof mods.eln.item.IConfigurable configurable)) {
            fail("no source element at ({},{})", x, z);
            return;
        }
        CompoundTag cfg = new CompoundTag();
        cfg.putDouble("voltage", voltage);
        configurable.readConfigTool(cfg, player);
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
        check(!counts.isEmpty(), "ore blocks in chunk ({},{}): {}", cx, cz, counts);
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
            fail("no item stack named '{}'", descriptorName);
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

    /**
     * `-PsmokeTest=all`: every six-node and transparent-node descriptor placed on its own stone,
     * on a grid north of the circuit, then ticked with the rest. An element whose server-side code
     * throws on placement or in its first ticks shows up in the log (and stops the server, as it
     * would in play); the count of nodes afterwards is the pass mark.
     */
    private void placeEverything() {
        ServerLevel world = world();
        FakePlayer player = FakePlayerFactory.getMinecraft(world);
        int placed = 0, failed = 0, threw = 0, i = 0;
        java.util.List<Object[]> descriptors = new java.util.ArrayList<>();
        for (var d : Eln.sixNodeItem.subItemList.values()) descriptors.add(new Object[]{"six", d});
        for (var d : Eln.transparentNodeItem.subItemList.values()) descriptors.add(new Object[]{"transparent", d});
        for (Object[] entry : descriptors) {
            var descriptor = (mods.eln.generic.GenericItemBlockUsingDamageDescriptor) entry[1];
            // 6 apart: the biggest multiblock machines reach 2 blocks out; a row of 16 per z
            int x = X + (i % 16) * 6, z = Z - 8 - (i / 16) * 6;
            i++;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                world.setBlock(new BlockPos(x + dx, GROUND, z + dz), Blocks.STONE.defaultBlockState(), 3);
            }
            ItemStack stack = descriptor.newItemStack(1);
            if (stack == null || stack.isEmpty()) { failed++; continue; }
            player.setYRot(0f);
            player.setYHeadRot(0f);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
            try {
                boolean ok = "six".equals(entry[0])
                    ? Eln.sixNodeItem.onItemUse(stack, player, world, new BlockPos(x, GROUND + 1, z), InteractionHand.MAIN_HAND,
                        net.minecraft.core.Direction.UP, 0.5f, 1.0f, 0.5f) == InteractionResult.SUCCESS
                    : Eln.transparentNodeItem.placeBlockAt(stack, player, world, new BlockPos(x, GROUND + 1, z), net.minecraft.core.Direction.UP);
                BlockState state = world.getBlockState(new BlockPos(x, GROUND + 1, z));
                boolean present = state.getBlock() instanceof mods.eln.node.NodeBlock;
                if (ok && present) placed++;
                else {
                    failed++;
                    Eln.logger.warn("{} ALL could not place '{}' ({}) at ({},{},{}): ok={} block={}", PREFIX, descriptor.name, entry[0], x, GROUND + 1, z, ok, BuiltInRegistries.BLOCK.getKey(state.getBlock()));
                }
            } catch (Throwable t) {
                failed++;
                threw++;
                Eln.logger.error("{} ALL placing '{}' ({}) threw", PREFIX, descriptor.name, entry[0], t);
            }
        }
        // the grid cannot satisfy every placement rule (walls, ceilings, water); an exception is the failure
        check(threw == 0, "ALL placed {} of {} descriptors ({} not placed, {} threw)", placed, descriptors.size(), failed, threw);
    }

    /** A transparent node stands on the block; the item's placement path creates node and block. */
    private void placeTransparentNode(ServerLevel world, FakePlayer player, String descriptorName, int x, int z) {
        player.setYRot(0f);
        player.setYHeadRot(0f);
        ItemStack stack = Eln.findItemStack(descriptorName, 1);
        if (stack == null || stack.isEmpty()) {
            fail("no item stack named '{}'", descriptorName);
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
            fail("node missing after {}: source={} cable={} load={}", placing ? "placement" : "restart", source, cable, load);
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
        check(energised && current, "nodes present, energised={} current flowing={}", energised, current);
        if (everything) Eln.logger.info("{} ALL {} nodes alive after {} ticks", PREFIX, NodeManager.instance.getNodeList().size(), ticks);
        verifyLamp(world);
        verifyProbe(world);
        runConsoleCommands(world);
    }

    /** The probe's node must be there; with CC: Tweaked loaded, the capability must answer with the peripheral. */
    private void verifyProbe(ServerLevel world) {
        BlockPos pos = new BlockPos(X + 4, GROUND + 1, LAMP_Z);
        NodeBase node = NodeManager.instance.getNodeFromCoordonate(new Coordinate(pos.getX(), pos.getY(), pos.getZ(), world));
        boolean nodeOk = node instanceof mods.eln.simplenode.computerprobe.ComputerProbeNode;
        if (!net.neoforged.fml.ModList.get().isLoaded(mods.eln.integration.computercraft.ComputerCraftIntegration.MOD_ID)) {
            check(nodeOk, "computer probe node present={}; CC: Tweaked not loaded, peripheral SKIP", nodeOk);
            return;
        }
        String type = mods.eln.integration.computercraft.ComputerCraftIntegration.peripheralTypeAt(world, pos);
        String description = mods.eln.integration.computercraft.ComputerCraftIntegration.describePeripheralAt(world, pos);
        // the ten 1.7.10 method names, bound by CC's generator, and version() answering through the binding
        boolean methodsOk = description.contains("methods=[signalGetDir, signalGetIn, signalGetOut, signalSetDir, signalSetOut, version, wirelessGet, wirelessRemove, wirelessRemoveAll, wirelessSet]")
            && description.contains("version=" + mods.eln.misc.Version.INSTANCE.getSimpleVersionName());
        boolean pass = nodeOk && "ElnProbe".equals(type) && methodsOk;
        check(pass, "computer probe node present={} peripheral {}", nodeOk, description);
    }

    /**
     * The lit socket must light its own block through the light engine (the server's runs off the
     * main thread, so this proves the auxiliary light manager path), and must have projected a light
     * block whose state property carries the level.
     */
    private void verifyLamp(ServerLevel world) {
        SixNodeElement socket = element(world, X + 2, LAMP_Z);
        if (socket == null) {
            fail("lamp socket missing after {}", placing ? "placement" : "restart");
            return;
        }
        Eln.logger.info("{} lamp   meter: {}", PREFIX, socket.multiMeterString());
        BlockPos socketPos = new BlockPos(X + 2, GROUND + 1, LAMP_Z);
        int nodeLight = socket.sixNode.getLightValue();
        var aux = world.getAuxLightManager(socketPos);
        int auxLight = aux == null ? -1 : aux.getLightAt(socketPos);
        int blockLight = world.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, socketPos);
        int lightBlocks = 0, spotLevel = 0, spotBrightness = 0;
        BlockPos spot = null;
        for (BlockPos p : BlockPos.betweenClosed(socketPos.offset(-3, -1, -3), socketPos.offset(3, 6, 3))) {
            BlockState state = world.getBlockState(p);
            if (state.getBlock() != Eln.lightBlock) continue;
            lightBlocks++;
            int level = state.getValue(mods.eln.lightblock.LightBlock.LIGHT);
            if (level > spotLevel) {
                spotLevel = level;
                spot = p.immutable();
                spotBrightness = world.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p);
            }
        }
        boolean pass = nodeLight > 0 && auxLight == nodeLight && blockLight == nodeLight && spot != null && spotBrightness == spotLevel;
        check(pass, "lamp: node light={} aux={} block light at socket={}; {} light block(s), brightest {} at {} reads {}",
            nodeLight, auxLight, blockLight, lightBlocks, spotLevel, spot, spotBrightness);
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

    /** Stops the server; a failed check makes the process exit 1 once the server thread is done, so a script can tell. */
    private void shutdown() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (failures > 0) {
            Eln.logger.error("{} {} check(s) FAILED, stopping server", PREFIX, failures);
            Thread serverThread = server.getRunningThread();
            Thread exit = new Thread(() -> {
                try { serverThread.join(); } catch (InterruptedException ignored) { }
                System.exit(1);
            }, "smoke-exit");
            exit.start();
        } else {
            Eln.logger.info("{} all checks passed, stopping server", PREFIX);
        }
        server.halt(false);
    }
}
