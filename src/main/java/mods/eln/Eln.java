package mods.eln;

import mods.eln.block.ArcClayBlock;
import mods.eln.block.ArcMetalBlock;
import mods.eln.cable.CableRenderDescriptor;
import mods.eln.client.ClientKeyHandler;
import mods.eln.config.JsonConfig;
import mods.eln.entity.ReplicatorPopProcess;
import mods.eln.environment.BiomeClimateService;
import mods.eln.eventhandlers.ElnFMLEventsHandler;
import mods.eln.eventhandlers.ElnForgeEventsHandler;
import mods.eln.eventhandlers.RoomThermalBlockEventsHandler;
import mods.eln.fluid.ElnFluidRegistry;
import mods.eln.fluid.FuelRegistry;
import mods.eln.fluid.ThermalRegistry;
import mods.eln.generic.CreativeTabPopulator;
import mods.eln.generic.GenericCreativeTab;
import mods.eln.generic.GenericItemUsingDamageDescriptor;
import mods.eln.generic.GenericItemUsingDamageDescriptorWithComment;
import mods.eln.generic.SharedItem;
import mods.eln.ghost.GhostBlock;
import mods.eln.ghost.GhostManager;
import mods.eln.ghost.GhostManagerNbt;
import mods.eln.item.*;
import mods.eln.item.electricalinterface.ItemEnergyInventoryProcess;
import mods.eln.item.lampitem.LampLists;
import mods.eln.lightblock.LightBlock;
import mods.eln.lightblock.LightBlockEntity;
import mods.eln.misc.*;
import mods.eln.mqtt.MqttManager;
import mods.eln.metrics.MetricsSubsystem;
import mods.eln.network.ElnNetwork;
import mods.eln.node.NodeBlockEntity;
import mods.eln.node.NodeManager;
import mods.eln.node.NodeManagerNbt;
import mods.eln.node.NodeServer;
import mods.eln.node.six.*;
import mods.eln.node.transparent.*;
import mods.eln.ore.OreDescriptor;
import mods.eln.ore.OreItem;
import mods.eln.ore.OreScannerManager;
import mods.eln.packets.*;
import mods.eln.railroad.ElectricMinecartChargeReporter;
import mods.eln.registration.ElnRegistry;
import mods.eln.registration.ItemRegistration;
import mods.eln.registration.SingleNodeRegistration;
import mods.eln.registration.SixNodeRegistration;
import mods.eln.registration.TransparentNodeRegistration;
import mods.eln.server.*;
import mods.eln.sim.Simulator;
import mods.eln.sim.ThermalLoadInitializer;
import mods.eln.sim.mna.component.Resistor;
import mods.eln.sim.nbt.NbtElectricalLoad;
import mods.eln.simplenode.computerprobe.ComputerProbeBlock;
import mods.eln.simplenode.energyconverter.EnergyConverterElnToOtherBlock;
import mods.eln.sixnode.PortableNaNDescriptor;
import mods.eln.sixnode.currentcable.CurrentCableDescriptor;
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor;
import mods.eln.sixnode.electricaldatalogger.DataLogsPrintDescriptor;
import mods.eln.sixnode.lampsupply.LampSupplyElement;
import mods.eln.sixnode.modbusrtu.ModbusTcpServer;
import mods.eln.sixnode.tutorialsign.TutorialSignElement;
import mods.eln.sixnode.wirelesssignal.IWirelessSignalSpot;
import mods.eln.sixnode.wirelesssignal.tx.WirelessSignalTxElement;
import mods.eln.transparentnode.electricalfurnace.ElectricalFurnaceDescriptor;
import mods.eln.transparentnode.teleporter.TeleporterElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.File;
import java.util.*;

import static mods.eln.i18n.I18N.TR_GROUP;

/**
 * The mod. 1.21 has no preInit/init/postInit: content is constructed in the constructor and
 * reaches the registries through {@link ElnRegistry} when NeoForge fires the register events;
 * what used to be init/postInit runs in {@link #commonSetup}/{@link #loadComplete}, and the
 * server lifecycle hooks are NeoForge bus events.
 */
