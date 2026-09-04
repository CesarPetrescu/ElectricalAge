package mods.eln;

import mods.eln.misc.McBridge;
import mods.eln.registration.ElnRegistry;

import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import mods.eln.block.ArcClayBlock;
import mods.eln.block.ArcClayItemBlock;
import mods.eln.block.ArcMetalBlock;
import mods.eln.block.ArcMetalItemBlock;
import mods.eln.cable.CableRenderDescriptor;
import mods.eln.client.ClientKeyHandler;
import mods.eln.client.SoundLoader;
import mods.eln.config.JsonConfig;
import mods.eln.craft.CraftingRecipes;
import mods.eln.entity.ReplicatorPopProcess;
import mods.eln.eventhandlers.ElnFMLEventsHandler;
import mods.eln.eventhandlers.ElnForgeEventsHandler;
import mods.eln.eventhandlers.RoomThermalBlockEventsHandler;
import mods.eln.fluid.ElnFluidRegistry;
import mods.eln.fluid.FuelRegistry;
import mods.eln.fluid.ThermalRegistry;
import mods.eln.fluid.FluidRegistrationKt;
import mods.eln.environment.BiomeClimateService;
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
import mods.eln.node.NodeBlockEntity;
import mods.eln.node.NodeManager;
import mods.eln.node.NodeManagerNbt;
import mods.eln.node.NodeServer;
import mods.eln.node.six.*;
import mods.eln.node.transparent.*;
import mods.eln.ore.OreBlock;
import mods.eln.ore.OreDescriptor;
import mods.eln.ore.OreItem;
import mods.eln.ore.OreScannerManager;
import mods.eln.packets.*;
import mods.eln.registration.ItemRegistration;
import mods.eln.registration.SingleNodeRegistration;
import mods.eln.registration.SixNodeRegistration;
import mods.eln.registration.TransparentNodeRegistration;
import mods.eln.railroad.ElectricMinecartChargeReporter;
import mods.eln.server.*;
import mods.eln.server.console.ElnConsoleCommands;
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
import mods.eln.transparentnode.computercraftio.PeripheralHandler;
import mods.eln.transparentnode.electricalfurnace.ElectricalFurnaceDescriptor;
import mods.eln.transparentnode.teleporter.TeleporterElement;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.command.ICommandManager;
import net.minecraft.command.ServerCommandManager;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.File;
import java.util.*;

import static mods.eln.i18n.I18N.TR;
import static mods.eln.i18n.I18N.TR_GROUP;
import static mods.eln.i18n.I18N.tr;

@Mod(
        modid = Eln.MODID,
        name = Eln.NAME,
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.12.2]")
public class Eln {
    @Instance(Eln.MODID)
    public static Eln instance;
    @SidedProxy(clientSide = "mods.eln.client.ClientProxy", serverSide = "mods.eln.CommonProxy")
    public static CommonProxy proxy;
    // Lower-case since 1.11: FMLModContainer rejects anything else. Registry names, packets and
    // the lang/model asset paths all derive from it.
    public final static String MODID = "eln";

    public Eln() {
        // Must run before any mod's preInit (FML constructs mod instances first): Eln's fluids use
        // Forge's universal bucket on 1.12.2. Not a static initializer, so the unit tests that touch
        // Eln's static fields do not drag Blocks/Items in without a Bootstrap.
        FluidRegistry.enableUniversalBucket();
    }
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
    public static SimpleNetworkWrapper elnNetwork;
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
    public static CreativeTabs creativeTab;
    public static CreativeTabs creativeTabPowerElectronics;
    public static CreativeTabs creativeTabSignalProcessing;
    public static CreativeTabs creativeTabLighting;
    public static CreativeTabs creativeTabCables;
    public static CreativeTabs creativeTabPowerDistribution;
    public static CreativeTabs creativeTabToolsArmor;
    public static CreativeTabs creativeTabOresMaterials;
    public static CreativeTabs creativeTabMachines;
    public static CreativeTabs creativeTabCreative;
    public static CreativeTabs creativeTabOther;
    public static Item swordCopper, hoeCopper, shovelCopper, pickaxeCopper, axeCopper;
    public static GenericItemUsingDamageDescriptorWithComment plateCopper;
    public static ItemArmor helmetCopper, chestplateCopper, legsCopper, bootsCopper;
    public static ItemArmor helmetECoal, plateECoal, legsECoal, bootsECoal;
    public static SharedItem sharedItem;
    public static SharedItem sharedItemStackOne;
    public static ItemStack wrenchItemStack;
    public static SixNodeBlock sixNodeBlock;
    public static TransparentNodeBlock transparentNodeBlock;
    public static OreBlock oreBlock;
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
    public static FMLEventChannel eventChannel;
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
        if (McBridge.isNothing(stack)) {
            stack = dictionnaryOreFromMod.get(name);
            stack = Utils.newItemStack(Item.getIdFromItem(stack.getItem()), stackSize, stack.getItemDamage());
        }
        return stack;
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        configureElnLogFile(event);
        proxy.preInit();

