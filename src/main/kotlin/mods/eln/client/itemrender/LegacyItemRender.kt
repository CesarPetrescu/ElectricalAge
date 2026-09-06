package mods.eln.client.itemrender

import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

/**
 * A stand-in for Forge's `IItemRenderer`, which was removed in 1.8 along with the rest of the
 * pre-JSON item pipeline.
 *
 * Electrical Age draws roughly 250 of its items as OBJ models through descriptor `renderItem`
 * bodies. Keeping the old interface shape means those bodies are not rewritten per Minecraft
 * version: on 1.21 a single `BlockEntityWithoutLevelRenderer` (phase 3) reads the current
 * [ItemDisplayContext], maps it onto [ItemRenderType], and calls straight into these methods.
 * Plain 2D items no longer come through here at all - their JSON model draws them.
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
             * Maps 1.21's [ItemDisplayContext] onto the legacy perspective the descriptors expect.
             * `GROUND` covers dropped items, `FIXED` covers item frames, and both hands collapse
             * onto the same first-person case because no descriptor distinguishes them.
             */
            @JvmStatic
            fun of(type: ItemDisplayContext): ItemRenderType = when (type) {
                ItemDisplayContext.GUI -> INVENTORY
                ItemDisplayContext.GROUND, ItemDisplayContext.FIXED -> ENTITY
                ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND -> EQUIPPED_FIRST_PERSON
                ItemDisplayContext.THIRD_PERSON_LEFT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND -> EQUIPPED
                ItemDisplayContext.HEAD, ItemDisplayContext.NONE -> EQUIPPED
                else -> EQUIPPED
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
