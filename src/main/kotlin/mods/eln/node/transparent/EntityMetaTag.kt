package mods.eln.node.transparent

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * Used to differentiate between subclasses of TransparentNodeEntity, so that
 * our TEs can implement different interfaces depending on what functionality
 * they have.
 */
enum class EntityMetaTag(val meta: Int, val cls: Class<*>, val create: (BlockPos, BlockState) -> BlockEntity) {
    Fluid(1, TransparentNodeEntityWithFluid::class.java, { pos, state -> TransparentNodeEntityWithFluid(pos, state) }),
    Basic(3, TransparentNodeEntity::class.java, { pos, state -> TransparentNodeEntity(pos, state) }); // 3, because this is the default value used in pre-metatag worlds
}
