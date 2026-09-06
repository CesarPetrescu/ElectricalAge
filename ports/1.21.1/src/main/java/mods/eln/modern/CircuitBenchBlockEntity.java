package mods.eln.modern;

import java.util.Locale;
import mods.eln.sim.bench.RcCircuit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Server-only simulation ownership. Clients hold a small immutable measurement snapshot.
 * Persisted state is versioned. Malformed/future state freezes the bench and is retained
 * verbatim on disk until the player explicitly resets it; it is not broadcast to clients.
 */
public final class CircuitBenchBlockEntity extends BlockEntity {
    private static final String STATE = "eln_state";
    private RcCircuit.Snapshot snapshot = new RcCircuit.Snapshot(0, true);
    private RcCircuit circuit;
    private Tag rejectedState;
    private String fault;
    private long simulatedSteps;

    public CircuitBenchBlockEntity(BlockPos pos, BlockState state) { super(ElectricalAgeModern.CIRCUIT_BENCH_ENTITY.get(), pos, state); }
    public void serverTick() {
        if (level == null || level.isClientSide || isRemoved() || fault != null) return;
        try {
            if (circuit == null) { circuit = new RcCircuit(); circuit.restore(snapshot); }
            double previous = snapshot.voltage();
            circuit.step();
            snapshot = circuit.snapshot();
            simulatedSteps++;
            if (snapshot.voltage() != previous) setChanged();
            if (simulatedSteps % 20 == 0) sync();
        } catch (RuntimeException exception) {
            fault = "Simulation fault; reset required";
            closeCircuit();
            ElectricalAgeModern.LOGGER.error("ELN circuit bench fault at {}", worldPosition, exception);
            setChanged(); sync();
        }
    }
    public void togglePower() {
        if (level == null || level.isClientSide || fault != null) return;
        snapshot = new RcCircuit.Snapshot(snapshot.voltage(), !snapshot.powered());
        if (circuit != null) circuit.setPowered(snapshot.powered());
        setChanged(); sync();
    }
    public void reset() {
        if (level == null || level.isClientSide) return;
        closeCircuit();
        snapshot = new RcCircuit.Snapshot(0, true);
        rejectedState = null; fault = null; simulatedSteps = 0;
        setChanged(); sync();
    }
    private void sync() {
        if (level != null && !level.isClientSide && !isRemoved()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    private void closeCircuit() { if (circuit != null) { circuit.close(); circuit = null; } }
    @Override public void setRemoved() { closeCircuit(); super.setRemoved(); }
    public double voltage() { return snapshot.voltage(); }
    public double energy() { return .5 * RcCircuit.CAPACITANCE * voltage() * voltage(); }
    public boolean powered() { return snapshot.powered(); }
    public boolean hasFault() { return fault != null; }
    public long simulatedSteps() { return simulatedSteps; }
    public String measurementText() {
        if (fault != null) return "ELN: " + fault + ". Sneak-right-click to reset.";
        double current = ((powered() ? RcCircuit.SOURCE_VOLTS : 0) - voltage()) / RcCircuit.RESISTANCE;
        return String.format(Locale.ROOT, "ELN bench | source %s | capacitor %.4f V | resistor %.4f A | %.4f J", powered() ? "ON" : "OFF", voltage(), current, energy());
    }
    private CompoundTag stateTag() {
        CompoundTag state = new CompoundTag();
        state.putInt("schema", 1); state.putDouble("voltage", voltage()); state.putBoolean("powered", powered());
        return state;
    }
    private static RcCircuit.Snapshot decode(CompoundTag state) {
        if (!state.contains("schema", Tag.TAG_INT) || state.getInt("schema") != 1) throw new IllegalArgumentException("Unsupported bench state schema");
        if (!state.contains("voltage", Tag.TAG_DOUBLE) || !state.contains("powered", Tag.TAG_BYTE)) throw new IllegalArgumentException("Missing or mistyped bench state");
        return new RcCircuit.Snapshot(state.getDouble("voltage"), state.getBoolean("powered"));
    }
    @Override public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(STATE, rejectedState == null ? stateTag() : rejectedState.copy());
    }
    @Override public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        closeCircuit(); simulatedSteps = 0; rejectedState = null; fault = null;
        if (!tag.contains(STATE)) { snapshot = new RcCircuit.Snapshot(0, true); return; }
        try {
            if (!tag.contains(STATE, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Bench state is not a compound");
            snapshot = decode(tag.getCompound(STATE));
        } catch (IllegalArgumentException invalid) {
            rejectedState = tag.get(STATE).copy();
            snapshot = new RcCircuit.Snapshot(0, false);
            fault = invalid.getMessage();
        }
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag view = new CompoundTag(); view.put(STATE, stateTag()); view.putBoolean("eln_fault", hasFault()); return view;
    }
    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        try { snapshot = decode(tag.getCompound(STATE)); fault = tag.getBoolean("eln_fault") ? "Server has frozen this bench" : null; }
        catch (IllegalArgumentException invalid) { snapshot = new RcCircuit.Snapshot(0,false); fault = "Invalid measurement snapshot"; }
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag(), registries);
    }
}
