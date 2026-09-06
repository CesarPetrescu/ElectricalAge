package mods.eln.modern.gametest;

import mods.eln.modern.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
public final class BenchHardeningGameTests {
    private static final BlockPos POS=new BlockPos(1,1,1);
    private static CircuitBenchBlockEntity place(GameTestHelper h){h.setBlock(POS,ElectricalAgeModern.CIRCUIT_BENCH.get());return (CircuitBenchBlockEntity)h.getBlockEntity(POS);}
    private static void pass(GameTestHelper h,String name){h.succeed();System.out.println("ELN_GAMETEST_PASS "+name);}
    @GameTest(template="empty",timeoutTicks=60)
    public static void faultLatchCannotBeClearedByReload(GameTestHelper h){
        var b=place(h);CompoundTag tag=new CompoundTag();b.saveAdditional(tag,h.getLevel().registryAccess());tag.getCompound("eln_state").putBoolean("faulted",true);
        b.loadAdditional(tag,h.getLevel().registryAccess());h.assertTrue(b.hasFault(),"Persisted fault ignored");
        CompoundTag saved=new CompoundTag();b.saveAdditional(saved,h.getLevel().registryAccess());h.assertTrue(saved.getCompound("eln_state").getBoolean("faulted"),"Fault latch not saved");
        h.runAfterDelay(5,()->{h.assertTrue(b.simulatedSteps()==0,"Faulted bench advanced");b.reset();h.assertTrue(!b.hasFault(),"Reset failed");pass(h,"bench_fault_latch");});
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void invalidBooleanIsNotSilentlyCoerced(GameTestHelper h){
        var b=place(h);CompoundTag tag=new CompoundTag();b.saveAdditional(tag,h.getLevel().registryAccess());tag.getCompound("eln_state").putByte("powered",(byte)2);
        b.loadAdditional(tag,h.getLevel().registryAccess());h.assertTrue(b.hasFault(),"Invalid boolean accepted");pass(h,"bench_strict_boolean");
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void packetTargetAndRotationAreCorrect(GameTestHelper h){
        var b=place(h);b.serverTick();h.assertTrue(b.voltage()>0,"Test setup must have nonzero charge");var wrong=new CircuitBenchBlockEntity(b.getBlockPos().above(),b.getBlockState());wrong.togglePower();
        wrong.onDataPacket(null,b.getUpdatePacket(),h.getLevel().registryAccess());h.assertTrue(wrong.voltage()==0,"Wrong target packet applied");
        var state=ElectricalAgeModern.CIRCUIT_BENCH.get().defaultBlockState();
        h.assertTrue(state.rotate(Rotation.CLOCKWISE_90).getValue(CircuitBenchBlock.FACING)==Direction.EAST,"Structure rotation did not update facing");
        h.assertTrue(state.mirror(Mirror.LEFT_RIGHT).getValue(CircuitBenchBlock.FACING)==Direction.SOUTH,"Mirror did not update facing");pass(h,"bench_packet_rotation");
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void unloadedAndRemovedBenchCannotAdvance(GameTestHelper h){
        var b=place(h);
        h.runAfterDelay(5,()->{double previous=b.voltage();b.onChunkUnloaded();b.setRemoved();b.serverTick();b.togglePower();h.assertTrue(b.voltage()==previous&&b.powered(),"Removed bench mutated");
            b.clearRemoved();h.runAfterDelay(5,()->{h.assertTrue(b.voltage()>previous&&!b.hasFault(),"Reloaded bench retained closed circuit");pass(h,"bench_chunk_callback");});});
    }
}
