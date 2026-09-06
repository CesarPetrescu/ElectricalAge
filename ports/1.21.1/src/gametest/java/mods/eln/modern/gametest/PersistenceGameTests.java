package mods.eln.modern.gametest;

import io.netty.buffer.Unpooled;
import mods.eln.modern.CircuitBenchBlockEntity;
import mods.eln.modern.CompoundStateData;
import mods.eln.modern.ElectricalAgeModern;
import mods.eln.sim.mna.component.VoltageSource;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("eln")
@PrefixGameTestTemplate(false)
public final class PersistenceGameTests {
    @GameTest(template="empty",timeoutTicks=60)
    public static void inheritedComponentUsesRealNbtAdapter(GameTestHelper helper) {
        CompoundTag tag=new CompoundTag();
        CompoundStateData adapter=new CompoundStateData(tag);
        VoltageSource original=new VoltageSource("source").setU(3.3);
        original.getCurrentState().state=-.25;
        original.writeState(adapter,"p_");
        VoltageSource restored=new VoltageSource("source");
        restored.readState(adapter,"p_");
        helper.assertTrue(restored.getU()==3.3 && restored.getI()==.25,"Inherited component state did not survive modern NBT");
        adapter.setBoolean("enabled",true);
        helper.assertTrue(adapter.getBoolean("enabled"),"Boolean persistence failed");
        tag.putDouble("p_sourceU",Double.NaN);
        boolean rejected=false;
        try { restored.readState(adapter,"p_"); } catch (IllegalArgumentException expected) { rejected=true; }
        helper.assertTrue(rejected,"Invalid component value was accepted");
        tag.putDouble("p_sourceU",6.0);
        tag.putDouble("p_sourceIstate",Double.NaN);
        rejected=false;
        try { restored.readState(adapter,"p_"); } catch (IllegalArgumentException expected) { rejected=true; }
        helper.assertTrue(rejected && restored.getU()==3.3 && restored.getI()==.25,"Bad current partially mutated source state");
        helper.succeed();System.out.println("ELN_GAMETEST_PASS component_nbt_adapter");
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void vanillaUpdatePacketRoundTrip(GameTestHelper helper) {
        BlockPos relative=new BlockPos(1,1,1);
        helper.setBlock(relative,ElectricalAgeModern.CIRCUIT_BENCH.get().defaultBlockState());
        CircuitBenchBlockEntity source=(CircuitBenchBlockEntity)helper.getBlockEntity(relative);
        CompoundTag state=new CompoundTag();state.putInt("schema",1);state.putDouble("voltage",6.25);state.putBoolean("powered",false);
        CompoundTag root=new CompoundTag();root.put("eln_state",state);
        source.loadAdditional(root,helper.getLevel().registryAccess());
        RegistryFriendlyByteBuf buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),helper.getLevel().registryAccess());
        try {
            ClientboundBlockEntityDataPacket.STREAM_CODEC.encode(buffer,source.getUpdatePacket());
            helper.assertTrue(buffer.readableBytes()<512,"Unexpectedly large bench update");
            ClientboundBlockEntityDataPacket packet=ClientboundBlockEntityDataPacket.STREAM_CODEC.decode(buffer);
            helper.assertTrue(buffer.readableBytes()==0,"Packet was not consumed completely");
            helper.assertTrue(packet.getPos().equals(helper.absolutePos(relative)),"Packet changed its target");
            CircuitBenchBlockEntity target=new CircuitBenchBlockEntity(packet.getPos(),ElectricalAgeModern.CIRCUIT_BENCH.get().defaultBlockState());
            target.onDataPacket(null,packet,helper.getLevel().registryAccess());
            helper.assertTrue(target.voltage()==6.25 && !target.powered() && !target.hasFault(),"Client view did not survive the real packet codec");
            target.setRemoved();
        } finally { buffer.release(); }
        helper.succeed();System.out.println("ELN_GAMETEST_PASS vanilla_packet_codec");
    }
}
