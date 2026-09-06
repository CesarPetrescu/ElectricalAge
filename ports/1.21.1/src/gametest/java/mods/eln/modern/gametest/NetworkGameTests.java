package mods.eln.modern.gametest;

import io.netty.buffer.Unpooled;
import mods.eln.modern.ElectricalAgeModern;
import mods.eln.modern.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
public final class NetworkGameTests {
    private static final BlockPos SOURCE=new BlockPos(1,1,0),LOAD=new BlockPos(1,1,2),GAP=new BlockPos(0,1,1);
    private static CircuitDeviceBlockEntity at(GameTestHelper h,BlockPos p){return (CircuitDeviceBlockEntity)h.getBlockEntity(p);}
    private static CircuitDeviceBlockEntity ring(GameTestHelper h,boolean capacitor){
        h.setBlock(SOURCE,ElectricalAgeModern.VOLTAGE_SOURCE.get().defaultBlockState().setValue(CircuitDeviceBlock.FACING,Direction.EAST));
        h.setBlock(LOAD,(capacitor?ElectricalAgeModern.CAPACITOR:ElectricalAgeModern.RESISTIVE_LOAD).get().defaultBlockState().setValue(CircuitDeviceBlock.FACING,Direction.EAST));
        for(int x:new int[]{0,2})for(int z=0;z<3;z++)h.setBlock(new BlockPos(x,1,z),ElectricalAgeModern.RESISTIVE_WIRE.get().defaultBlockState());
        return at(h,LOAD);
    }
    private static void near(GameTestHelper h,double expected,double actual){h.assertTrue(Double.isFinite(actual)&&Math.abs(expected-actual)<1e-7,"Electrical mismatch: "+expected+" != "+actual);}
    private static void pass(GameTestHelper h,String name){h.succeed();System.out.println("ELN_GAMETEST_PASS "+name);}
    @GameTest(template="empty",timeoutTicks=100)
    public static void closedLoopAndLiveSplitMerge(GameTestHelper h){
        CircuitDeviceBlockEntity load=ring(h,false);
        h.runAfterDelay(12,()->{
            h.assertTrue(load.simulatedSteps()>0&&!load.hasFault(),"Network ticker did not run");near(h,100/11.6,load.voltage());near(h,10/11.6,load.current());
            h.assertTrue(load.getBlockState().getValue(CircuitDeviceBlock.LIT),"Powered load did not light");
            h.setBlock(GAP,Blocks.AIR.defaultBlockState());
            h.runAfterDelay(5,()->{
                near(h,0,load.current());h.assertTrue(!load.getBlockState().getValue(CircuitDeviceBlock.LIT),"Open circuit remained lit");
                h.setBlock(GAP,ElectricalAgeModern.RESISTIVE_WIRE.get().defaultBlockState());
                h.runAfterDelay(5,()->{near(h,10/11.6,load.current());pass(h,"network_split_merge");});
            });
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void capacitorHistorySurvivesTopologyRebuild(GameTestHelper h){
        CircuitDeviceBlockEntity capacitor=ring(h,true);
        h.runAfterDelay(15,()->{
            double expected=10*(1-Math.pow(1/(1+.05/1.6),capacitor.simulatedSteps()));near(h,expected,capacitor.voltage());
            h.setBlock(GAP,Blocks.AIR.defaultBlockState());double saved=capacitor.voltage();
            h.runAfterDelay(5,()->{
                near(h,saved,capacitor.voltage());near(h,0,capacitor.current());
                h.setBlock(GAP,ElectricalAgeModern.RESISTIVE_WIRE.get().defaultBlockState());
                h.runAfterDelay(5,()->{h.assertTrue(capacitor.voltage()>saved&&!capacitor.hasFault(),"Reconnect lost integration state");pass(h,"network_capacitor_rebuild");});
            });
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void toggledSourceDischargesTheSameNetwork(GameTestHelper h){
        CircuitDeviceBlockEntity capacitor=ring(h,true);
        h.runAfterDelay(15,()->{
            double initial=capacitor.voltage();long before=capacitor.simulatedSteps();at(h,SOURCE).togglePower();
            h.runAfterDelay(10,()->{near(h,initial*Math.pow(1/(1+.05/1.6),capacitor.simulatedSteps()-before),capacitor.voltage());h.assertTrue(capacitor.current()<0,"Discharge current sign wrong");pass(h,"network_source_toggle");});
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void deviceNbtAndRealPacketRoundTrip(GameTestHelper h){
        CircuitDeviceBlockEntity capacitor=ring(h,true);
        h.runAfterDelay(10,()->{
            CompoundTag saved=new CompoundTag();capacitor.saveAdditional(saved,h.getLevel().registryAccess());
            CircuitDeviceBlockEntity copy=new CircuitDeviceBlockEntity(capacitor.getBlockPos(),capacitor.getBlockState());
            copy.loadAdditional(saved,h.getLevel().registryAccess());near(h,capacitor.voltage(),copy.voltage());h.assertTrue(!copy.hasFault(),"Valid state rejected");
            RegistryFriendlyByteBuf buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
            try{
                ClientboundBlockEntityDataPacket.STREAM_CODEC.encode(buf,capacitor.getUpdatePacket());h.assertTrue(buf.readableBytes()<512,"Device update too large");
                var packet=ClientboundBlockEntityDataPacket.STREAM_CODEC.decode(buf);h.assertTrue(buf.readableBytes()==0,"Unread payload tail");
                copy.onDataPacket(null,packet,h.getLevel().registryAccess());near(h,capacitor.current(),copy.current());near(h,capacitor.voltage(),copy.voltage());
                CircuitDeviceBlockEntity wrong=new CircuitDeviceBlockEntity(capacitor.getBlockPos().above(),capacitor.getBlockState());wrong.onDataPacket(null,packet,h.getLevel().registryAccess());near(h,0,wrong.voltage());
            }finally{buf.release();}
            pass(h,"network_nbt_packet");
        });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void faultLatchAndFutureDataSurviveReload(GameTestHelper h){
        h.setBlock(LOAD,ElectricalAgeModern.CAPACITOR.get().defaultBlockState());CircuitDeviceBlockEntity capacitor=at(h,LOAD);
        capacitor.latchFault("Test fault");CompoundTag saved=new CompoundTag();capacitor.saveAdditional(saved,h.getLevel().registryAccess());
        capacitor.loadAdditional(saved,h.getLevel().registryAccess());h.assertTrue(capacitor.hasFault(),"Runtime fault disappeared on load");
        CompoundTag unknown=saved.copy();unknown.getCompound("eln_device").putInt("schema",999);unknown.getCompound("eln_device").putString("future","retain");
        capacitor.loadAdditional(unknown,h.getLevel().registryAccess());CompoundTag roundtrip=new CompoundTag();capacitor.saveAdditional(roundtrip,h.getLevel().registryAccess());
        h.assertTrue(unknown.equals(roundtrip),"Unknown device state lost");h.assertTrue(!capacitor.getUpdateTag(h.getLevel().registryAccess()).contains("eln_device"),"Raw disk evidence leaked");
        capacitor.reset();h.assertTrue(!capacitor.hasFault(),"Explicit reset failed");pass(h,"network_fault_persistence");
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void invalidBooleanAndWrongDeviceKindRejected(GameTestHelper h){
        h.setBlock(LOAD,ElectricalAgeModern.CAPACITOR.get().defaultBlockState());CircuitDeviceBlockEntity capacitor=at(h,LOAD);
        CompoundTag clean=new CompoundTag();capacitor.saveAdditional(clean,h.getLevel().registryAccess());
        CompoundTag invalid=clean.copy();invalid.getCompound("eln_device").putByte("powered",(byte)2);capacitor.loadAdditional(invalid,h.getLevel().registryAccess());h.assertTrue(capacitor.hasFault(),"Bad boolean accepted");
        invalid=clean.copy();invalid.getCompound("eln_device").putString("kind","SOURCE");capacitor.loadAdditional(invalid,h.getLevel().registryAccess());h.assertTrue(capacitor.hasFault(),"Wrong device kind accepted");
        pass(h,"network_invalid_nbt");
    }
}