        elnNetwork = NetworkRegistry.INSTANCE.newSimpleChannel("electrical-age");
        elnNetwork.registerMessage(AchievePacketHandler.class, AchievePacket.class, 0, Side.SERVER);
        elnNetwork.registerMessage(TransparentNodeRequestPacketHandler.class, TransparentNodeRequestPacket.class, 1,
                Side.SERVER);
        elnNetwork.registerMessage(TransparentNodeResponsePacketHandler.class, TransparentNodeResponsePacket.class, 2,
                Side.CLIENT);
        elnNetwork.registerMessage(GhostNodeWailaRequestPacketHandler.class, GhostNodeWailaRequestPacket.class, 3,
                Side.SERVER);
        elnNetwork.registerMessage(GhostNodeWailaResponsePacketHandler.class, GhostNodeWailaResponsePacket.class, 4,
                Side.CLIENT);
        elnNetwork.registerMessage(SixNodeWailaRequestPacketHandler.class, SixNodeWailaRequestPacket.class, 5,
                Side.SERVER);
        elnNetwork.registerMessage(SixNodeWailaResponsePacketHandler.class, SixNodeWailaResponsePacket.class, 6,
                Side.CLIENT);

        ModContainer container = FMLCommonHandler.instance().findContainerFor(this);
        ModMetadata meta = event.getModMetadata();
        meta.modId = MODID;
        meta.version = Version.INSTANCE.getSimpleVersionName();
        meta.name = NAME;
        meta.description = tr("mod.meta.desc");
        meta.updateUrl = UPDATE_URL;
        meta.authorList = Arrays.asList(AUTHORS);
        meta.autogenerated = false; // Force to update from code

        Utils.println(Version.print());

        Side side = FMLCommonHandler.instance().getEffectiveSide();
        if (side == Side.CLIENT) MinecraftForge.EVENT_BUS.register(new SoundLoader());

        config = new JsonConfig(event.getSuggestedConfigurationFile());
        config.loadConfig();
        config.writeExampleFile();
        FuelRegistry.init(event.getSuggestedConfigurationFile());
        ThermalRegistry.init(event.getSuggestedConfigurationFile());
        MqttManager.init();
        MetricsSubsystem.refreshFromConfig();

        eventChannel = NetworkRegistry.INSTANCE.newEventDrivenChannel(channelName);

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
        instance = this;

        NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandler());

        Item itemCreativeTab = new Item().setTranslationKey("eln:elncreativetab");
        ElnRegistry.registerItem(itemCreativeTab, "eln.itemCreativeTab");

        creativeTabPowerElectronics = new GenericCreativeTab("ElnPowerElectronics", Items.REDSTONE);
        creativeTabCables = new GenericCreativeTab("ElnCables", Items.STRING);
        creativeTabPowerDistribution = new GenericCreativeTab("ElnPowerDistribution", Items.STRING);
        creativeTabSignalProcessing = new GenericCreativeTab("ElnSignalProcessing", Items.COMPARATOR);
        creativeTabLighting = new GenericCreativeTab("ElnLighting", Item.getItemFromBlock(Blocks.REDSTONE_LAMP));
        creativeTabToolsArmor = new GenericCreativeTab("ElnToolsArmor", Items.IRON_PICKAXE);
        creativeTabOresMaterials = new GenericCreativeTab("ElnOresMaterials", Items.IRON_INGOT);
        creativeTabMachines = new GenericCreativeTab("ElnMachines", Item.getItemFromBlock(Blocks.DISPENSER));
        creativeTabCreative = new GenericCreativeTab("ElnCreative", Items.NETHER_STAR);
        creativeTabOther = creativeTabOresMaterials;
        creativeTab = creativeTabOther;

        oreBlock = (OreBlock) new OreBlock().setCreativeTab(creativeTabOresMaterials).setTranslationKey("OreEln");

        arcClayBlock = new ArcClayBlock();
        arcMetalBlock = new ArcMetalBlock();

        sharedItem =
                (SharedItem) new SharedItem().setCreativeTab(creativeTabOther).setMaxStackSize(64).setTranslationKey("sharedItem");

        sharedItemStackOne =
                (SharedItem) new SharedItem().setCreativeTab(creativeTabOther).setMaxStackSize(1).setTranslationKey(
                        "sharedItemStackOne");

        transparentNodeBlock = (TransparentNodeBlock) new TransparentNodeBlock(Material.IRON,
                TransparentNodeEntity.class).setCreativeTab(creativeTabOther);
        sixNodeBlock =
                (SixNodeBlock) new SixNodeBlock(Material.PLANTS, SixNodeEntity.class).setCreativeTab(creativeTabOther);

        ghostBlock = (GhostBlock) new GhostBlock();
        lightBlock = new LightBlock();

        obj.loadAllElnModels();

        ElnRegistry.registerItem(sharedItem, "Eln.sharedItem");
        ElnRegistry.registerItem(sharedItemStackOne, "Eln.sharedItemStackOne");
        ElnRegistry.registerBlock(ghostBlock, "Eln.ghostBlock");
        ElnRegistry.registerBlock(lightBlock, "Eln.lightBlock");
        ElnRegistry.registerBlock(sixNodeBlock, "Eln.SixNode", SixNodeItem.class);
        ElnRegistry.registerBlock(transparentNodeBlock, "Eln.TransparentNode", TransparentNodeItem.class);
        ElnRegistry.registerBlock(oreBlock, "Eln.Ore", OreItem.class);
        ElnRegistry.registerBlock(arcClayBlock, "Eln.arc_clay_block", ArcClayItemBlock.class);
        ElnRegistry.registerBlock(arcMetalBlock, "Eln.arc_metal_block", ArcMetalItemBlock.class);
        ElnRegistry.registerTileEntity(TransparentNodeEntity.class, "TransparentNodeEntity");
        ElnRegistry.registerTileEntity(TransparentNodeEntityWithFluid.class, "TransparentNodeEntityWF");
        ElnRegistry.registerTileEntity(SixNodeEntity.class, "SixNodeEntity");
        ElnRegistry.registerTileEntity(LightBlockEntity.class, "LightBlockEntity");

        NodeManager.registerUuid(sixNodeBlock.getNodeUuid(), SixNode.class);
        NodeManager.registerUuid(transparentNodeBlock.getNodeUuid(), TransparentNode.class);

        // 1.12.2: the ItemBlocks are not in the registry until after preInit; take ours directly.
        sixNodeItem = (SixNodeItem) ElnRegistry.itemBlockOf(sixNodeBlock);
        transparentNodeItem = (TransparentNodeItem) ElnRegistry.itemBlockOf(transparentNodeBlock);

        oreItem = (OreItem) ElnRegistry.itemBlockOf(oreBlock);

        SixNode.sixNodeCacheList.add(new SixNodeCacheStd());

        LampLists.translateLampTypes(); // This MUST be called before block/item registration!

        SingleNodeRegistration.INSTANCE.registerSingle();
        SixNodeRegistration.INSTANCE.registerSix();
        TransparentNodeRegistration.INSTANCE.registerTransparent();
        ItemRegistration.INSTANCE.registerItem();

        updateCreativeTabIcons();

        ElnRegistry.registerOre("blockAluminum", arcClayBlock);
        ElnRegistry.registerOre("blockSteel", arcMetalBlock);

        AnalyticsHandler.INSTANCE.submitUpstreamAnalytics();
        AnalyticsHandler.INSTANCE.submitAgeSeriesAnalytics();
    }

    private void configureElnLogFile(FMLPreInitializationEvent event) {
        File runtimeDir = event.getSuggestedConfigurationFile().getParentFile().getParentFile();
        if (runtimeDir == null) return;

        File logDir = new File(runtimeDir, "logs");
        if (!logDir.exists() && !logDir.mkdirs()) return;

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        final String appenderName = "ELN_FILE";
        if (configuration.getAppenders().containsKey(appenderName)) return;

        // log4j 2.8 (1.12.2's runtime) dropped the 2.0-beta static factories; the builders exist
        // in every version from 2.7 on, including the 2.17 RFG puts on the dev classpath.
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

    @EventHandler
    public void modsLoaded(FMLPostInitializationEvent event) {
        Other.check();
        if (Other.ccLoaded) {
            PeripheralHandler.register();
        }
        registerCraftingRecipes();
    }

    private static void registerCraftingRecipes() {
        // Ensure every recipe defined in CraftingRecipes is registered.
        CraftingRecipes.INSTANCE.itemCrafting();
    }

    @EventHandler
    public void load(FMLInitializationEvent event) {
        final String[] names = OreDictionary.getOreNames();
        Collections.addAll(oreNames, names);
        proxy.registerRenderers();
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
        FluidRegistrationKt.registerElnFluids();
        MinecraftForge.EVENT_BUS.register(new ElnForgeEventsHandler());
        MinecraftForge.EVENT_BUS.register(new RoomThermalBlockEventsHandler());
        MinecraftForge.EVENT_BUS.register(new ElectricMinecartChargeReporter());
        MinecraftForge.EVENT_BUS.register(new ElnFMLEventsHandler());
        MinecraftForge.EVENT_BUS.register(this);
        FMLInterModComms.sendMessage("waila", "register", "mods.eln.integration.waila.WailaIntegration" +
                ".callbackRegister");
        Utils.println("Electrical age init done");
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        serverEventListener = new ServerEventListener();
    }

    @EventHandler
    public void onServerStopped(FMLServerStoppedEvent ev) {
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

    @EventHandler
    public void onServerStart(FMLServerAboutToStartEvent ev) {
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

    @EventHandler
    public void onServerStarting(FMLServerStartingEvent ev) {
        {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            WorldServer worldServer = server.getWorld(0);
            ghostManagerNbt = (GhostManagerNbt) worldServer.getMapStorage().getOrLoadData(GhostManagerNbt.class, "GhostManager");
            if (ghostManagerNbt == null) {
                ghostManagerNbt = new GhostManagerNbt("GhostManager");
                worldServer.getMapStorage().setData("GhostManager", ghostManagerNbt);
            }
            saveConfig = (SaveConfig) worldServer.getMapStorage().getOrLoadData(SaveConfig.class, "SaveConfig");
            if (saveConfig == null) {
                saveConfig = new SaveConfig("SaveConfig");
                worldServer.getMapStorage().setData("SaveConfig", saveConfig);
            }
            nodeManagerNbt = (NodeManagerNbt) worldServer.getMapStorage().getOrLoadData(NodeManagerNbt.class, "NodeManager");
            if (nodeManagerNbt == null) {
                nodeManagerNbt = new NodeManagerNbt("NodeManager");
                worldServer.getMapStorage().setData("NodeManager", nodeManagerNbt);
            }
            nodeServer.init();
        }
        {
            MinecraftServer s = FMLCommonHandler.instance().getMinecraftServerInstance();
            ICommandManager command = s.getCommandManager();
            ServerCommandManager manager = (ServerCommandManager) command;
            manager.registerCommand(new ElnConsoleCommands());
        }
        OreScannerManager.regenOreScannerFactors();
        BiomeClimateService.auditMissingBiomeProfilesAtStartup();
        mods.eln.devtest.SmokeTest.registerIfRequested();
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
        setTabIcon(creativeTabPowerElectronics, stack(sixNodeItem, meta(33, 1)));
        setTabIcon(creativeTabSignalProcessing, stack(sixNodeItem, meta(32, 0)));
        setTabIcon(creativeTabLighting, stack(sharedItem, meta(4, 37)));
        setTabIcon(creativeTabCables, stack(sixNodeItem, meta(34, 2)));
        setTabIcon(creativeTabToolsArmor, stack(sharedItem, meta(14, 0)));
        setTabIcon(creativeTabOresMaterials, stack(sharedItem, meta(8, 7)));
        setTabIcon(creativeTabMachines, stack(transparentNodeItem, meta(33, 4)));
        setTabIcon(creativeTabCreative, stack(sixNodeItem, meta(3, 0)));
        if (creativeTabOther != creativeTabOresMaterials) {
            setTabIcon(creativeTabOther, stack(sharedItem, meta(8, 0)));
        }
    }

    private void setTabIcon(CreativeTabs tab, ItemStack stack) {
        if (tab instanceof GenericCreativeTab && !McBridge.isNothing(stack) && stack.getItem() != null) {
            ((GenericCreativeTab) tab).setIcon(stack);
        }
    }

    private static ItemStack stack(Item item, int damage) {
        if (item == null) return null;
        return new ItemStack(item, 1, damage);
    }

    private static int meta(int group, int subId) {
        return subId + (group << 6);
    }

    public boolean isDevelopmentRun() {
        return (Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment");
    }
}
