package mods.eln;

import mods.eln.generic.CreativeTabPopulator;
import mods.eln.generic.GenericCreativeTab;
import mods.eln.generic.GenericItemUsingDamageDescriptorWithComment;
import mods.eln.generic.SharedItem;
import mods.eln.i18n.I18N;
import mods.eln.ore.OreDescriptor;
import mods.eln.ore.OreItem;
import mods.eln.registration.ElnRegistry;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;

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
    public static final HashMap<String, ItemStack> dictionnaryOreFromMod = new HashMap<>();
    public static Logger logger = LogManager.getLogger("ELN");
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
    public static SharedItem sharedItem;
    public static SharedItem sharedItemStackOne;
    public static OreItem oreItem;
    public static OreDescriptor oreCopper;
    public static GenericItemUsingDamageDescriptorWithComment dustCopper;
    public static final double SVU = 5;
    public static final double signalVoltageAcceptNegative = -0.5;
    public static final double signalVoltageAcceptPositive = SVU + 0.5;
    public static final double SVII = gateInputCurrent / SVU, SVUinv = 1.0 / SVU;
    public static final double SVP = gateOutputCurrent * SVU;

    public Eln(IEventBus modBus, ModContainer container) {
        instance = this;
        LOGGER.info("Electrical Age {} constructing on NeoForge", container.getModInfo().getVersion());
        // 1.21 has no preInit: content is constructed here, in the mod constructor, and reaches the
        // registries through ElnRegistry when RegisterEvent fires (see its class comment).
        registerContent();
        modBus.addListener(this::buildCreativeTabContents);
        mods.eln.devtest.DevHooks.registerIfRequested();
    }

    public static ItemStack findItemStack(String name, int stackSize) {
        ItemStack stack = ElnRegistry.findItemStack(name, stackSize);
        if (stack == null || stack.isEmpty()) {
            ItemStack dict = dictionnaryOreFromMod.get(name);
            if (dict != null) stack = dict.copyWithCount(stackSize);
        }
        return stack;
    }

    /** ui.icons.noSymbols from Eln.cfg (JsonConfig is ported in phase 1; until then the default). */
    public static boolean uiIconsNoSymbols() {
        return false;
    }

    private void registerContent() {
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

        sharedItem = new SharedItem("Eln.sharedItem");
        sharedItem.setCreativeTab(creativeTabOther).setMaxStackSize(64);
        sharedItemStackOne = new SharedItem("Eln.sharedItemStackOne");
        sharedItemStackOne.setCreativeTab(creativeTabOther).setMaxStackSize(1);
        oreItem = new OreItem();

        // Phase 0 of the 1.21.1 port: a sample of the content, registered through the flattened
        // descriptor families, until ItemRegistration and the node registrations are ported (phase 1).
        registerPhase0Sample();
    }

    private void registerPhase0Sample() {
        sharedItem.setCreativeTabForGroup(5, creativeTabOresMaterials);
        sharedItem.setCreativeTabForGroup(6, creativeTabOresMaterials);
        oreItem.setCreativeTabForGroup(0, creativeTabOresMaterials);

        dustCopper = new GenericItemUsingDamageDescriptorWithComment(I18N.TR_NAME(I18N.Type.NONE, "Copper Dust"), new String[]{});
        sharedItem.addElement(1 + (5 << 6), dustCopper);
        sharedItem.addElement(2 + (5 << 6), new GenericItemUsingDamageDescriptorWithComment(I18N.TR_NAME(I18N.Type.NONE, "Iron Dust"), new String[]{}));
        sharedItem.addElement(1 + (6 << 6), new GenericItemUsingDamageDescriptorWithComment(I18N.TR_NAME(I18N.Type.NONE, "Copper Ingot"), new String[]{}));

        oreCopper = new OreDescriptor(I18N.TR_NAME(I18N.Type.NONE, "Copper Ore"), 1, 30, 6, 10, 0, 80);
        oreItem.addDescriptor(1, oreCopper);
        oreItem.addDescriptor(4, new OreDescriptor(I18N.TR_NAME(I18N.Type.NONE, "Lead Ore"), 4, 8, 3, 9, 0, 24));
        oreItem.addDescriptor(5, new OreDescriptor(I18N.TR_NAME(I18N.Type.NONE, "Tungsten Ore"), 5, 6, 3, 9, 0, 32));
        oreItem.addDescriptor(6, new OreDescriptor(I18N.TR_NAME(I18N.Type.NONE, "Cinnabar Ore"), 6, 3, 3, 9, 0, 32));

        ElnRegistry.afterItems(() -> GenericCreativeTab.setIcon(creativeTabOresMaterials, dustCopper.newItemStack()));
    }

    private void buildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        CreativeTabPopulator.addEntries(event);
    }
}
