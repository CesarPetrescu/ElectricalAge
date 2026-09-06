@file:Suppress("NAME_SHADOWING")
package mods.eln.ghost

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction.Companion.fromFacing
import mods.eln.misc.IMetaBlock
import mods.eln.node.NodeBlock
import mods.eln.node.transparent.TransparentNodeEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraft.core.Direction as EnumFacing

/**
 * The invisible placeholder a multiblock machine puts in the cells it occupies.
 *
 * Its shape used to be its metadata value ([tCube], [tFloor], [tLadder]); it is the [SHAPE]
 * state property. A cube ghost collides with the owning machine's boxes (the machine's tile
 * entity answers for every cell it covers); a floor ghost is a slab; a ladder ghost is passable.
 */
class GhostBlock : Block(Properties.of().mapColor(MapColor.METAL).strength(0.5f).noOcclusion().dynamicShape().noLootTable()), IMetaBlock {

    init {
        registerDefaultState(stateDefinition.any().setValue(SHAPE, tCube))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(SHAPE)
    }

    override fun stateForMeta(meta: Int): BlockState = defaultBlockState().setValue(SHAPE, meta.coerceIn(tCube, tLadder))

    override fun metaOfState(state: BlockState): Int = state.getValue(SHAPE)

    private fun shapeOf(state: BlockState) = state.getValue(SHAPE)

    override fun getCollisionShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        when (shapeOf(state)) {
            tFloor -> FLOOR_SHAPE
            tLadder -> Shapes.empty()  // a ladder ghost is passable
            else -> {
                val element = (world as? Level)?.let { getElement(it, pos.x, pos.y, pos.z) }
                val te = element?.observatorCoordonate?.tileEntity
                if (te is TransparentNodeEntity) {
                    val boxes = ArrayList<AABB?>()
                    te.addCollisionBoxesToList(AABB(pos).inflate(4.0), boxes, element.elementCoordinate)
                    var shape = Shapes.empty()
                    for (bb in boxes) {
                        if (bb == null) continue
                        shape = Shapes.or(shape, Shapes.create(bb.move(-pos.x.toDouble(), -pos.y.toDouble(), -pos.z.toDouble())))
                    }
                    shape
                } else Shapes.block()
            }
        }

    /** The outline (1.7.10's selected bounding box). */
    override fun getShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        when (shapeOf(state)) {
            tFloor -> FLOOR_SHAPE
            tLadder -> Shapes.empty()
            else -> Shapes.block()
        }

    override fun isLadder(state: BlockState, world: LevelReader, pos: BlockPos, entity: LivingEntity): Boolean =
        shapeOf(state) == tLadder

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.INVISIBLE

    override fun getCloneItemStack(state: BlockState, target: HitResult, world: LevelReader, pos: BlockPos, player: Player): ItemStack = ItemStack.EMPTY

    override fun onRemove(state: BlockState, world: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!world.isClientSide && !state.`is`(newState.block)) {
            getElement(world, pos.x, pos.y, pos.z)?.breakBlock()
        }
        super.onRemove(state, world, pos, newState, movedByPiston)
    }

    fun onBlockActivated(
        world: Level, pos: BlockPos, state: BlockState, player: Player,
        hand: InteractionHand, side: EnumFacing, vx: Float, vy: Float, vz: Float
    ): Boolean {
        if (!world.isClientSide) {
            val element = getElement(world, pos.x, pos.y, pos.z)
            if (element != null) return element.onBlockActivated(player, fromFacing(side), vx, vy, vz)
        }
        return true
    }

    override fun useItemOn(stack: ItemStack, state: BlockState, world: Level, pos: BlockPos, player: Player, hand: InteractionHand, hit: BlockHitResult): ItemInteractionResult {
        val (vx, vy, vz) = NodeBlock.hitFractions(hit, pos)
        return if (onBlockActivated(world, pos, state, player, hand, hit.direction, vx, vy, vz)) ItemInteractionResult.sidedSuccess(world.isClientSide)
        else ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
    }

    override fun useWithoutItem(state: BlockState, world: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        val (vx, vy, vz) = NodeBlock.hitFractions(hit, pos)
        return if (onBlockActivated(world, pos, state, player, InteractionHand.MAIN_HAND, hit.direction, vx, vy, vz)) InteractionResult.sidedSuccess(world.isClientSide)
        else InteractionResult.PASS
    }

    fun getElement(world: Level?, x: Int, y: Int, z: Int): GhostElement? {
        return Eln.ghostManager.getGhost(Coordinate(x, y, z, world!!))
    }

    val nodeUuid: String
        get() = "g"

    companion object {
        const val tCube = 0
        const val tFloor = 1
        const val tLadder = 2

        @JvmField
        val SHAPE: IntegerProperty = IntegerProperty.create("shape", tCube, tLadder)

        private val FLOOR_SHAPE: VoxelShape = Shapes.create(AABB(0.0, 0.0, 0.0, 1.0, 0.0625, 1.0))
    }
}
