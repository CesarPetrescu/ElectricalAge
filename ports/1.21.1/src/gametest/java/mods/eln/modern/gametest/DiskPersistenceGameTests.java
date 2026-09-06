package mods.eln.modern.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import mods.eln.modern.CircuitBenchBlockEntity;
import mods.eln.modern.ElectricalAgeModern;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real compressed NBT and filesystem I/O, not an ordinary populated-world restart test. */
@GameTestHolder("eln")
@PrefixGameTestTemplate(false)
public final class DiskPersistenceGameTests {
    @GameTest(template="empty",timeoutTicks=60)
    public static void compressedDiskRoundTripPreservesChargeAndFault(GameTestHelper helper) throws Exception {
        BlockPos pos = new BlockPos(1,1,1);
        helper.setBlock(pos, ElectricalAgeModern.CIRCUIT_BENCH.get().defaultBlockState());
        CircuitBenchBlockEntity source = (CircuitBenchBlockEntity) helper.getBlockEntity(pos);
        for (int i=0; i<10; i++) source.serverTick();
        helper.assertTrue(source.voltage()>0, "Disk fixture must have nonzero charge");
        CompoundTag saved = new CompoundTag();
        source.saveAdditional(saved, helper.getLevel().registryAccess());
        Path path = Files.createTempFile("eln-persistence-", ".nbt");
        try {
            NbtIo.writeCompressed(saved, path);
            CompoundTag restored = NbtIo.readCompressed(path, NbtAccounter.create(1024*1024));
            CircuitBenchBlockEntity copy = new CircuitBenchBlockEntity(source.getBlockPos(),source.getBlockState());
            copy.loadAdditional(restored,helper.getLevel().registryAccess());
            helper.assertTrue(copy.voltage()==source.voltage() && !copy.hasFault(), "Disk round-trip changed charge");
            saved.getCompound("eln_state").putBoolean("faulted", true);
            NbtIo.writeCompressed(saved,path);
            copy.loadAdditional(NbtIo.readCompressed(path,NbtAccounter.create(1024*1024)),helper.getLevel().registryAccess());
            helper.assertTrue(copy.hasFault() && copy.voltage()==source.voltage(), "Disk reload lost fault latch or charge");
            copy.setRemoved();
        } finally {
            Files.deleteIfExists(path);
        }
        helper.succeed();
        System.out.println("ELN_GAMETEST_PASS compressed_nbt_disk_roundtrip");
    }
}
