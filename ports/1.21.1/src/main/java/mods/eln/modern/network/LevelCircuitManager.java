package mods.eln.modern.network;

import java.util.*;
import mods.eln.modern.ElectricalAgeModern;
import mods.eln.sim.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** One thread-confined, bounded manager per server level. No client/global electrical graph.
 * Vanilla block-entity tick heartbeats are the authority for circuit participation. A mere
 * loaded chunk is insufficient; losing ticks removes its ports while preserving capacitor V.
 */
@EventBusSubscriber(modid=ElectricalAgeModern.MODID)
public final class LevelCircuitManager {
    private static final Map<ServerLevel,Manager> LEVELS=new IdentityHashMap<>();
    private LevelCircuitManager() { }
    public static void touch(CircuitDeviceBlockEntity device) {
        if(!(device.getLevel() instanceof ServerLevel level) || device.isRemoved())return;
        requireThread(level);
        Manager manager=LEVELS.computeIfAbsent(level,k->new Manager());
        manager.begin(level.getGameTime());
        if(device.hasFault())return;
        if(manager.touched.size()>=GridTopology.MAX_DEVICES && !manager.touched.containsKey(device.getBlockPos())) {
            device.latchFault("Prototype limit: 64 active devices per level");return;
        }
        manager.touched.put(device.getBlockPos().immutable(),device);
    }
    public static void invalidate(Level world) { if(world instanceof ServerLevel level){requireThread(level);Manager manager=LEVELS.get(level);if(manager!=null)manager.dirty=true;} }
    public static void forget(CircuitDeviceBlockEntity device) {
        if(!(device.getLevel() instanceof ServerLevel level))return;
        requireThread(level);Manager manager=LEVELS.get(level);
        if(manager!=null){manager.touched.remove(device.getBlockPos(),device);manager.dirty=true;}
    }
    private static void requireThread(ServerLevel level){if(!level.getServer().isSameThread())throw new IllegalStateException("Circuit graph accessed off server thread");}
    @SubscribeEvent public static void postTick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level))return;
        Manager manager=LEVELS.get(level);if(manager==null)return;
        requireThread(level);manager.begin(level.getGameTime());manager.step();
        if(manager.touched.isEmpty()){manager.close();LEVELS.remove(level);}
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level){Manager manager=LEVELS.remove(level);if(manager!=null)manager.close();}
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event){for(Manager manager:LEVELS.values())manager.close();LEVELS.clear();}
    private record Identity(CircuitDeviceBlockEntity entity,GridTopology.Kind kind,GridTopology.Side facing) { }
    private static final class Manager {
        final Map<BlockPos,CircuitDeviceBlockEntity> touched=new LinkedHashMap<>();
        Map<BlockPos,Identity> previous=Map.of();
        CircuitNetwork circuit;
        long heartbeat=Long.MIN_VALUE;
        boolean dirty=true;
        void begin(long tick){if(tick!=heartbeat){heartbeat=tick;touched.clear();}}
        void close(){if(circuit!=null){circuit.close();circuit=null;}}
        void step(){
            Map<BlockPos,Identity> next=new HashMap<>();List<GridTopology.Device> descriptors=new ArrayList<>();
            for(var entry:touched.entrySet()){
                CircuitDeviceBlockEntity entity=entry.getValue();if(entity.hasFault()||entity.isRemoved())continue;
                GridTopology.Device descriptor=entity.descriptor();descriptors.add(descriptor);next.put(entry.getKey(),new Identity(entity,descriptor.kind(),descriptor.facing()));
            }
            for(var entry:previous.entrySet())if(!next.containsKey(entry.getKey()))entry.getValue().entity().pause();
            try {
                if(dirty||!previous.equals(next)){
                    close();circuit=GridTopology.compile(descriptors);previous=Map.copyOf(next);dirty=false;
                }
                if(circuit==null)return;
                for(GridTopology.Device descriptor:descriptors)if(descriptor.kind()==GridTopology.Kind.SOURCE)
                    circuit.setSourceVoltage(descriptor.bodyId(),descriptor.powered()?GridTopology.SOURCE_VOLTS:0);
                circuit.step();
                for(GridTopology.Device descriptor:descriptors){
                    CircuitDeviceBlockEntity entity=touched.get(new BlockPos(descriptor.cell().x(),descriptor.cell().y(),descriptor.cell().z()));
                    CircuitNetwork.Reading reading;
                    if(descriptor.kind()==GridTopology.Kind.WIRE){
                        double max=0;boolean faulted=false;
                        for(GridTopology.Side side:GridTopology.Side.values()){
                            var arm=circuit.reading(descriptor.cell().key()+"/wire/"+side);max=Math.max(max,Math.abs(arm.current()));faulted|=arm.faulted();
                        }
                        reading=new CircuitNetwork.Reading(0,max,0,faulted);
                    }else{
                        reading=circuit.reading(descriptor.bodyId());
                        if(descriptor.kind()==GridTopology.Kind.SOURCE)reading=new CircuitNetwork.Reading(
                            reading.voltage()-circuit.reading(descriptor.cell().key()+"/resistance").voltage(),reading.current(),0,reading.faulted());
                    }
                    entity.accept(reading);
                }
            }catch(RuntimeException failure){
                close();for(Identity value:next.values())value.entity().latchFault("Circuit topology rejected; inspect server log and reset");
                ElectricalAgeModern.LOGGER.error("ELN bounded circuit rebuild/step failed",failure);
                previous=Map.copyOf(next);dirty=false;
            }
        }
    }
}
