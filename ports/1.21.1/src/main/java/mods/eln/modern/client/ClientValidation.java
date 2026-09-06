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

/** Opt-in CI probe. Normal play and user resource packs do not run diagnostic assertions. */
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
        if (!Boolean.getBoolean("eln.validateClient")) return;
        Minecraft client = Minecraft.getInstance();
        if (validated || !(client.screen instanceof TitleScreen)) return;
        BakedModel missing = client.getModelManager().getMissingModel();
        int blockQuads = 0;
        for (BlockState state : ElectricalAgeModern.CIRCUIT_BENCH.get().getStateDefinition().getPossibleStates()) {
            BakedModel block = client.getBlockRenderer().getBlockModel(state);
            int quads = count(block,state);
            if (block == missing || quads != 12) throw new IllegalStateException("ELN circuit bench block model is incomplete: " + state + " quads=" + quads);
            blockQuads += quads;
        }
        BakedModel item = client.getItemRenderer().getModel(new ItemStack(ElectricalAgeModern.CIRCUIT_BENCH_ITEM.get()), null, null, 0);
        int itemQuads = count(item,null);
        if (item == missing || itemQuads != 12) throw new IllegalStateException("ELN circuit bench item model is incomplete: quads=" + itemQuads);
        int deviceStates=0;
        for (var device : java.util.List.of(ElectricalAgeModern.VOLTAGE_SOURCE, ElectricalAgeModern.RESISTIVE_WIRE,
                ElectricalAgeModern.RESISTIVE_LOAD, ElectricalAgeModern.CAPACITOR)) {
            for (BlockState state : device.get().getStateDefinition().getPossibleStates()) {
                BakedModel model=client.getBlockRenderer().getBlockModel(state);
                if (model==missing || count(model,state)!=6) throw new IllegalStateException("ELN network model incomplete: "+state);
                deviceStates++;
            }
            BakedModel deviceItem=client.getItemRenderer().getModel(new ItemStack(device.get().asItem()),null,null,0);
            if (deviceItem==missing || count(deviceItem,null)!=6) throw new IllegalStateException("ELN device item model incomplete");
        }
        ElectricalAgeModern.LOGGER.info("ELN_NETWORK_MODELS_READY states={} items=4",deviceStates);
        if (Boolean.getBoolean("eln.verifyPackagedRuntime")) {
            String origin=ElectricalAgeModern.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
            if (!origin.contains(".jar")) throw new IllegalStateException("Packaged probe loaded development classes: "+origin);
            try {
                Class.forName("mods.eln.modern.gametest.CircuitBenchGameTests",false,ElectricalAgeModern.class.getClassLoader());
                throw new IllegalStateException("GameTest classes leaked into the packaged runtime");
            } catch (ClassNotFoundException expected) { /* Production jar excludes GameTests. */ }
            ElectricalAgeModern.LOGGER.info("ELN_PACKAGED_RUNTIME_OK origin={}",origin);
        }
        validated = true;
        ElectricalAgeModern.LOGGER.info("ELN_CLIENT_READY obj_quads={} item_quads={}", blockQuads, itemQuads);
    }
}
