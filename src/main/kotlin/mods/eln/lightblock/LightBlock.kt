package mods.eln.lightblock

import mods.eln.misc.Coordinate
import net.minecraft.block.BlockContainer
import net.minecraft.block.material.Material
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.phys.AABB
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import java.util.Random
import mods.eln.misc.isReplaceable

/**
 * The invisible block Electrical Age places to light a room.
 *
 * On 1.7.10 the light level lived in the block's metadata nibble. 1.8 replaced metadata with
 * block states, so the level is a [IntegerProperty] instead; [getMetaFromState] and
 * [getStateFromMeta] keep the 0..15 mapping the rest of the lighting code assumes.
 */
class LightBlock : BlockContainer(Material.AIR) {

    init {
        defaultState = blockState.baseState.withProperty(LIGHT, 0)
    }

    override fun createBlockState(): StateDefinition = StateDefinition(this, LIGHT)

    override fun getStateFromMeta(meta: Int): BlockState =
        defaultState.withProperty(LIGHT, meta.coerceIn(0, 15))

    override fun getMetaFromState(state: BlockState): Int = state.getValue(LIGHT)

    override fun getLightValue(state: BlockState, world: BlockGetter, pos: BlockPos): Int =
        state.getValue(LIGHT)

    override fun collisionRayTrace(
        blockState: BlockState, world: Level, pos: BlockPos, start: Vec3, end: Vec3
    ): HitResult? = null

    override fun getCollisionBoundingBox(
        blockState: BlockState, world: BlockGetter, pos: BlockPos
    ): AABB? = NULL_AABB

    override fun isOpaqueCube(state: BlockState): Boolean = false

    override fun isFullCube(state: BlockState): Boolean = false

    override fun getRenderType(state: BlockState): RenderShape = RenderShape.INVISIBLE

    override fun getItemDropped(state: BlockState, random: Random, fortune: Int): Item? = null

    override fun quantityDropped(random: Random): Int = 0

    override fun isReplaceable(access: BlockGetter, pos: BlockPos): Boolean = true

    override fun createNewTileEntity(world: Level, meta: Int): BlockEntity = LightBlockEntity()

    override fun breakBlock(world: Level, pos: BlockPos, state: BlockState) {
        val coord = Coordinate(pos.x, pos.y, pos.z, world)

        for (o in LightBlockEntity.observers) {
            o.lightBlockDestructor(coord)
        }

        super.breakBlock(world, pos, state)
    }

    override fun getLightOpacity(state: BlockState): Int = 0

    companion object {
        @JvmField
        val LIGHT: IntegerProperty = IntegerProperty.create("light", 0, 15)
    }
}
