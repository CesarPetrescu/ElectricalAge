package mods.eln.lightblock

import mods.eln.misc.Coordinate
import mods.eln.misc.IMetaBlock
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.material.PushReaction
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * The invisible block Electrical Age places to light a room.
 *
 * On 1.7.10 the light level lived in the block's metadata nibble; it is the [LIGHT] state
 * property, and the block's own light emission. It is air-like: no shape, no collision,
 * replaceable, no drops.
 */
class LightBlock : Block(
    Properties.of().air().replaceable().noCollission().noOcclusion().noLootTable().instabreak()
        .pushReaction(PushReaction.DESTROY).lightLevel { it.getValue(LIGHT) }
), EntityBlock, IMetaBlock {

    init {
        registerDefaultState(stateDefinition.any().setValue(LIGHT, 0))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(LIGHT)
    }

    override fun stateForMeta(meta: Int): BlockState = defaultBlockState().setValue(LIGHT, meta.coerceIn(0, 15))

    override fun metaOfState(state: BlockState): Int = state.getValue(LIGHT)

    override fun getShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = Shapes.empty()

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.INVISIBLE

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = LightBlockEntity(pos, state)

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? =
        if (level.isClientSide) null else BlockEntityTicker { _, _, _, entity -> (entity as? LightBlockEntity)?.update() }

    override fun onRemove(state: BlockState, world: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!state.`is`(newState.block)) {
            val coord = Coordinate(pos.x, pos.y, pos.z, world)
            for (o in LightBlockEntity.observers) {
                o.lightBlockDestructor(coord)
            }
        }
        super.onRemove(state, world, pos, newState, movedByPiston)
    }

    override fun getLightBlock(state: BlockState, world: BlockGetter, pos: BlockPos): Int = 0

    companion object {
        @JvmField
        val LIGHT: IntegerProperty = IntegerProperty.create("light", 0, 15)
    }
}
