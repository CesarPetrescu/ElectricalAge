package mods.eln.client.itemrender

import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType
import net.minecraft.item.ItemStack

/**
 * A stand-in for Forge's `IItemRenderer`, which was removed in 1.8 along with the rest of the
 * pre-JSON item pipeline.
 *
 * Electrical Age draws roughly 250 of its items as OBJ models through descriptor `renderItem`
 * bodies. On 1.12.2 those bodies still work - the fixed-function GL they use is unchanged - but
 * they have to be reached through a [net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer]
 * instead of an `IItemRenderer`. Keeping the old interface shape here means the descriptors do
 * not have to be rewritten twice: phase 3 binds a single TEISR that reads the current
 * [TransformType], maps it onto [ItemRenderType], and calls straight into these methods.
 *
 * @see mods.eln.node.six.SixNodeDescriptor
 * @see mods.eln.generic.GenericItemUsingDamageDescriptor
 */
interface IItemRenderer {

    /** The perspective an item is being drawn from. Mirrors the 1.7.10 enum of the same name. */
    enum class ItemRenderType {
        ENTITY,
        EQUIPPED,
        EQUIPPED_FIRST_PERSON,
        INVENTORY,
        FIRST_PERSON_MAP;

        companion object {
            /**
             * Maps 1.12.2's [TransformType] onto the legacy perspective the descriptors expect.
             * `GROUND` covers dropped items, `FIXED` covers item frames, and both hands collapse
             * onto the same first-person case because no descriptor distinguishes them.
             */
            @JvmStatic
            fun of(type: TransformType): ItemRenderType = when (type) {
                TransformType.GUI -> INVENTORY
                TransformType.GROUND, TransformType.FIXED -> ENTITY
                TransformType.FIRST_PERSON_LEFT_HAND, TransformType.FIRST_PERSON_RIGHT_HAND -> EQUIPPED_FIRST_PERSON
                TransformType.THIRD_PERSON_LEFT_HAND, TransformType.THIRD_PERSON_RIGHT_HAND -> EQUIPPED
                TransformType.HEAD, TransformType.NONE -> EQUIPPED
            }
        }
    }

    /** Extra transforms the old pipeline applied on the renderer's behalf. */
    enum class ItemRendererHelper {
        ENTITY_ROTATION,
        ENTITY_BOBBING,
        EQUIPPED_BLOCK,
        BLOCK_3D,
        INVENTORY_BLOCK
    }

    fun handleRenderType(item: ItemStack, type: ItemRenderType): Boolean

    fun shouldUseRenderHelper(type: ItemRenderType, item: ItemStack, helper: ItemRendererHelper): Boolean

    fun renderItem(type: ItemRenderType, item: ItemStack, vararg data: Any)
}
