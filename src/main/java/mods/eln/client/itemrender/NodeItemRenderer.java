package mods.eln.client.itemrender;

import com.mojang.blaze3d.vertex.PoseStack;
import mods.eln.client.gl.FixedFunction;
import mods.eln.generic.DescriptorBlockItem;
import mods.eln.generic.GenericItemBlockUsingDamageDescriptor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the node items (six-node and transparent-node placers) through their descriptor's
 * 1.7.10 {@code renderItem} body: the flat icon with its voltage background in the inventory,
 * the OBJ model in the world and in hand. Their JSON model is {@code builtin/entity}, so vanilla
 * calls here after applying the model's display transform and centring a unit cube on the origin.
 *
 * The old pipeline's coordinate spaces are restored before the body runs: the inventory was a
 * 16x16 pixel square with y down; everything else was block space, the model sitting on the
 * block's centre. The per-type hand transforms the item families applied on 1.7.10 were tuned
 * for that renderer's hand space and are not used; the display transforms in the model are.
 */
public final class NodeItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static NodeItemRenderer instance;

    private NodeItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static NodeItemRenderer get() {
        if (instance == null) instance = new NodeItemRenderer();
        return instance;
    }

    private final java.util.Map<Object, Boolean> asBlockInInventory = new java.util.IdentityHashMap<>();

    /**
     * Whether the inventory icon is the model rather than the flat sprite: descriptors that asked
     * 1.7.10 for its INVENTORY_BLOCK helper (the fabricator, the wire machines), and descriptors
     * whose sprite the asset tree does not have (the model is better than a missing-texture square).
     * The data generator gives those items a block's GUI display transform.
     */
    public static boolean inventoryAsBlock(GenericItemBlockUsingDamageDescriptor descriptor, ItemStack stack, java.util.function.Predicate<ResourceLocation> textureExists) {
        if (!(descriptor instanceof IItemRenderer renderer)) return false;
        if (renderer.shouldUseRenderHelper(IItemRenderer.ItemRenderType.INVENTORY, stack, IItemRenderer.ItemRendererHelper.INVENTORY_BLOCK)) return true;
        String icon = descriptor.getIconName();
        return icon == null || !textureExists.test(ResourceLocation.fromNamespaceAndPath("eln", "textures/blocks/" + icon + ".png"));
    }

    private boolean inventoryAsBlock(DescriptorBlockItem<?> item, ItemStack stack) {
        return asBlockInInventory.computeIfAbsent(item.descriptor, d -> inventoryAsBlock(item.descriptor, stack,
            location -> Minecraft.getInstance().getResourceManager().getResource(location).isPresent()));
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (!(stack.getItem() instanceof DescriptorBlockItem<?> item)) return;
        if (!(item.descriptor instanceof IItemRenderer renderer)) return;
        IItemRenderer.ItemRenderType type = IItemRenderer.ItemRenderType.of(context);
        boolean flatIcon = type == IItemRenderer.ItemRenderType.INVENTORY && !inventoryAsBlock(item, stack);
        boolean blockIcon = type == IItemRenderer.ItemRenderType.INVENTORY && !flatIcon;
        if (blockIcon) type = IItemRenderer.ItemRenderType.ENTITY;
        poseStack.pushPose();
        if (flatIcon) {
            poseStack.translate(0f, 1f, 0f);
            poseStack.scale(1f / 16f, -1f / 16f, 1f / 16f);
        } else {
            poseStack.translate(0.5f, 0.5f, 0.5f);
            // objItemScale sizes a model for 1.7.10's 10-pixel block icon; a block's GUI transform expects a unit cube
            if (blockIcon) poseStack.scale(0.7f, 0.7f, 0.7f);
        }
        FixedFunction.begin(poseStack, buffers, light, overlay);
        try {
            // Forge's inventory render of a flat custom icon turned GL_LIGHTING off around it: the sprite at its own colours
            if (flatIcon) mods.eln.client.gl.GL11.glDisable(mods.eln.client.gl.GL11.GL_LIGHTING);
            renderer.renderItem(type, stack);
        } finally {
            FixedFunction.finish();
            poseStack.popPose();
        }
    }
}
