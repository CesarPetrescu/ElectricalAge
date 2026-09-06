package mods.eln.client;

import mods.eln.Eln;
import mods.eln.entity.ReplicatorEntity;
import mods.eln.entity.ReplicatorRender;
import mods.eln.node.six.SixNodeEntity;
import mods.eln.node.six.SixNodeRender;
import mods.eln.node.transparent.TransparentNodeEntity;
import mods.eln.node.transparent.TransparentNodeRender;
import mods.eln.sixnode.tutorialsign.TutorialSignOverlay;
import mods.eln.sound.SoundClientEventListener;
import net.minecraft.client.model.SilverfishModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client side of the mod's setup (what {@code ClientProxy} did). Renderers are registered
 * through {@link EntityRenderersEvent.RegisterRenderers}; the rest runs from common setup.
 */
@EventBusSubscriber(modid = Eln.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {
    }

    public static UuidManager uuidManager;
    public static SoundClientEventListener soundClientEventListener;

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SixNodeEntity.TYPE.get(), context -> new SixNodeRender());
        event.registerBlockEntityRenderer(TransparentNodeEntity.TYPE.get(), context -> new TransparentNodeRender());
        event.registerEntityRenderer(ReplicatorEntity.TYPE.get(),
            context -> new ReplicatorRender(context, new SilverfishModel<>(context.bakeLayer(ModelLayers.SILVERFISH)), 0.3f));
    }

    /** Called from FMLCommonSetupEvent on the client. */
    public static void init() {
        Eln.clientKeyHandler = new ClientKeyHandler();
        NeoForge.EVENT_BUS.register(Eln.clientKeyHandler);
        NeoForge.EVENT_BUS.register(new TutorialSignOverlay());
        uuidManager = new UuidManager();
        soundClientEventListener = new SoundClientEventListener(uuidManager);

        if (Eln.config.getBooleanOrElse("updates.versionCheck.enabled", true))
            NeoForge.EVENT_BUS.register(VersionCheckerHandler.getInstance());

        new FrameTime();
        new ConnectionListener();
    }
}
