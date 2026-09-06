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
    /** The mechanical row: a shaft line along X south of the spool rows, its cables one block north of it. */
    private static final int MECH_Z = Z + 14;
    /** The large (3x3x3) machines' row, south of that. */
    private static final int LARGE_Z = Z + 21;
    /** The other shaft machines, one of each, in a line south of the large row: models and placement. */
    private static final int GALLERY_Z = Z + 28;
    /** The heat generator: a stone heat furnace burning coal into a 48V turbine through a thermal cable. */
    private static final int HEAT_Z = Z + 35;
    /** The gallery row, west to east; the radial motor's ghost footprint takes the two cells after it. */
    private static final String[] GALLERY = {"Fixed Shaft", "Steam Turbine", "Gas Turbine", "Polarized Shaft Generator", "Polarized Shaft Motor", "Clutch", "Joint hub", "Crank Shaft", "Rolling Shaft Machine", null, "Radial Motor"};
    private static final int CRANK_X = X + 7;
    /** The mechanical row needs seconds to spin up; the run stops after this tick. */
    private static final int LAST_TICK = 260;

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
        } else if (ticks > 20 && ticks < LAST_TICK && ticks % 20 == 0) {
            traceMechanical();
        } else if (ticks == LAST_TICK) {
            try {
                verifyMechanical();
                if (!placing) breakShafts();
            } catch (Throwable t) {
                fail("mechanical verification threw", t);
            }
            if (placing) shutdown();
        } else if (ticks == LAST_TICK + 60) {
            try {
                verifyBrokenShafts();
            } catch (Throwable t) {
                fail("verification after breaking shafts threw", t);
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
        int minZ = (Z - 8 - 27 * 6) >> 4, maxZ = (HEAT_Z + 8) >> 4;
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

        checkCableSpools(world, player);
        placeMechanical(world, player);

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

    /**
     * Utility cable spools: a socket takes 1 m off whatever spool is right-clicked on it, whether the
     * spool is the creative 128 m, one that has placed a segment, one cut short, or one as crafted.
     * Each case gets its own classic lamp socket on a row south of the circuit.
     */
    private void checkCableSpools(ServerLevel world, FakePlayer player) {
        var desc = Eln.sixNodeItem.subItemList.values().stream()
            .filter(d -> d instanceof mods.eln.sixnode.electricalcable.UtilityCableDescriptor u && !u.melted)
            .map(d -> (mods.eln.sixnode.electricalcable.UtilityCableDescriptor) d).findFirst().orElse(null);
        if (desc == null) {
            fail("no utility cable descriptor registered");
            return;
        }
        int z = Z + 7;   // a row of its own, behind where the client smoke stands
        // a spool that placed one segment: through the item-use path, on its own stone
        ItemStack used = desc.newCreativeTabStack();
        world.setBlock(new BlockPos(X - 2, GROUND, z), Blocks.STONE.defaultBlockState(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, used);
        Eln.sixNodeItem.onItemUse(used, player, world, new BlockPos(X - 2, GROUND + 1, z), InteractionHand.MAIN_HAND, net.minecraft.core.Direction.UP, 0.5f, 1.0f, 0.5f);
        ItemStack cut = desc.newCreativeTabStack();
        desc.setRemainingLengthMeters(cut, 5.0);
        Object[][] spools = {{"creative", desc.newCreativeTabStack()}, {"after one placement", used}, {"cut to 5 m", cut}, {"as crafted", desc.newItemStack()}};
        int i = 0;
        for (Object[] entry : spools) {
            ItemStack spool = (ItemStack) entry[1];
            double before = desc.getRemainingLengthMeters(spool);
            int x = X + 2 * i++;
            world.setBlock(new BlockPos(x, GROUND, z), Blocks.STONE.defaultBlockState(), 3);
            placeSixNode(world, player, "Classic Lamp Socket", x, z);
            if (!(element(world, x, z) instanceof mods.eln.sixnode.lampsocket.LampSocketElement socket)) {
                fail("no lamp socket for the '{}' spool", entry[0]);
                continue;
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, spool);
            boolean taken = socket.onBlockActivated(player, Direction.YN, 0.5f, 0.5f, 0.5f);
            ItemStack inSlot = socket.getInventory().getItem(mods.eln.sixnode.lampsocket.LampSocketContainer.CABLE_SLOT_ID);
            double slotLength = inSlot.isEmpty() ? -1 : desc.getRemainingLengthMeters(inSlot);
            double after = spool.isEmpty() ? 0 : desc.getRemainingLengthMeters(spool);
            boolean ok = taken && !inSlot.isEmpty() && Math.abs(slotLength - 1.0) < 1e-6 && Math.abs(before - after - 1.0) < 1e-6;
            check(ok, "spool '{}' into a lamp socket: taken={} slot={} m, spool {} -> {} m", entry[0], taken, slotLength, before, after);
        }
        checkCableSpoolGui(world, player, desc);
    }

    /**
     * The same through the GUI, without one: a spool dropped on a cable slot (what the client's
     * click sends as Slot.safeInsert) and a spool shift-clicked from the hotbar (quickMoveStack)
     * each cut one segment, and a plain 128 m spool goes into a lamp supply that already holds
     * segments as one more segment. A classic cable (no length) still moves whole.
     */
    private void checkCableSpoolGui(ServerLevel world, FakePlayer player, mods.eln.sixnode.electricalcable.UtilityCableDescriptor desc) {
        int z = Z + 9;
        // drop on the slot
        world.setBlock(new BlockPos(X, GROUND, z), Blocks.STONE.defaultBlockState(), 3);
        placeSixNode(world, player, "Classic Lamp Socket", X, z);
        if (element(world, X, z) instanceof mods.eln.sixnode.lampsocket.LampSocketElement socket) {
            mods.eln.GuiHandler.pendingContainerId = 1;
            var menu = socket.newContainer(Direction.YN, player);
            var slot = menu.slots.get(mods.eln.sixnode.lampsocket.LampSocketContainer.CABLE_SLOT_ID);
            ItemStack spool = desc.newCreativeTabStack();
            ItemStack back = slot.safeInsert(spool, 1);
            ItemStack inSlot = socket.getInventory().getItem(mods.eln.sixnode.lampsocket.LampSocketContainer.CABLE_SLOT_ID);
            boolean ok = !inSlot.isEmpty() && Math.abs(desc.getRemainingLengthMeters(inSlot) - 1.0) < 1e-6
                && back == spool && Math.abs(desc.getRemainingLengthMeters(spool) - 127.0) < 1e-6;
            check(ok, "spool dropped on the socket's GUI slot: slot={} m, spool left {} m", inSlot.isEmpty() ? -1 : desc.getRemainingLengthMeters(inSlot), desc.getRemainingLengthMeters(spool));
            // a classic cable through the same slot: moves whole, as before
            ItemStack lv = Eln.findItemStack("Low Voltage Cable", 1);
            socket.getInventory().setItem(mods.eln.sixnode.lampsocket.LampSocketContainer.CABLE_SLOT_ID, ItemStack.EMPTY);
            ItemStack lvBack = slot.safeInsert(lv, 1);
            check(lvBack.isEmpty() && !socket.getInventory().getItem(mods.eln.sixnode.lampsocket.LampSocketContainer.CABLE_SLOT_ID).isEmpty(),
                "a classic cable dropped on the socket's GUI slot moves whole: slot empty={}", socket.getInventory().getItem(1).isEmpty());
        } else fail("no lamp socket for the GUI spool check");

        // shift-click from the hotbar into a lamp supply: one segment is cut into the slot; a second
        // shift-click finds the slot full (segments do not stack: the item stacks to 1) and must not
        // cut anything more off the spool
        world.setBlock(new BlockPos(X + 2, GROUND, z), Blocks.STONE.defaultBlockState(), 3);
        placeSixNode(world, player, "120V Lamp Supply", X + 2, z);
        if (element(world, X + 2, z) instanceof mods.eln.sixnode.lampsupply.LampSupplyElement supply) {
            mods.eln.GuiHandler.pendingContainerId = 2;
            var menu = supply.newContainer(Direction.YN, player);
            int invSize = supply.getInventory().getContainerSize();
            ItemStack spool = desc.newCreativeTabStack();
            player.getInventory().setItem(0, spool);
            int hotbarSlot = invSize + 27;   // the container binds the player inventory rows first, then the hotbar
            menu.quickMoveStack(player, hotbarSlot);
            ItemStack inSlot = supply.getInventory().getItem(mods.eln.sixnode.lampsupply.LampSupplyContainer.cableSlotId);
            boolean ok = inSlot.getCount() == 1 && Math.abs(desc.getRemainingLengthMeters(inSlot) - 1.0) < 1e-6
                && Math.abs(desc.getRemainingLengthMeters(spool) - 127.0) < 1e-6;
            check(ok, "spool shift-clicked into a lamp supply: slot has {} x {} m, spool left {} m",
                inSlot.getCount(), inSlot.isEmpty() ? -1 : desc.getRemainingLengthMeters(inSlot), desc.getRemainingLengthMeters(spool));
            // the second shift-click: the slot is full, so vanilla moves the spool within the player's
            // inventory (a copy, elsewhere) and it must keep its length
            menu.quickMoveStack(player, hotbarSlot);
            inSlot = supply.getInventory().getItem(mods.eln.sixnode.lampsupply.LampSupplyContainer.cableSlotId);
            double moved = -1;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack st = player.getInventory().getItem(i);
                if (!st.isEmpty() && Eln.sixNodeItem.getDescriptor(st) == desc) moved = desc.getRemainingLengthMeters(st);
            }
            check(inSlot.getCount() == 1 && Math.abs(moved - 127.0) < 1e-6,
                "second shift-click with the slot full: slot has {}, spool still {} m", inSlot.getCount(), moved);
        } else fail("no lamp supply for the GUI spool check");
    }

    /**
     * The mechanical row: a creative source at 480 V feeds a shaft motor whose shaft runs through a
     * joint, a tachometer and a flywheel into a generator; the generator feeds a resistor to ground.
     * Every transparent node is placed looking south, so its front is north (the cables) and its
     * shaft runs east-west. Read at LAST_TICK, once the line has had time to spin up.
     */
    private void placeMechanical(ServerLevel world, FakePlayer player) {
        for (int dx = -2; dx <= 8; dx++) for (int dz = -1; dz <= 0; dz++) {
            world.setBlock(new BlockPos(X + dx, GROUND, MECH_Z + dz), Blocks.STONE.defaultBlockState(), 3);
            for (int dy = 1; dy <= 3; dy++) world.setBlock(new BlockPos(X + dx, GROUND + dy, MECH_Z + dz), Blocks.AIR.defaultBlockState(), 3);
        }
        placeSixNode(world, player, "Electrical Source", X - 1, MECH_Z - 1);
        placeSixNode(world, player, "High Voltage Cable", X, MECH_Z - 1);
        setVoltage(world, player, X - 1, MECH_Z - 1, 480.0);
        String[] line = {"Shaft Motor", "Joint", "Tachometer", "Flywheel", "Generator"};
        for (int i = 0; i < line.length; i++) placeTransparentNode(world, player, line[i], X + i, MECH_Z);
        placeSixNode(world, player, "High Voltage Cable", X + 4, MECH_Z - 1);
        placeSixNode(world, player, "Creative Power Resistor", X + 5, MECH_Z - 1, 90.0f);
        placeSixNode(world, player, "Ground Cable", X + 6, MECH_Z - 1);
        Eln.logger.info("{} mechanical row placed, source set to 480 V", PREFIX);

        // The large machines, on their own row further south: a 3x3x3 large shaft motor whose shaft
        // ports sit on the middle layer, a joint between them at that height, a large generator, and
        // the very-high-voltage supply and load on the floor in front of each (the front cell of the
        // bottom layer is free: that layer is only the shaft line).
        for (int dx = -3; dx <= 16; dx++) for (int dz = -2; dz <= 2; dz++) {
            world.setBlock(new BlockPos(X + dx, GROUND, LARGE_Z + dz), Blocks.STONE.defaultBlockState(), 3);
            for (int dy = 1; dy <= 4; dy++) world.setBlock(new BlockPos(X + dx, GROUND + dy, LARGE_Z + dz), Blocks.AIR.defaultBlockState(), 3);
        }
        placeSixNode(world, player, "Electrical Source", X - 1, LARGE_Z - 1);
        placeSixNode(world, player, "Very High Voltage Cable", X, LARGE_Z - 1);
        setVoltage(world, player, X - 1, LARGE_Z - 1, Eln.VVU);
        placeTransparentNode(world, player, "Large Shaft Motor", X, LARGE_Z);
        // the joint sits at shaft height, so on a block of its own (a transparent node needs a floor)
        world.setBlock(new BlockPos(X + 2, GROUND + 1, LARGE_Z), Blocks.STONE.defaultBlockState(), 3);
        placeTransparentNode(world, player, "Joint", X + 2, GROUND + 2, LARGE_Z);
        placeTransparentNode(world, player, "Large Generator", X + 4, LARGE_Z);
        placeSixNode(world, player, "Very High Voltage Cable", X + 4, LARGE_Z - 1);
        placeSixNode(world, player, "Creative Power Resistor", X + 5, LARGE_Z - 1, 90.0f);
        placeSixNode(world, player, "Ground Cable", X + 6, LARGE_Z - 1);
        if (element(world, X + 5, LARGE_Z - 1) instanceof mods.eln.item.IConfigurable resistor) {
            CompoundTag cfg = new CompoundTag();
            cfg.putDouble("resistance", 2000.0);
            resistor.readConfigTool(cfg, player);
        }
        // the large turbines beside them, on the same row (their shafts are not connected to anything)
        placeTransparentNode(world, player, "Large Steam Turbine", X + 9, LARGE_Z);
        placeTransparentNode(world, player, "Large Gas Turbine", X + 13, LARGE_Z);
        Eln.logger.info("{} large mechanical row placed, source set to {} V", PREFIX, Eln.VVU);

        // Every other shaft machine once, in a shaft line of its own: the models, and that each one
        // can be placed. The steam turbine gets a blade (a turbine without one draws no fan); the
        // radial motor's ghost footprint reaches two blocks east and one down, so the floor there is left open.
        for (int dx = -2; dx <= 16; dx++) for (int dz = -2; dz <= 2; dz++) {
            world.setBlock(new BlockPos(X + dx, GROUND, GALLERY_Z + dz), Blocks.STONE.defaultBlockState(), 3);
            for (int dy = 1; dy <= 3; dy++) world.setBlock(new BlockPos(X + dx, GROUND + dy, GALLERY_Z + dz), Blocks.AIR.defaultBlockState(), 3);
        }
        for (int dx = 11; dx <= 12; dx++) for (int dz = -1; dz <= 1; dz++) world.setBlock(new BlockPos(X + dx, GROUND, GALLERY_Z + dz), Blocks.AIR.defaultBlockState(), 3);
        // the fixed shaft holds the left half still; the clutch (no plate, slipping) leaves the hub, the
        // crank and the rolling machine as a free network the crank can turn by hand
        for (int i = 0; i < GALLERY.length; i++) if (GALLERY[i] != null) placeTransparentNode(world, player, GALLERY[i], X + i, GALLERY_Z);
        if (transparentElement(world, X + 1, GALLERY_Z) instanceof mods.eln.mechanical.TurbineElement turbine) {
            turbine.getInventory().setItem(mods.eln.mechanical.TurbineElement.BLADE_SLOT, Eln.findItemStack("Iron Turbine Blade", 1));
            turbine.inventoryChange(turbine.getInventory());
        } else fail("no steam turbine element in the gallery row");
        Eln.logger.info("{} shaft machine gallery placed", PREFIX);
        placeHeatGenerator(world, player);
    }

    /**
     * The other generator, the early-game one: a stone heat furnace with coal, its heat out of its
     * back into a copper thermal cable on the floor, into the hot side of a 48V turbine, whose
     * electrical output feeds a resistor to ground. The furnace is told to take fuel and given a
     * temperature target the way its GUI does it (the element's own packets).
     */
    private void placeHeatGenerator(ServerLevel world, FakePlayer player) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -1; dz <= 5; dz++) {
            world.setBlock(new BlockPos(X + dx, GROUND, HEAT_Z + dz), Blocks.STONE.defaultBlockState(), 3);
            for (int dy = 1; dy <= 2; dy++) world.setBlock(new BlockPos(X + dx, GROUND + dy, HEAT_Z + dz), Blocks.AIR.defaultBlockState(), 3);
        }
        // front is north (placed looking south): the furnace's heat leaves at its back, the turbine takes heat on its east side
        placeTransparentNode(world, player, "Stone Heat Furnace", X, HEAT_Z);
        placeSixNode(world, player, "Copper Thermal Cable", X, HEAT_Z + 1);
        placeTransparentNode(world, player, "48V Turbine", X - 1, HEAT_Z + 1);
        placeSixNode(world, player, "Low Voltage Cable", X - 1, HEAT_Z + 2);
        placeSixNode(world, player, "Creative Power Resistor", X - 1, HEAT_Z + 3);
        placeSixNode(world, player, "Ground Cable", X - 1, HEAT_Z + 4);
        if (transparentElement(world, X, HEAT_Z) instanceof mods.eln.transparentnode.heatfurnace.HeatFurnaceElement furnace) {
            furnace.getInventory().setItem(mods.eln.transparentnode.heatfurnace.HeatFurnaceContainer.combustibleId, new ItemStack(net.minecraft.world.item.Items.COAL, 64));
            furnace.inventoryChange(furnace.getInventory());
            elementPacket(furnace, mods.eln.transparentnode.heatfurnace.HeatFurnaceElement.unserializeToogleTakeFuelId, null);
            // without a regulator in its slot the furnace burns at the gain its GUI slider sets; the
            // 48V turbine's nominal delta is 250 C and it blows up at twice that, so keep the fire low
            elementPacket(furnace, mods.eln.transparentnode.heatfurnace.HeatFurnaceElement.unserializeGain, 0.3f);
            Eln.logger.info("{} heat furnace loaded with coal, taking fuel, gain 0.3", PREFIX);
        } else fail("no heat furnace element");
    }

    /** What a GUI sends the element: a packet type byte and, for some, a float. */
    private void elementPacket(mods.eln.node.transparent.TransparentNodeElement element, byte type, Float value) {
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            var out = new java.io.DataOutputStream(bytes);
            out.writeByte(type);
            if (value != null) out.writeFloat(value);
            element.networkUnserialize(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
        } catch (java.io.IOException e) {
            fail("element packet", e);
        }
    }

    /** The heat generator: the furnace hot, the turbine turning that heat into volts and amps through the resistor. */
    private void verifyHeatGenerator(ServerLevel world) {
        var furnace = transparentElement(world, X, HEAT_Z);
        var turbine = transparentElement(world, X - 1, HEAT_Z + 1);
        SixNodeElement thermalCable = element(world, X, HEAT_Z + 1);
        if (!(furnace instanceof mods.eln.transparentnode.heatfurnace.HeatFurnaceElement f) || !(turbine instanceof mods.eln.transparentnode.turbine.TurbineElement t)) {
            BlockPos pos = new BlockPos(X - 1, GROUND + 1, HEAT_Z + 1);
            fail("heat generator missing after {}: furnace={} turbine={} (block {} entity {} node {})", placing ? "placement" : "restart", furnace, turbine,
                BuiltInRegistries.BLOCK.getKey(world.getBlockState(pos).getBlock()), world.getBlockEntity(pos),
                NodeManager.instance.getNodeFromCoordonate(new Coordinate(pos.getX(), pos.getY(), pos.getZ(), world)));
            return;
        }
        double furnaceT = f.thermalLoad.getTemperature();
        Eln.logger.info("{} furnace {} C, thermal cable {}, turbine {} / {}", PREFIX, furnaceT, thermalCable == null ? null : thermalCable.thermoMeterString(),
            t.thermoMeterString(Direction.XP), t.multiMeterString(Direction.ZN));
        double u = 0, i = 0;
        if (element(world, X - 1, HEAT_Z + 2) instanceof mods.eln.sixnode.electricalcable.ElectricalCableElement cable) {
            u = cable.electricalLoad.getVoltage();
            i = cable.electricalLoad.getCurrent();
        }
        check(furnaceT > 80 && u > 20 && i > 0.1, "heat generator: furnace at {} C, 48V turbine delivers {} V {} A into the resistor", furnaceT, u, i);
    }

    /** The spin-up, every second: the motor's and the generator's meters (speed, energy, volts, amps); and a hand on the crank. */
    private void traceMechanical() {
        ServerLevel world = world();
        if (transparentElement(world, CRANK_X, GALLERY_Z) instanceof mods.eln.mechanical.CrankableShaftElement crank) {
            FakePlayer player = FakePlayerFactory.getMinecraft(world);
            for (int i = 0; i < 5; i++) crank.onBlockActivated(player, Direction.ZN, 0.5f, 0.5f, 0.5f);
            Eln.logger.info("{} t={} crank: {}", PREFIX, ticks, crank.multiMeterString(Direction.ZN));
        }
        var motor = transparentElement(world, X, MECH_Z);
        var generator = transparentElement(world, X + 4, MECH_Z);
        if (motor == null || generator == null) return;
        Eln.logger.info("{} t={} motor: {} | generator: {}", PREFIX, ticks, motor.multiMeterString(Direction.ZN), generator.multiMeterString(Direction.ZN));
        BlockPos turbinePos = new BlockPos(X - 1, GROUND + 1, HEAT_Z + 1);
        var heatTurbine = transparentElement(world, X - 1, HEAT_Z + 1);
        Eln.logger.info("{} t={} heat turbine: block={} element={} {}", PREFIX, ticks, BuiltInRegistries.BLOCK.getKey(world.getBlockState(turbinePos).getBlock()), heatTurbine,
            heatTurbine == null ? "" : heatTurbine.multiMeterString(Direction.ZN) + heatTurbine.thermoMeterString(Direction.XP));
        var largeMotor = transparentElement(world, X, LARGE_Z);
        var largeGenerator = transparentElement(world, X + 4, LARGE_Z);
        if (largeMotor == null || largeGenerator == null) return;
        Eln.logger.info("{} t={} large motor: {} | large generator: {}", PREFIX, ticks, largeMotor.multiMeterString(Direction.ZN), largeGenerator.multiMeterString(Direction.ZN));
        if (ticks == 40 || ticks == 240) {
            StringBuilder sb = new StringBuilder();
            for (int y = GROUND + 1; y <= GROUND + 3; y++) for (int z = LARGE_Z - 2; z <= LARGE_Z + 2; z++) for (int x = X - 2; x <= X + 6; x++) {
                BlockState state = world.getBlockState(new BlockPos(x, y, z));
                if (state.isAir()) continue;
                NodeBase node = NodeManager.instance.getNodeFromCoordonate(new Coordinate(x, y, z, world));
                sb.append(String.format("(%d,%d,%d)=%s/%s ", x - X, y - GROUND, z - LARGE_Z, BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(), node == null ? "-" : node.getClass().getSimpleName()));
            }
            Eln.logger.info("{} t={} large row cells: {}", PREFIX, ticks, sb);
        }
        var supplyCable = element(world, X, LARGE_Z - 1);
        var supply = element(world, X - 1, LARGE_Z - 1);
        Eln.logger.info("{} t={} large supply: {} | cable: {} block={} connections={}", PREFIX, ticks,
            supply == null ? null : supply.multiMeterString(), supplyCable == null ? null : supplyCable.multiMeterString(),
            BuiltInRegistries.BLOCK.getKey(world.getBlockState(new BlockPos(X, GROUND + 1, LARGE_Z - 1)).getBlock()),
            supplyCable == null ? -1 : supplyCable.sixNode.nodeConnectionList.size());
    }

    private mods.eln.node.transparent.TransparentNodeElement transparentElement(ServerLevel world, int x, int z) {
        NodeBase node = NodeManager.instance.getNodeFromCoordonate(new Coordinate(x, GROUND + 1, z, world));
        return node instanceof mods.eln.node.transparent.TransparentNode t ? t.element : null;
    }

    /**
     * The shaft line must be one network turning near the motor's nominal speed, and the generator
     * on its far end must be putting volts and amps into the resistor.
     */
    private void verifyMechanical() {
        ServerLevel world = world();
        String[] names = {"Shaft Motor", "Joint", "Tachometer", "Flywheel", "Generator"};
        mods.eln.mechanical.SimpleShaftElement[] line = new mods.eln.mechanical.SimpleShaftElement[names.length];
        for (int i = 0; i < names.length; i++) {
            var element = transparentElement(world, X + i, MECH_Z);
            if (element instanceof mods.eln.mechanical.SimpleShaftElement shaft) line[i] = shaft;
            else {
                fail("mechanical row: no '{}' at ({},{},{}) after {}: {}", names[i], X + i, GROUND + 1, MECH_Z, placing ? "placement" : "restart", element);
                return;
            }
            Eln.logger.info("{} {} meter: {} shaft={}", PREFIX, names[i], line[i].multiMeterString(Direction.ZN), System.identityHashCode(line[i].getShaft()));
        }
        SixNodeElement load = element(world, X + 5, MECH_Z - 1);
        String loadMeter = load == null ? null : load.multiMeterString();
        Eln.logger.info("{} generator load meter: {}", PREFIX, loadMeter);

        var motor = (mods.eln.mechanical.MotorElement) line[0];
        var generator = (mods.eln.mechanical.GeneratorElement) line[4];
        boolean oneNetwork = true;
        for (var element : line) oneNetwork &= element.getShaft() == motor.getShaft();
        double rads = motor.getShaft().getRads();
        check(oneNetwork, "shaft line is one network: {}", oneNetwork);
        check(rads > 0.75 * motor.getDesc().getNominalRads(), "shaft line turns at {} rad/s (motor nominal {})", rads, motor.getDesc().getNominalRads());
        // the generator's output, read on the cable between it and the resistor
        double u = 0, i = 0;
        if (element(world, X + 4, MECH_Z - 1) instanceof mods.eln.sixnode.electricalcable.ElectricalCableElement cable) {
            u = cable.electricalLoad.getVoltage();
            i = cable.electricalLoad.getCurrent();
        }
        check(u > 0.75 * generator.getDesc().getNominalU() && i > 0.1,
            "generator delivers {} V {} A into the resistor (nominal {} V; resistor meter {})", u, i, generator.getDesc().getNominalU(), loadMeter);
        verifyLargeMechanical(world);
        verifyGallery(world);
        verifyHeatGenerator(world);
    }

    /**
     * The restart run ends by breaking shafts, the way a player does (the block goes, the node
     * follows): the flywheel out of the middle of the small row, which has to split that network,
     * and the large motor, a multiblock whose ghost blocks and ghost shaft nodes have to go with it.
     */
    private void breakShafts() {
        ServerLevel world = world();
        world.destroyBlock(new BlockPos(X + 3, GROUND + 1, MECH_Z), false);
        world.destroyBlock(new BlockPos(X, GROUND + 1, LARGE_Z), false);
        Eln.logger.info("{} broke the flywheel of the small row and the large shaft motor", PREFIX);
    }

    private void verifyBrokenShafts() {
        ServerLevel world = world();
        var motor = transparentElement(world, X, MECH_Z);
        var generator = transparentElement(world, X + 4, MECH_Z);
        var flywheel = transparentElement(world, X + 3, MECH_Z);
        if (!(motor instanceof mods.eln.mechanical.MotorElement m) || !(generator instanceof mods.eln.mechanical.GeneratorElement g)) {
            fail("small row after breaking the flywheel: motor={} generator={}", motor, generator);
            return;
        }
        Eln.logger.info("{} after the break: motor {} | generator {}", PREFIX, m.multiMeterString(Direction.ZN), g.multiMeterString(Direction.ZN));
        boolean split = flywheel == null && m.getShaft() != g.getShaft();
        boolean motorRuns = m.getShaft().getRads() > 0.75 * m.getDesc().getNominalRads();
        boolean generatorCoasts = g.getShaft().getRads() < 0.5 * m.getDesc().getNominalRads();
        check(split && motorRuns && generatorCoasts, "flywheel gone={} network split={}; motor side {} rad/s, generator side {} rad/s (spinning down into its load)",
            flywheel == null, m.getShaft() != g.getShaft(), m.getShaft().getRads(), g.getShaft().getRads());

        // the large motor: nothing of it left, the joint and the large generator still one running network
        StringBuilder left = new StringBuilder();
        for (int y = GROUND + 1; y <= GROUND + 3; y++) for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            if (y == GROUND + 1 && dz != 0) continue;   // the bottom layer is only the shaft line; the supply cables sit in front of it
            BlockPos pos = new BlockPos(X + dx, y, LARGE_Z + dz);
            BlockState state = world.getBlockState(pos);
            NodeBase node = NodeManager.instance.getNodeFromCoordonate(new Coordinate(pos.getX(), pos.getY(), pos.getZ(), world));
            if (!state.isAir() || node != null) left.append(String.format("(%d,%d,%d)=%s/%s ", dx, y - GROUND, dz, BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(), node));
        }
        NodeBase jointNode = NodeManager.instance.getNodeFromCoordonate(new Coordinate(X + 2, GROUND + 2, LARGE_Z, world));
        var joint = jointNode instanceof mods.eln.node.transparent.TransparentNode t ? t.element : null;
        var largeGenerator = transparentElement(world, X + 4, LARGE_Z);
        boolean rest = joint instanceof mods.eln.mechanical.StraightJointElement j && largeGenerator instanceof mods.eln.mechanical.GeneratorElement lg
            && j.getShaft() == lg.getShaft() && lg.getShaft().getRads() > 50;
        check(left.length() == 0 && rest, "large motor broken: cells left={}; joint and large generator still one network turning: {}", left, rest);
    }

    /** The gallery: every machine still there (placement accepted, nothing dropped itself). */
    private void verifyGallery(ServerLevel world) {
        StringBuilder missing = new StringBuilder();
        for (int i = 0; i < GALLERY.length; i++) {
            if (GALLERY[i] == null) continue;
            var element = transparentElement(world, X + i, GALLERY_Z);
            if (element == null || !GALLERY[i].equals(element.transparentNodeDescriptor.name)) missing.append(GALLERY[i]).append(" (").append(element).append(") ");
        }
        for (String[] large : new String[][]{{"Large Steam Turbine", "9"}, {"Large Gas Turbine", "13"}}) {
            var element = transparentElement(world, X + Integer.parseInt(large[1]), LARGE_Z);
            if (element == null || !large[0].equals(element.transparentNodeDescriptor.name)) missing.append(large[0]).append(" (").append(element).append(") ");
        }
        check(missing.length() == 0, "shaft machine gallery present after {}: missing={}", placing ? "placement" : "restart", missing);
        // the crank has been turned five times a second since placement (traceMechanical): its free
        // half of the row (hub, crank, rolling machine; the clutch slips, the fixed shaft holds the rest) turns
        var crank = transparentElement(world, CRANK_X, GALLERY_Z);
        var hub = transparentElement(world, CRANK_X - 1, GALLERY_Z);
        var fixed = transparentElement(world, X, GALLERY_Z);
        if (crank instanceof mods.eln.mechanical.CrankableShaftElement c && hub instanceof mods.eln.mechanical.VerticalHubElement h && fixed instanceof mods.eln.mechanical.FixedShaftElement f) {
            check(c.getShaft().getRads() > 1 && h.getShaft() == c.getShaft() && f.getShaft() != c.getShaft() && f.getShaft().getRads() == 0,
                "crank shaft turned by hand: {} rad/s, hub on the same network={}, fixed shaft's network still={} ({} rad/s)",
                c.getShaft().getRads(), h.getShaft() == c.getShaft(), f.getShaft() != c.getShaft(), f.getShaft().getRads());
        } else fail("gallery: crank={} hub={} fixed shaft={}", crank, hub, fixed);
    }

    /** The large row: motor, joint (at shaft height) and generator one network, turning, delivering. */
    private void verifyLargeMechanical(ServerLevel world) {
        var motorElement = transparentElement(world, X, LARGE_Z);
        var jointElement = transparentElement(world, X + 2, LARGE_Z);
        var generatorElement = transparentElement(world, X + 4, LARGE_Z);
        NodeBase joint = NodeManager.instance.getNodeFromCoordonate(new Coordinate(X + 2, GROUND + 2, LARGE_Z, world));
        var jointAtHeight = joint instanceof mods.eln.node.transparent.TransparentNode t ? t.element : null;
        if (!(motorElement instanceof mods.eln.mechanical.MotorElement motor) || !(generatorElement instanceof mods.eln.mechanical.GeneratorElement generator)
            || !(jointAtHeight instanceof mods.eln.mechanical.StraightJointElement jointShaft)) {
            fail("large row missing after {}: motor={} joint={} generator={}", placing ? "placement" : "restart", motorElement, jointAtHeight, generatorElement);
            return;
        }
        Eln.logger.info("{} large motor meter: {} shaft={}", PREFIX, motor.multiMeterString(Direction.ZN), System.identityHashCode(motor.getShaft()));
        Eln.logger.info("{} large joint meter: {} shaft={}", PREFIX, jointShaft.multiMeterString(Direction.ZN), System.identityHashCode(jointShaft.getShaft()));
        Eln.logger.info("{} large generator meter: {} shaft={}", PREFIX, generator.multiMeterString(Direction.ZN), System.identityHashCode(generator.getShaft()));
        boolean oneNetwork = motor.getShaft() == jointShaft.getShaft() && jointShaft.getShaft() == generator.getShaft();
        double rads = motor.getShaft().getRads();
        check(oneNetwork, "large shaft line is one network: {}", oneNetwork);
        check(rads > 0.75 * motor.getDesc().getNominalRads(), "large shaft line turns at {} rad/s (motor nominal {})", rads, motor.getDesc().getNominalRads());
        double u = 0, i = 0;
        if (element(world, X + 4, LARGE_Z - 1) instanceof mods.eln.sixnode.electricalcable.ElectricalCableElement cable) {
            u = cable.electricalLoad.getVoltage();
            i = cable.electricalLoad.getCurrent();
        }
        SixNodeElement load = element(world, X + 5, LARGE_Z - 1);
        check(u > 0.75 * generator.getDesc().getNominalU() && i > 0.1,
            "large generator delivers {} V {} A into the resistor (nominal {} V; resistor meter {})", u, i, generator.getDesc().getNominalU(), load == null ? null : load.multiMeterString());
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
        placeTransparentNode(world, player, descriptorName, x, GROUND + 1, z);
    }

    private void placeTransparentNode(ServerLevel world, FakePlayer player, String descriptorName, int x, int y, int z) {
        player.setYRot(0f);
        player.setYHeadRot(0f);
        ItemStack stack = Eln.findItemStack(descriptorName, 1);
        if (stack == null || stack.isEmpty()) {
            fail("no item stack named '{}'", descriptorName);
            return;
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
        boolean placed = Eln.transparentNodeItem.placeBlockAt(stack, player, world, new BlockPos(x, y, z), net.minecraft.core.Direction.UP);
        BlockState state = world.getBlockState(new BlockPos(x, y, z));
        Eln.logger.info("{} place '{}' at ({},{},{}) -> {} block={}", PREFIX, descriptorName, x, y, z, placed, BuiltInRegistries.BLOCK.getKey(state.getBlock()));
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
