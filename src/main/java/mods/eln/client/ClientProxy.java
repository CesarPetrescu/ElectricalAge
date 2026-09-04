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
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;
import paulscode.sound.SoundSystemConfig;

public class ClientProxy extends CommonProxy {

    public static UuidManager uuidManager;
    public static SoundClientEventListener soundClientEventListener;

    @Override
    public void registerRenderers() {
        new ClientPacketHandler();
        ClientRegistry.bindTileEntitySpecialRenderer(SixNodeEntity.class, new SixNodeRender());
        ClientRegistry.bindTileEntitySpecialRenderer(TransparentNodeEntity.class, new TransparentNodeRender());

        MinecraftForgeClient.registerItemRenderer(Eln.transparentNodeItem, Eln.transparentNodeItem);
        MinecraftForgeClient.registerItemRenderer(Eln.sixNodeItem, Eln.sixNodeItem);
        MinecraftForgeClient.registerItemRenderer(Eln.sharedItem, Eln.sharedItem);
        MinecraftForgeClient.registerItemRenderer(Eln.sharedItemStackOne, Eln.sharedItemStackOne);

        // 1.8+: renderers are created per RenderManager through a factory, not registered as instances.
        RenderingRegistry.registerEntityRenderingHandler(ReplicatorEntity.class,
                manager -> new ReplicatorRender(manager, new ModelSilverfish(), 0.3f));

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
