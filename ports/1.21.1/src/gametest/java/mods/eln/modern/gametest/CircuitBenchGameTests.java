package mods.eln.modern.gametest;

import mods.eln.modern.CircuitBenchBlockEntity;
import mods.eln.modern.ElectricalAgeModern;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("eln")
@PrefixGameTestTemplate(false)
public final class CircuitBenchGameTests {
    private static final BlockPos POS = new BlockPos(1,1,1);
    private static CircuitBenchBlockEntity place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ElectricalAgeModern.CIRCUIT_BENCH.get().defaultBlockState());
        return (CircuitBenchBlockEntity) helper.getBlockEntity(pos);
    }
    private static CompoundTag state(double volts, boolean powered, int schema) {
        CompoundTag inner = new CompoundTag(); inner.putInt("schema",schema); inner.putDouble("voltage",volts); inner.putBoolean("powered",powered);
        CompoundTag outer = new CompoundTag(); outer.put("eln_state",inner); return outer;
    }
    private static void pass(GameTestHelper helper, String name) {
        helper.succeed(); System.out.println("ELN_GAMETEST_PASS " + name);
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void serverTickerChargesUsingMna(GameTestHelper helper) {
        CircuitBenchBlockEntity bench = place(helper,POS);
        helper.runAfterDelay(20,()->{
            helper.assertTrue(bench.simulatedSteps()>0,"Server ticker did not run");
            double expected=10*(1-Math.pow(1/1.005,bench.simulatedSteps()));
            helper.assertTrue(Math.abs(expected-bench.voltage())<1e-8,"Charge did not match discrete circuit equation");
            helper.assertTrue(!bench.hasFault(),"Bench faulted"); pass(helper,"server_charge");
        });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void serverTickerDischargesUsingMna(GameTestHelper helper) {
        CircuitBenchBlockEntity bench = place(helper,POS);
        bench.loadAdditional(state(10,false,1),helper.getLevel().registryAccess());
        helper.runAfterDelay(20,()->{
            double expected=10*Math.pow(1/1.005,bench.simulatedSteps());
            helper.assertTrue(bench.simulatedSteps()>0 && bench.voltage()<10,"Discharge ticker did not run");
            helper.assertTrue(Math.abs(expected-bench.voltage())<1e-8,"Discharge did not match circuit equation"); pass(helper,"server_discharge");
        });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void benchesAreIndependent(GameTestHelper helper) {
        CircuitBenchBlockEntity a=place(helper,new BlockPos(0,1,1)), b=place(helper,new BlockPos(2,1,1));
        a.togglePower();
        helper.runAfterDelay(15,()->{
            helper.assertTrue(a.voltage()==0 && b.voltage()>0,"Separate benches leaked state"); pass(helper,"independent_instances");
        });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void nbtRoundTripPreservesCharge(GameTestHelper helper) {
        CircuitBenchBlockEntity bench=place(helper,POS);
        helper.runAfterDelay(15,()->{
            CompoundTag saved=new CompoundTag();bench.saveAdditional(saved,helper.getLevel().registryAccess());
            CircuitBenchBlockEntity restored=new CircuitBenchBlockEntity(helper.absolutePos(POS),ElectricalAgeModern.CIRCUIT_BENCH.get().defaultBlockState());
            restored.loadAdditional(saved,helper.getLevel().registryAccess());
            helper.assertTrue(restored.voltage()==bench.voltage() && restored.powered()==bench.powered(),"NBT snapshot changed state");
            restored.setRemoved(); pass(helper,"nbt_roundtrip");
        });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void malformedStateFreezesUntilExplicitReset(GameTestHelper helper) {
        CircuitBenchBlockEntity bench=place(helper,POS);
        bench.loadAdditional(state(Double.NaN,true,1),helper.getLevel().registryAccess());
        helper.assertTrue(bench.hasFault(),"NaN was accepted");
        helper.runAfterDelay(3,()->{
            helper.assertTrue(bench.simulatedSteps()==0,"Corrupt state was simulated");
            CompoundTag saved=new CompoundTag();bench.saveAdditional(saved,helper.getLevel().registryAccess());
            helper.assertTrue(Double.isNaN(saved.getCompound("eln_state").getDouble("voltage")),"Corrupt evidence was silently discarded");
            bench.reset();
            helper.runAfterDelay(3,()->{helper.assertTrue(!bench.hasFault() && bench.voltage()>0,"Explicit reset did not recover");pass(helper,"malformed_state");});
        });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void futureSchemaIsPreserved(GameTestHelper helper) {
        CircuitBenchBlockEntity bench=place(helper,POS);
        CompoundTag data=state(3,true,999);data.getCompound("eln_state").putString("future_field","retain_me");
        bench.loadAdditional(data,helper.getLevel().registryAccess());
        CompoundTag saved=new CompoundTag();bench.saveAdditional(saved,helper.getLevel().registryAccess());
        helper.assertTrue(bench.hasFault() && saved.getCompound("eln_state").equals(data.getCompound("eln_state")),"Unknown version was destroyed");
        helper.assertTrue(!bench.getUpdateTag(helper.getLevel().registryAccess()).getCompound("eln_state").contains("future_field"),"Unsupported disk payload leaked into network view");
        pass(helper,"future_schema");
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void lifecycleCallbacksRecreateOwnedCircuit(GameTestHelper helper) {
        CircuitBenchBlockEntity bench=place(helper,POS);
        helper.runAfterDelay(5,()->{
            double before=bench.voltage(); bench.setRemoved(); bench.clearRemoved();
            helper.runAfterDelay(5,()->{helper.assertTrue(bench.voltage()>before && !bench.hasFault(),"Reactivated block entity retained a closed solver");pass(helper,"lifecycle_callbacks");});
        });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void recipeIsLoaded(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("eln","circuit_bench")).isPresent(),"Bench recipe missing");
        pass(helper,"recipe_loaded");
    }
}
