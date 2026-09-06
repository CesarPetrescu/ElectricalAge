package mods.eln.modern.network;

import java.util.Locale;
import mods.eln.modern.ElectricalAgeModern;
import mods.eln.sim.network.CircuitNetwork;
import mods.eln.sim.network.GridTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Durable device-owned differential history; the level owns transient matrix/cache state. */
public final class CircuitDeviceBlockEntity extends BlockEntity {
    private static final String STATE="eln_device";
    private double voltage,current,energy;
    private boolean powered=true;
    private String fault;
    private Tag rejectedState;
    private long steps;
    public CircuitDeviceBlockEntity(BlockPos pos,BlockState state) { super(ElectricalAgeModern.CIRCUIT_DEVICE_ENTITY.get(),pos,state); }
    public GridTopology.Kind kind() { return ((CircuitDeviceBlock)getBlockState().getBlock()).kind(); }
    public GridTopology.Device descriptor() {
        return new GridTopology.Device(new GridTopology.Cell(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ()),kind(),
            GridTopology.Side.valueOf(getBlockState().getValue(CircuitDeviceBlock.FACING).name()),powered,kind()==GridTopology.Kind.CAPACITOR?voltage:0);
    }
    public double voltage() { return voltage; }
    public double current() { return current; }
    public double energy() { return energy; }
    public boolean powered() { return powered; }
    public boolean hasFault() { return fault!=null; }
    public long simulatedSteps() { return steps; }
    private boolean serverActive() { return level!=null && !level.isClientSide && !isRemoved(); }
    public void togglePower() {
        if(!serverActive() || hasFault() || kind()!=GridTopology.Kind.SOURCE)return;
        powered=!powered;setChanged();sync();
    }
    public void reset() {
        if(!serverActive())return;
        voltage=current=energy=0;steps=0;powered=true;fault=null;rejectedState=null;
        LevelCircuitManager.invalidate(level);setChanged();sync();
    }
    public void accept(CircuitNetwork.Reading reading) {
        if(!serverActive() || hasFault())return;
        if(reading.faulted() || !Double.isFinite(reading.voltage()) || Math.abs(reading.voltage())>1000
                || !Double.isFinite(reading.current()) || !Double.isFinite(reading.energy()) || reading.energy()<0) {
            latchFault("Circuit fault or safe operating limit reached");return;
        }
        boolean changed=reading.voltage()!=voltage;
        voltage=reading.voltage();current=reading.current();energy=reading.energy();steps++;
        if(changed && kind()==GridTopology.Kind.CAPACITOR)setChanged();
        boolean lit=kind()==GridTopology.Kind.LOAD && Math.abs(voltage*current)>.1;
        if(getBlockState().getValue(CircuitDeviceBlock.LIT)!=lit)
            level.setBlock(worldPosition,getBlockState().setValue(CircuitDeviceBlock.LIT,lit),Block.UPDATE_CLIENTS);
        if(steps%10==0)sync();
    }
    public void latchFault(String reason) {
        if(!serverActive() || hasFault())return;
        fault=reason;current=0;LevelCircuitManager.invalidate(level);setChanged();
        if(getBlockState().getValue(CircuitDeviceBlock.LIT))level.setBlock(worldPosition,getBlockState().setValue(CircuitDeviceBlock.LIT,false),Block.UPDATE_CLIENTS);
        sync();
    }
    /** A device not heartbeating this tick is out of the simulated graph, not an implicit ground. */
    void pause() { current=0; }
    private void sync() { if(serverActive())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS); }
    @Override public void onChunkUnloaded() { LevelCircuitManager.forget(this);super.onChunkUnloaded(); }
    @Override public void setRemoved() { LevelCircuitManager.forget(this);super.setRemoved(); }
    public String measurementText() {
        if(hasFault())return "ELN: "+fault+". Sneak-right-click resets this device.";
        if(kind()==GridTopology.Kind.WIRE)return String.format(Locale.ROOT,"ELN wire | largest arm current %.5f A | 0.05 ohm per arm",current);
        return String.format(Locale.ROOT,"ELN %s | %s | front-to-back %.5f V | %.5f A | %.5f J",kind().name().toLowerCase(Locale.ROOT),powered?"ON":"OFF",voltage,current,energy);
    }
    private CompoundTag stateTag() {
        CompoundTag tag=new CompoundTag();tag.putInt("schema",1);tag.putString("kind",kind().name());
        tag.putDouble("voltage",kind()==GridTopology.Kind.CAPACITOR?voltage:0);tag.putBoolean("powered",powered);tag.putBoolean("faulted",hasFault());return tag;
    }
    private static boolean strictBoolean(CompoundTag tag,String key) {
        if(!tag.contains(key,Tag.TAG_BYTE) || (tag.getByte(key)!=0 && tag.getByte(key)!=1))throw new IllegalArgumentException("Invalid "+key);
        return tag.getBoolean(key);
    }
    @Override public void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);tag.put(STATE,rejectedState==null?stateTag():rejectedState.copy());
    }
    @Override public void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);LevelCircuitManager.invalidate(level);
        voltage=current=energy=0;steps=0;fault=null;rejectedState=null;powered=true;
        if(!tag.contains(STATE))return;
        try {
            if(!tag.contains(STATE,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Device state is not a compound");
            CompoundTag data=tag.getCompound(STATE);
            if(!data.contains("schema",Tag.TAG_INT) || data.getInt("schema")!=1)throw new IllegalArgumentException("Unknown device schema");
            if(!data.contains("kind",Tag.TAG_STRING) || !data.getString("kind").equals(kind().name()))throw new IllegalArgumentException("Wrong device kind");
            if(!data.contains("voltage",Tag.TAG_DOUBLE))throw new IllegalArgumentException("Missing voltage");
            double volts=data.getDouble("voltage");
            if(!Double.isFinite(volts) || Math.abs(volts)>1000 || (kind()!=GridTopology.Kind.CAPACITOR && volts!=0))throw new IllegalArgumentException("Invalid stored voltage");
            boolean enabled=strictBoolean(data,"powered"), frozen=strictBoolean(data,"faulted");
            voltage=volts;powered=enabled;energy=kind()==GridTopology.Kind.CAPACITOR?.5*volts*volts:0;
            if(frozen)fault="Persisted circuit fault";
        }catch(IllegalArgumentException invalid){rejectedState=tag.get(STATE).copy();powered=false;fault=invalid.getMessage();}
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag=new CompoundTag();tag.putDouble("voltage",voltage);tag.putDouble("current",current);tag.putDouble("energy",energy);
        tag.putBoolean("powered",powered);tag.putBoolean("faulted",hasFault());return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries) {
        // Decode entirely before assignment. Disk evidence and graph objects are never sent.
        try {
            if(!tag.contains("voltage",Tag.TAG_DOUBLE)||!tag.contains("current",Tag.TAG_DOUBLE)||!tag.contains("energy",Tag.TAG_DOUBLE))throw new IllegalArgumentException("Missing measurements");
            double v=tag.getDouble("voltage"),i=tag.getDouble("current"),e=tag.getDouble("energy");
            if(!Double.isFinite(v)||Math.abs(v)>1000||!Double.isFinite(i)||!Double.isFinite(e)||e<0)throw new IllegalArgumentException("Invalid measurements");
            boolean p=strictBoolean(tag,"powered"),f=strictBoolean(tag,"faulted");
            voltage=v;current=i;energy=e;powered=p;fault=f?"Server has frozen this circuit":null;
        }catch(IllegalArgumentException invalid){current=0;fault="Invalid measurement snapshot";}
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void onDataPacket(Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries){
        if(packet.getPos().equals(worldPosition)&&packet.getType()==getType()&&packet.getTag()!=null)handleUpdateTag(packet.getTag(),registries);
    }
}
