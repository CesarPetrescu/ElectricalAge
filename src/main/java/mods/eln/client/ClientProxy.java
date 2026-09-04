package mods.eln.client;

import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import mods.eln.CommonProxy;
import mods.eln.Eln;
import mods.eln.entity.ReplicatorEntity;
import mods.eln.entity.ReplicatorRender;
import mods.eln.node.six.SixNodeEntity;
import mods.eln.node.six.SixNodeRender;
import mods.eln.node.transparent.TransparentNodeEntity;
import mods.eln.node.transparent.TransparentNodeRender;
import mods.eln.sixnode.tutorialsign.TutorialSignOverlay;
import mods.eln.sound.SoundClientEventListener;
import net.minecraft.client.model.ModelSilverfish;
import com.google.common.collect.ImmutableMap;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import paulscode.sound.SoundSystemConfig;

import java.util.Map;

public class ClientProxy extends CommonProxy {

    public static UuidManager uuidManager;
    public static SoundClientEventListener soundClientEventListener;

    @Override
    public void preInit() {
        // 1.8+: renderers are created per RenderManager through a factory, and the RenderManager
        // consumes the factory map between preInit and init - so this cannot live in registerRenderers.
        RenderingRegistry.registerEntityRenderingHandler(ReplicatorEntity.class,
                manager -> new ReplicatorRender(manager, new ModelSilverfish(), 0.3f));

        // 1.8+: an item's inventory model is looked up by an explicit ModelResourceLocation per
        // metadata. Only the ore item has real per-meta block models; the descriptor-driven items
        // (six node, transparent node, shared item) render through IItemRenderer and are bound to a
        // TileEntityItemStackRenderer in phase 3.
        registerOreItemModels();
    }

    private static void registerOreItemModels() {
        Item ore = Item.getItemFromBlock(Eln.oreBlock);
        if (ore == null) return;
        for (Map.Entry<Integer, String> e : ORE_MODELS.entrySet()) {
            ModelLoader.setCustomModelResourceLocation(ore, e.getKey(),
                    new ModelResourceLocation(new ResourceLocation(Eln.MODID, "ore_" + e.getValue()), "inventory"));
        }
    }

    private static final Map<Integer, String> ORE_MODELS = ImmutableMap.of(
            1, "copperore", 4, "leadore", 5, "tungstenore", 6, "cinnabarore");

    @Override
    public void registerRenderers() {
        new ClientPacketHandler();
        ClientRegistry.bindTileEntitySpecialRenderer(SixNodeEntity.class, new SixNodeRender());
        ClientRegistry.bindTileEntitySpecialRenderer(TransparentNodeEntity.class, new TransparentNodeRender());

        // TODO(phase 3): IItemRenderer is gone since 1.8. The four descriptor-driven items
        // (transparentNodeItem, sixNodeItem, sharedItem, sharedItemStackOne) get one
        // TileEntityItemStackRenderer that dispatches to their mods.eln.client.itemrender.IItemRenderer
        // bodies, bound through Item.setTileEntityItemStackRenderer + a builtin/generated model.

        Eln.clientKeyHandler = new ClientKeyHandler();
        MinecraftForge.EVENT_BUS.register(Eln.clientKeyHandler);
        MinecraftForge.EVENT_BUS.register(new TutorialSignOverlay());
        uuidManager = new UuidManager();
        soundClientEventListener = new SoundClientEventListener(uuidManager);

        if (Eln.config.getBooleanOrElse("updates.versionCheck.enabled", true))
            MinecraftForge.EVENT_BUS.register(VersionCheckerHandler.getInstance());

        new FrameTime();
        new ConnectionListener();

        if (Eln.config.getIntOrElse("ui.audio.soundChannels", 200) > 0) {
            SoundSystemConfig.setNumberNormalChannels(Math.max(SoundSystemConfig.getNumberNormalChannels(), Eln.config.getIntOrElse("ui.audio.soundChannels", 200)));
        }
    }
}
