package mods.eln.integration.create

import com.simibubi.create.content.kinetics.base.KineticBlock
import com.simibubi.create.foundation.block.IBE
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.BlockHitResult

class CreateAdapterBlock(val industrial: Boolean) : KineticBlock(BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL)), IBE<CreateAdapterEntity> {
    init { registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)) }
    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) { builder.add(FACING) }
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState = defaultBlockState().setValue(FACING, context.clickedFace)
    override fun getRotationAxis(state: BlockState): Direction.Axis = state.getValue(FACING).axis
    override fun hasShaftTowards(world: LevelReader, pos: BlockPos, state: BlockState, face: Direction): Boolean = face == state.getValue(FACING).opposite
    override fun getBlockEntityClass(): Class<CreateAdapterEntity> = CreateAdapterEntity::class.java
    override fun getBlockEntityType(): BlockEntityType<out CreateAdapterEntity> = if (industrial) CreateIntegration.industrialType.get() else CreateIntegration.basicType.get()
    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (!level.isClientSide) (level.getBlockEntity(pos) as? CreateAdapterEntity)?.let { player.openMenu(it) }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }
    companion object { val FACING = BlockStateProperties.FACING }
}