@Mod(Eln.MODID)
public class Eln {
    public static Eln instance;
    // Lower-case since 1.11: registry names, packets and the lang/model asset paths all derive from it.
    public final static String MODID = "eln";
    public final static Logger LOGGER = LogManager.getLogger(MODID);
    public final static String NAME = "Electrical Age - jrddunbr's build";
    public final static String UPDATE_URL = "https://github.com/age-series/ElectricalAge/releases";
    public final static String[] AUTHORS = {"Dolu1990", "jrddunbr", "Baughn", "Grissess", "Caeleron", "Omega_Haxors",
     "lambdaShade", "cm0x4D", "metc", "TheBuilderBoy76", "Justus0405"};
    public static final String channelName = "miaouMod";
    public static final double solarPanelBasePower = 65.0;
    public static final byte packetPlayerKey = 14;
    public static final byte packetNodeSingleSerialized = 15;
    public static final byte packetPublishForNode = 16;
    public static final byte packetOpenLocalGui = 17;
    public static final byte packetForClientNode = 18;
    public static final byte packetPlaySound = 19;
    public static final byte packetDestroyUuid = 20;
    public static final byte packetClientToServerConnection = 21;
    public static final byte packetServerToClientInfo = 22;
    public static final byte packetFalstadImport = 23;
    public static final Obj3DFolder obj = new Obj3DFolder();
    public static final double gateInputCurrent = 0.00005;
    public static final double gateOutputCurrent = 0.100;
    public static final double LVU = 50;
    public static final double MVU = 200;
    public static final double HVU = 800;
    public static final double VVU = 3200;
    public static final double CCU = 120_000;
    public static final double cableHeatingTime = 30;
    public static final double cableWarmLimit = 130;
    public static final double cableThermalConductionTao = 0.5;
    public static final ThermalLoadInitializer cableThermalLoadInitializer =
     new ThermalLoadInitializer(cableWarmLimit, -100, cableHeatingTime, cableThermalConductionTao);
    public static final ThermalLoadInitializer sixNodeThermalLoadInitializer =
     new ThermalLoadInitializer(cableWarmLimit, -100, cableHeatingTime, 1000);
    public static final HashMap<String, ItemStack> dictionnaryOreFromMod = new HashMap<>();
    public static Logger logger = LogManager.getLogger("ELN");
    public static JsonConfig config = new JsonConfig(new File("config/Eln.cfg"));
    public static PacketHandler packetHandler;
    public static LiveDataManager clientLiveDataManager;
    public static ClientKeyHandler clientKeyHandler;
    public static SaveConfig saveConfig;
    public static GhostManager ghostManager;
    public static GhostManagerNbt ghostManagerNbt;
    public static ModbusTcpServer modbusServer;
    public static NodeManagerNbt nodeManagerNbt;
    public static Simulator simulator = null;
    public static DelayedTaskManager delayedTask;
    public static ItemEnergyInventoryProcess itemEnergyInventoryProcess;
    public static CreativeModeTab creativeTab;
    public static CreativeModeTab creativeTabPowerElectronics;
    public static CreativeModeTab creativeTabSignalProcessing;
    public static CreativeModeTab creativeTabLighting;
    public static CreativeModeTab creativeTabCables;
    public static CreativeModeTab creativeTabPowerDistribution;
    public static CreativeModeTab creativeTabToolsArmor;
    public static CreativeModeTab creativeTabOresMaterials;
    public static CreativeModeTab creativeTabMachines;
    public static CreativeModeTab creativeTabCreative;
    public static CreativeModeTab creativeTabOther;
    public static Item swordCopper, hoeCopper, shovelCopper, pickaxeCopper, axeCopper;
    public static GenericItemUsingDamageDescriptorWithComment plateCopper;
    public static ArmorItem helmetCopper, chestplateCopper, legsCopper, bootsCopper;
    public static ArmorItem helmetECoal, plateECoal, legsECoal, bootsECoal;
    public static SharedItem sharedItem;
    public static SharedItem sharedItemStackOne;
    public static ItemStack wrenchItemStack;
    public static SixNodeBlock sixNodeBlock;
    public static TransparentNodeBlock transparentNodeBlock;
    public static GhostBlock ghostBlock;
    public static LightBlock lightBlock;
    public static ArcClayBlock arcClayBlock;
    public static ArcMetalBlock arcMetalBlock;
    public static SixNodeItem sixNodeItem;
    public static TransparentNodeItem transparentNodeItem;
    public static OreItem oreItem;
    public static PortableNaNDescriptor portableNaNDescriptor = null;
    public static CableRenderDescriptor stdPortableNaN = null;
    public static boolean mqttEnabled = false;
    public static boolean simMetricsEnabled = false;
    public static String simMetricsMqttServer = "";
    public static String simMetricsId = "server";
    public static int simMetricsPublishIntervalTicks = 20;
    public static boolean debugEnabled = false;
    public static SiliconWafer siliconWafer;
    public static Transistor transistor;
    public static Thermistor thermistor;
    public static NibbleMemory nibbleMemory;
    public static ArithmeticLogicUnit alu;
    public static String dictSiliconWafer;
    public static String dictTransistor;
    public static String dictThermistor;
    public static String dictNibbleMemory;
    public static String dictALU;
    public static Map<ElnFluidRegistry, Fluid> fluids = new EnumMap<ElnFluidRegistry, Fluid>(ElnFluidRegistry.class);
    public static Map<ElnFluidRegistry, Block> fluidBlocks = new EnumMap<ElnFluidRegistry, Block>(ElnFluidRegistry.class);
    public static WindProcess wind;
    static public GenericItemUsingDamageDescriptor multiMeterElement, thermometerElement, allMeterElement;
    static public GenericItemUsingDamageDescriptor configCopyToolElement;
    static public GenericItemUsingDamageDescriptor falstadImportToolElement;
    public static TreeResin treeResin;
    public static MiningPipeDescriptor miningPipeDescriptor;
    static NodeServer nodeServer;
    private static NodeManager nodeManager;
    public static OreDescriptor oreCopper;
    public static GenericItemUsingDamageDescriptorWithComment dustCopper;
    public ArrayList<IConfigSharing> configShared = new ArrayList<>();
    public CopperCableDescriptor copperCableDescriptor;
    public WireScrapDescriptor wireScrapDescriptor;
    public WoundWireBundleDescriptor woundWireBundleDescriptor;
    public ElectricalCableDescriptor creativeCableDescriptor;
    public ElectricalCableDescriptor veryHighVoltageCableDescriptor;
    public ElectricalCableDescriptor highVoltageCableDescriptor;
    public ElectricalCableDescriptor signalCableDescriptor;
    public ElectricalCableDescriptor lowVoltageCableDescriptor;
    public ElectricalCableDescriptor meduimVoltageCableDescriptor;
    public ElectricalCableDescriptor signalBusCableDescriptor;
    public CurrentCableDescriptor lowCurrentCableDescriptor;
    public CurrentCableDescriptor mediumCurrentCableDescriptor;
    public CurrentCableDescriptor highCurrentCableDescriptor;
    public Double ELN_CONVERTER_MAX_POWER = 120_000.0;
    public ServerEventListener serverEventListener;
    public CableRenderDescriptor stdCableRenderSignal;
    public CableRenderDescriptor stdCableRenderSignalBus;
    public CableRenderDescriptor stdCableRender50V;
    public CableRenderDescriptor stdCableRender200V;
    public CableRenderDescriptor stdCableRender800V;
    public CableRenderDescriptor stdCableRender3200V;
    public CableRenderDescriptor stdCableRenderCreative;
    public CableRenderDescriptor lowCurrentCableRender;
    public CableRenderDescriptor mediumCurrentCableRender;
    public CableRenderDescriptor highCurrentCableRender;
    public FunctionTable batteryVoltageFunctionTable;
    public ArrayList<ItemStack> furnaceList = new ArrayList<ItemStack>();
    public RecipesList maceratorRecipes = new RecipesList();
    public RecipesList compressorRecipes = new RecipesList();
    public RecipesList plateMachineRecipes = new RecipesList();
    public RecipesList arcFurnaceRecipes = new RecipesList();
    public RecipesList magnetiserRecipes = new RecipesList();
    public GenericItemUsingDamageDescriptorWithComment copperIngot, plumbIngot, tungstenIngot;
    public DataLogsPrintDescriptor dataLogsPrintDescriptor;
    public static final double SVU = 5;
    public static final double signalVoltageAcceptNegative = -0.5;
    public static final double signalVoltageAcceptPositive = SVU + 0.5;
    public static final double SVII = gateInputCurrent / SVU, SVUinv = 1.0 / SVU;
    public EnergyConverterElnToOtherBlock elnToOtherBlockConverter;
    public ComputerProbeBlock computerProbeBlock;
    public static final double SVP = gateOutputCurrent * SVU;
    public ElectricalFurnaceDescriptor electricalFurnace;

