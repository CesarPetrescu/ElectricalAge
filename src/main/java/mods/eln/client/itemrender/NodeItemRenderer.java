package mods.eln.client.itemrender;

import com.mojang.blaze3d.vertex.PoseStack;
import mods.eln.client.gl.FixedFunction;
import mods.eln.generic.DescriptorBlockItem;
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

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (!(stack.getItem() instanceof DescriptorBlockItem<?> item)) return;
        if (!(item.descriptor instanceof IItemRenderer renderer)) return;
        IItemRenderer.ItemRenderType type = IItemRenderer.ItemRenderType.of(context);
        poseStack.pushPose();
        if (type == IItemRenderer.ItemRenderType.INVENTORY) {
            poseStack.translate(0f, 1f, 0f);
            poseStack.scale(1f / 16f, -1f / 16f, 1f / 16f);
        } else {
            poseStack.translate(0.5f, 0.5f, 0.5f);
        }
        FixedFunction.begin(poseStack, buffers, light, overlay);
        try {
            renderer.renderItem(type, stack);
        } finally {
            FixedFunction.finish();
            poseStack.popPose();
        }
    }
}
