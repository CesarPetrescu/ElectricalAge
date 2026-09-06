package mods.eln.modern;

import com.mojang.logging.LogUtils;
import mods.eln.modern.network.CircuitDeviceBlock;
import mods.eln.modern.network.CircuitDeviceBlockEntity;
import mods.eln.sim.network.GridTopology;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(ElectricalAgeModern.MODID)
public final class ElectricalAgeModern {
    public static final String MODID = "eln";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredBlock<CircuitBenchBlock> CIRCUIT_BENCH = BLOCKS.register("circuit_bench",
        () -> new CircuitBenchBlock(BlockBehaviour.Properties.of().strength(2.0f).noOcclusion()));
    public static final DeferredItem<BlockItem> CIRCUIT_BENCH_ITEM = ITEMS.registerSimpleBlockItem(CIRCUIT_BENCH);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CircuitBenchBlockEntity>> CIRCUIT_BENCH_ENTITY = BLOCK_ENTITIES.register("circuit_bench",
        () -> BlockEntityType.Builder.of(CircuitBenchBlockEntity::new, CIRCUIT_BENCH.get()).build(null));
    private static DeferredBlock<CircuitDeviceBlock> device(String name, GridTopology.Kind kind) {
        return BLOCKS.register(name, () -> new CircuitDeviceBlock(BlockBehaviour.Properties.of().strength(2.0f)
            .lightLevel(state -> kind == GridTopology.Kind.LOAD && state.getValue(CircuitDeviceBlock.LIT) ? 12 : 0), kind));
    }
    public static final DeferredBlock<CircuitDeviceBlock> VOLTAGE_SOURCE = device("voltage_source", GridTopology.Kind.SOURCE);
    public static final DeferredBlock<CircuitDeviceBlock> RESISTIVE_WIRE = device("resistive_wire", GridTopology.Kind.WIRE);
    public static final DeferredBlock<CircuitDeviceBlock> RESISTIVE_LOAD = device("resistive_load", GridTopology.Kind.LOAD);
    public static final DeferredBlock<CircuitDeviceBlock> CAPACITOR = device("capacitor", GridTopology.Kind.CAPACITOR);
    public static final DeferredItem<BlockItem> SOURCE_ITEM = ITEMS.registerSimpleBlockItem(VOLTAGE_SOURCE);
    public static final DeferredItem<BlockItem> WIRE_ITEM = ITEMS.registerSimpleBlockItem(RESISTIVE_WIRE);
    public static final DeferredItem<BlockItem> LOAD_ITEM = ITEMS.registerSimpleBlockItem(RESISTIVE_LOAD);
    public static final DeferredItem<BlockItem> CAPACITOR_ITEM = ITEMS.registerSimpleBlockItem(CAPACITOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CircuitDeviceBlockEntity>> CIRCUIT_DEVICE_ENTITY = BLOCK_ENTITIES.register("circuit_device",
        () -> BlockEntityType.Builder.of(CircuitDeviceBlockEntity::new, VOLTAGE_SOURCE.get(), RESISTIVE_WIRE.get(), RESISTIVE_LOAD.get(), CAPACITOR.get()).build(null));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = TABS.register("main", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.eln"))
        .icon(() -> new ItemStack(CIRCUIT_BENCH_ITEM.get()))
        .displayItems((parameters, output) -> {
            output.accept(CIRCUIT_BENCH_ITEM.get()); output.accept(SOURCE_ITEM.get()); output.accept(WIRE_ITEM.get());
            output.accept(LOAD_ITEM.get()); output.accept(CAPACITOR_ITEM.get());
        })
        .build());

    public ElectricalAgeModern(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
        LOGGER.info("ELN_PORT_REGISTERED Minecraft 1.21.1 prototype; bounded connected circuits, not full legacy feature parity");
    }
}