    public static HashSet<String> oreNames = new HashSet<>();

    public static BrushDescriptor whiteDesc;

    public static List<String> brushSubNames;

    public static double getSmallRs() {
        return instance.lowVoltageCableDescriptor.electricalRs;
    }

    public static void applySmallRs(NbtElectricalLoad aLoad) {
        instance.lowVoltageCableDescriptor.applyTo(aLoad);
    }

    public static void applySmallRs(Resistor r) {
        instance.lowVoltageCableDescriptor.applyTo(r);
    }

    public static ItemStack findItemStack(String name, int stackSize) {
        ItemStack stack = ElnRegistry.findItemStack(name, stackSize);
        if (stack == null || stack.isEmpty()) {
            ItemStack dict = dictionnaryOreFromMod.get(name);
            if (dict != null) stack = dict.copyWithCount(stackSize);
        }
        return stack;
    }

    /** ui.icons.noSymbols: whether the "-ni" (no symbol) block sprites are preferred. */
    public static boolean uiIconsNoSymbols() {
        return config != null && config.getBooleanOrElse("ui.icons.noSymbols", false);
    }

    public Eln(IEventBus modBus, ModContainer container) {
        instance = this;
        LOGGER.info("Electrical Age {} constructing on NeoForge", container.getModInfo().getVersion());
        configureElnLogFile();
        registerContent();
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::loadComplete);
        modBus.addListener(this::buildCreativeTabContents);
        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        mods.eln.devtest.DevHooks.registerIfRequested();
    }

    /** What used to be preInit: everything the mod constructs, in the order it always did. */
    private void registerContent() {
        ElnNetwork.register("achieve", AchievePacket.class, AchievePacket::new, new AchievePacketHandler(), true);
        ElnNetwork.register("transparent_node_request", TransparentNodeRequestPacket.class, TransparentNodeRequestPacket::new, new TransparentNodeRequestPacketHandler(), true);
        ElnNetwork.register("transparent_node_response", TransparentNodeResponsePacket.class, TransparentNodeResponsePacket::new, new TransparentNodeResponsePacketHandler(), false);
        ElnNetwork.register("ghost_node_waila_request", GhostNodeWailaRequestPacket.class, GhostNodeWailaRequestPacket::new, new GhostNodeWailaRequestPacketHandler(), true);
        ElnNetwork.register("ghost_node_waila_response", GhostNodeWailaResponsePacket.class, GhostNodeWailaResponsePacket::new, new GhostNodeWailaResponsePacketHandler(), false);
        ElnNetwork.register("six_node_waila_request", SixNodeWailaRequestPacket.class, SixNodeWailaRequestPacket::new, new SixNodeWailaRequestPacketHandler(), true);
        ElnNetwork.register("six_node_waila_response", SixNodeWailaResponsePacket.class, SixNodeWailaResponsePacket::new, new SixNodeWailaResponsePacketHandler(), false);

        Utils.println(Version.print());

        File configFile = FMLPaths.CONFIGDIR.get().resolve("Eln.cfg").toFile();
        config = new JsonConfig(configFile);
        config.loadConfig();
        config.writeExampleFile();
        FuelRegistry.init(configFile);
        ThermalRegistry.init(configFile);
        MqttManager.init();
        MetricsSubsystem.refreshFromConfig();

        simulator = new Simulator(
            0.05,
            1 / config.getDoubleOrElse("simulation.electrical.frequency", 20.0),
            config.getIntOrElse("simulation.electrical.interSystemOverSampling", 50),
            1 / config.getDoubleOrElse("simulation.thermal.frequency", 400.0)
        );
        nodeManager = new NodeManager("caca");
        ghostManager = new GhostManager("caca2");
        delayedTask = new DelayedTaskManager();

        nodeServer = new NodeServer();
        clientLiveDataManager = new LiveDataManager();

        packetHandler = new PacketHandler();
        ElnNetwork.setRawHandler(packetHandler::onPayload);
        GuiHandler.register();

        creativeTabPowerElectronics = GenericCreativeTab.create("ElnPowerElectronics", Items.REDSTONE);
        creativeTabCables = GenericCreativeTab.create("ElnCables", Items.STRING);
        creativeTabPowerDistribution = GenericCreativeTab.create("ElnPowerDistribution", Items.STRING);
        creativeTabSignalProcessing = GenericCreativeTab.create("ElnSignalProcessing", Items.COMPARATOR);
        creativeTabLighting = GenericCreativeTab.create("ElnLighting", Blocks.REDSTONE_LAMP);
        creativeTabToolsArmor = GenericCreativeTab.create("ElnToolsArmor", Items.IRON_PICKAXE);
        creativeTabOresMaterials = GenericCreativeTab.create("ElnOresMaterials", Items.IRON_INGOT);
        creativeTabMachines = GenericCreativeTab.create("ElnMachines", Blocks.DISPENSER);
        creativeTabCreative = GenericCreativeTab.create("ElnCreative", Items.NETHER_STAR);
        creativeTabOther = creativeTabOresMaterials;
        creativeTab = creativeTabOther;

        // Blocks are constructed inside the block RegisterEvent (see ElnRegistry); the static
        // fields are assigned there. Everything that needs them at construction goes through the
        // family's block supplier or ElnRegistry.afterItems.
        ElnRegistry.registerBlock("Eln.arc_clay_block", () -> arcClayBlock = new ArcClayBlock());
        ElnRegistry.registerBlock("Eln.arc_metal_block", () -> arcMetalBlock = new ArcMetalBlock());
        var sixNodeBlockSupplier = ElnRegistry.registerBlock("Eln.SixNode", () -> sixNodeBlock = new SixNodeBlock(), null);
        var transparentNodeBlockSupplier = ElnRegistry.registerBlock("Eln.TransparentNode", () -> transparentNodeBlock = new TransparentNodeBlock(), null);
        ElnRegistry.registerBlock("Eln.ghostBlock", () -> ghostBlock = new GhostBlock());
        ElnRegistry.registerBlock("Eln.lightBlock", () -> lightBlock = new LightBlock());
        SixNodeEntity.TYPE = ElnRegistry.registerBlockEntity("SixNodeEntity", () -> sixNodeBlock, SixNodeEntity::new);
        TransparentNodeEntity.TYPE = ElnRegistry.registerBlockEntity("TransparentNodeEntity", () -> transparentNodeBlock, TransparentNodeEntity::new);
        TransparentNodeEntityWithFluid.TYPE = ElnRegistry.registerBlockEntity("TransparentNodeEntityWF", () -> transparentNodeBlock, TransparentNodeEntityWithFluid::new);
        LightBlockEntity.TYPE = ElnRegistry.registerBlockEntity("LightBlockEntity", () -> lightBlock, LightBlockEntity::new);

        sharedItem = new SharedItem("Eln.sharedItem");
        sharedItem.setCreativeTab(creativeTabOther).setMaxStackSize(64);
        sharedItemStackOne = new SharedItem("Eln.sharedItemStackOne");
        sharedItemStackOne.setCreativeTab(creativeTabOther).setMaxStackSize(1);
        sixNodeItem = new SixNodeItem(sixNodeBlockSupplier);
        sixNodeItem.setCreativeTab(creativeTabOther);
        transparentNodeItem = new TransparentNodeItem(transparentNodeBlockSupplier);
        transparentNodeItem.setCreativeTab(creativeTabOther);
        oreItem = new OreItem();
        oreItem.setCreativeTab(creativeTabOresMaterials);

        obj.loadAllElnModels();

        NodeManager.registerUuid(SixNodeBlock.NODE_UUID, SixNode.class);
        NodeManager.registerUuid(TransparentNodeBlock.NODE_UUID, TransparentNode.class);

        SixNode.sixNodeCacheList.add(new SixNodeCacheStd());

        LampLists.translateLampTypes(); // This MUST be called before block/item registration!

        SingleNodeRegistration.INSTANCE.registerSingle();
        SixNodeRegistration.INSTANCE.registerSix();
        TransparentNodeRegistration.INSTANCE.registerTransparent();
        ItemRegistration.INSTANCE.registerItem();

        ElnRegistry.afterItems(this::updateCreativeTabIcons);

        ElnRegistry.registerOre("blockAluminum", () -> new ItemStack(arcClayBlock));
        ElnRegistry.registerOre("blockSteel", () -> new ItemStack(arcMetalBlock));

        AnalyticsHandler.INSTANCE.submitUpstreamAnalytics();
        AnalyticsHandler.INSTANCE.submitAgeSeriesAnalytics();
    }

    private void configureElnLogFile() {
        File runtimeDir = FMLPaths.GAMEDIR.get().toFile();
        File logDir = new File(runtimeDir, "logs");
        if (!logDir.exists() && !logDir.mkdirs()) return;

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        final String appenderName = "ELN_FILE";
        if (configuration.getAppenders().containsKey(appenderName)) return;

        PatternLayout layout = PatternLayout.newBuilder()
            .withPattern("[%d{HH:mm:ss}] [%t/%level] [%logger]: %msg%n")
            .withConfiguration(configuration)
            .build();

        FileAppender appender = FileAppender.newBuilder()
            .withFileName(new File(logDir, "eln.log").getAbsolutePath())
            .withAppend(true)
            .withName(appenderName)
            .withLayout(layout)
            .withConfiguration(configuration)
            .build();
        if (appender == null) return;

        appender.start();

        LoggerConfig loggerConfig = configuration.getLoggerConfig("ELN");
        loggerConfig.addAppender(appender, Level.INFO, null);
        context.updateLoggers();
    }

    /** What used to be init. Registries are complete here. */
    private void commonSetup(FMLCommonSetupEvent event) {
        TR_GROUP("Eln", "Electrical Age");
        TR_GROUP("ElnPowerElectronics", "Electrical Age - Power Electronics");
        TR_GROUP("ElnSignalProcessing", "Electrical Age - Signal Processing");
        TR_GROUP("ElnLighting", "Electrical Age - Lighting");
        TR_GROUP("ElnCables", "Electrical Age - Cables");
        TR_GROUP("ElnPowerDistribution", "Electrical Age - Power Distribution");
        TR_GROUP("ElnToolsArmor", "Electrical Age - Tools & Armor");
        TR_GROUP("ElnOresMaterials", "Electrical Age - Ores & Materials");
        TR_GROUP("ElnMachines", "Electrical Age - Machines");
        TR_GROUP("ElnCreative", "Electrical Age - Creative");
        TR_GROUP("ElnOther", "Electrical Age - Other");
        NeoForge.EVENT_BUS.register(new ElnForgeEventsHandler());
        NeoForge.EVENT_BUS.register(new RoomThermalBlockEventsHandler());
        NeoForge.EVENT_BUS.register(new ElectricMinecartChargeReporter());
        NeoForge.EVENT_BUS.register(new ElnFMLEventsHandler());
        if (FMLEnvironment.dist == Dist.CLIENT) {
            mods.eln.client.ClientSetup.init();
        }
        Utils.println("Electrical age init done");
    }

    /** What used to be postInit. */
    private void loadComplete(FMLLoadCompleteEvent event) {
        Other.check();
        serverEventListener = new ServerEventListener();
    }

    private void buildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        CreativeTabPopulator.addEntries(event);
    }

    private void onServerStopped(ServerStoppedEvent ev) {
        TutorialSignElement.resetBalise();
        if (modbusServer != null) {
            modbusServer.destroy();
            modbusServer = null;
        }
        LightBlockEntity.observers.clear();
        NodeBlockEntity.clientList.clear();
        TeleporterElement.teleporterList.clear();
        IWirelessSignalSpot.spots.clear();
        clientLiveDataManager.stop();
        nodeManager.clear();
        ghostManager.clear();
        saveConfig = null;
        modbusServer = null;
        delayedTask.clear();
        DelayedBlockRemove.clear();
        serverEventListener.clear();
        nodeServer.stop();
        simulator.stop();
        LampSupplyElement.channelMap.clear();
        WirelessSignalTxElement.channelMap.clear();
        MqttManager.shutdown();
        MetricsSubsystem.shutdown();
    }

    private void onServerAboutToStart(ServerAboutToStartEvent ev) {
        modbusServer = new ModbusTcpServer(config.getIntOrElse("integrations.modbus.port", 1502));
        TeleporterElement.teleporterList.clear();
        LightBlockEntity.observers.clear();
        WirelessSignalTxElement.channelMap.clear();
        LampSupplyElement.channelMap.clear();
        clientLiveDataManager.start();
        simulator.init();
        simulator.addSlowProcess(wind = new WindProcess());

        if (config.getBooleanOrElse("entities.replicator.enabled", false)) simulator.addSlowProcess(new ReplicatorPopProcess());
        simulator.addSlowProcess(itemEnergyInventoryProcess = new ItemEnergyInventoryProcess());
    }

    private void onServerStarting(ServerStartingEvent ev) {
        MinecraftServer server = ev.getServer();
        ServerLevel overworld = server.overworld();
        ghostManagerNbt = overworld.getDataStorage().computeIfAbsent(GhostManagerNbt.FACTORY, "GhostManager");
        saveConfig = overworld.getDataStorage().computeIfAbsent(SaveConfig.FACTORY, "SaveConfig");
        nodeManagerNbt = overworld.getDataStorage().computeIfAbsent(NodeManagerNbt.FACTORY, "NodeManager");
        nodeServer.init();
        OreScannerManager.regenOreScannerFactors();
        BiomeClimateService.auditMissingBiomeProfilesAtStartup();
    }

    public double LVP() {
        return 1000 * config.getDoubleOrElse("balance.cables.powerFactor", 1.0);
    }
    public double MVP() {
        return 2000 * config.getDoubleOrElse("balance.cables.powerFactor", 1.0);
    }
    public double HVP() {
        return 5000 * config.getDoubleOrElse("balance.cables.powerFactor", 1.0);
    }
    public double VVP() {
        return 15000 * config.getDoubleOrElse("balance.cables.powerFactor", 1.0);
    }

    private void updateCreativeTabIcons() {
        setTabIcon(creativeTabPowerElectronics, stack(sixNodeItem.getDescriptor(meta(33, 1))));
        setTabIcon(creativeTabSignalProcessing, stack(sixNodeItem.getDescriptor(meta(32, 0))));
        setTabIcon(creativeTabLighting, stack(sharedItem.getDescriptor(meta(4, 37))));
        setTabIcon(creativeTabCables, stack(sixNodeItem.getDescriptor(meta(34, 2))));
        setTabIcon(creativeTabToolsArmor, stack(sharedItem.getDescriptor(meta(14, 0))));
        setTabIcon(creativeTabOresMaterials, stack(sharedItem.getDescriptor(meta(8, 7))));
        setTabIcon(creativeTabMachines, stack(transparentNodeItem.getDescriptor(meta(33, 4))));
        setTabIcon(creativeTabCreative, stack(sixNodeItem.getDescriptor(meta(3, 0))));
        if (creativeTabOther != creativeTabOresMaterials) {
            setTabIcon(creativeTabOther, stack(sharedItem.getDescriptor(meta(8, 0))));
        }
    }

    private void setTabIcon(CreativeModeTab tab, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) GenericCreativeTab.setIcon(tab, stack);
    }

    private static ItemStack stack(GenericItemUsingDamageDescriptor descriptor) {
        return descriptor == null ? null : descriptor.newItemStack();
    }

    private static ItemStack stack(mods.eln.generic.GenericItemBlockUsingDamageDescriptor descriptor) {
        return descriptor == null ? null : descriptor.newItemStack();
    }

    private static int meta(int group, int subId) {
        return subId + (group << 6);
    }

    public boolean isDevelopmentRun() {
        return !FMLLoader.isProduction();
    }
}
