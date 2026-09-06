package mods.eln.modern.client;

import mods.eln.modern.ElectricalAgeModern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Independent readiness marker for CI: title screen plus real non-missing block and item geometry. */
@EventBusSubscriber(modid = ElectricalAgeModern.MODID, value = Dist.CLIENT)
public final class ClientValidation {
    private static boolean validated;
    private ClientValidation() { }
    private static int count(BakedModel model, BlockState state) {
        int count = model.getQuads(state, null, RandomSource.create(1)).size();
        for (Direction side : Direction.values()) count += model.getQuads(state, side, RandomSource.create(1)).size();
        return count;
    }
    @SubscribeEvent public static void afterClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (validated || !(client.screen instanceof TitleScreen)) return;
        BlockState state = ElectricalAgeModern.CIRCUIT_BENCH.get().defaultBlockState();
        BakedModel block = client.getBlockRenderer().getBlockModel(state);
        BakedModel item = client.getItemRenderer().getModel(new ItemStack(ElectricalAgeModern.CIRCUIT_BENCH_ITEM.get()), null, null, 0);
        BakedModel missing = client.getModelManager().getMissingModel();
        int blockQuads = count(block,state), itemQuads = count(item,null);
        if (block == missing || item == missing || blockQuads == 0 || itemQuads == 0) throw new IllegalStateException("ELN circuit bench model did not bake correctly");
        if (Boolean.getBoolean("eln.verifyPackagedRuntime")) {
            String origin=ElectricalAgeModern.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
            if (!origin.contains(".jar")) throw new IllegalStateException("Packaged probe loaded development classes: "+origin);
            try {
                Class.forName("mods.eln.modern.gametest.CircuitBenchGameTests",false,ElectricalAgeModern.class.getClassLoader());
                throw new IllegalStateException("GameTest classes leaked into the packaged runtime");
            } catch (ClassNotFoundException expected) { /* The production jar must not contain test classes. */ }
            ElectricalAgeModern.LOGGER.info("ELN_PACKAGED_RUNTIME_OK origin={}",origin);
        }
        validated = true;
        ElectricalAgeModern.LOGGER.info("ELN_CLIENT_READY obj_quads={} item_quads={}", blockQuads, itemQuads);
    }
}
