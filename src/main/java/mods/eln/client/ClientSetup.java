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
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
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
        // the fluid-handling transparent nodes (EntityMetaTag.Fluid: the steam and gas turbines, the
        // radial motor, the fuel heat furnace, the heat exchanger) are their own block entity type
        event.registerBlockEntityRenderer(mods.eln.node.transparent.TransparentNodeEntityWithFluid.TYPE.get(), context -> new TransparentNodeRender());
        event.registerEntityRenderer(ReplicatorEntity.TYPE.get(),
            context -> new ReplicatorRender(context, new SilverfishModel<>(context.bakeLayer(ModelLayers.SILVERFISH)), 0.3f));
    }

    /** The mod's fluids: still/flowing sprites and tint, what 1.7.10's Fluid carried itself. Node items: their descriptor draws them. */
    @SubscribeEvent
    public static void onRegisterClientExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        var nodeItems = mods.eln.registration.ElnRegistry.getRegisteredItems().values().stream()
            .filter(item -> item instanceof mods.eln.generic.DescriptorBlockItem<?> d && d.descriptor instanceof mods.eln.client.itemrender.IItemRenderer)
            .toArray(net.minecraft.world.item.Item[]::new);
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return mods.eln.client.itemrender.NodeItemRenderer.get();
            }
        }, nodeItems);
        for (mods.eln.fluid.FluidRegistration.Entry entry : mods.eln.fluid.FluidRegistration.getEntries().values()) {
            String name = entry.getDef().name();
            int tint = entry.getDef().getColor() | 0xFF000000;
            event.registerFluidType(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
                @Override
                public net.minecraft.resources.ResourceLocation getStillTexture() {
                    return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Eln.MODID, "blocks/fluids/" + name + "_still");
                }

                @Override
                public net.minecraft.resources.ResourceLocation getFlowingTexture() {
                    return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Eln.MODID, "blocks/fluids/" + name + "_flow");
                }

                @Override
                public int getTintColor() {
                    return tint;
                }
            }, entry.getType().get());
        }
    }

    /** Key mappings are registered through the mod bus (1.7.10's ClientRegistry.registerKeyBinding). */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        Eln.clientKeyHandler = new ClientKeyHandler();
        Eln.clientKeyHandler.register(event);
        NeoForge.EVENT_BUS.register(Eln.clientKeyHandler);
    }

    /** Called from FMLCommonSetupEvent on the client. */
    public static void init() {
        NeoForge.EVENT_BUS.register(new TutorialSignOverlay());
        NeoForge.EVENT_BUS.register(new mods.eln.eventhandlers.ElnForgeEventsHandler());
        uuidManager = new UuidManager();
        soundClientEventListener = new SoundClientEventListener(uuidManager);

        if (Eln.config.getBooleanOrElse("updates.versionCheck.enabled", true))
            NeoForge.EVENT_BUS.register(VersionCheckerHandler.getInstance());

        new FrameTime();
        new ConnectionListener();
    }
}
