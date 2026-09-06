@file:Suppress("NAME_SHADOWING")
package mods.eln.ghost

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction.Companion.fromFacing
import mods.eln.node.transparent.TransparentNodeEntity
import net.minecraft.world.level.block.Block
import net.minecraft.block.material.Material
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.block.state.BlockFaceShape
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.RenderShape
import net.minecraft.core.Direction as EnumFacing
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.AABB
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import java.util.Random

/**
 * The invisible placeholder a multiblock machine puts in the cells it occupies.
 *
 * Its shape used to be its metadata value ([tCube], [tFloor], [tLadder]); on 1.12 that is a
 * blockstate property. The shape also used to be applied by mutating the block's own
 * `maxX`/`maxY`/`maxZ` fields around a `super.collisionRayTrace` call - those fields no longer
 * exist, because a block's bounds are now derived from its state, so each shape returns its own
 * bounding box instead.
 */
class GhostBlock : Block(Material.IRON) {

    init {
        defaultState = blockState.baseState.withProperty(SHAPE, tCube)
    }

    override fun createBlockState(): StateDefinition = StateDefinition(this, SHAPE)

    override fun getStateFromMeta(meta: Int): BlockState =
        defaultState.withProperty(SHAPE, meta.coerceIn(tCube, tLadder))

    override fun getMetaFromState(state: BlockState): Int = state.getValue(SHAPE)

    private fun shapeOf(state: BlockState) = state.getValue(SHAPE)

    override fun getBoundingBox(state: BlockState, world: BlockGetter, pos: BlockPos): AABB =
        when (shapeOf(state)) {
            tFloor -> FLOOR_BOX
            tLadder -> LADDER_BOX
            else -> FULL_BLOCK_AABB
        }

    override fun getItemDropped(state: BlockState, random: Random, fortune: Int): Item? = null

    override fun addCollisionBoxToList(
        state: BlockState, world: Level, pos: BlockPos, entityBox: AABB,
        collidingBoxes: MutableList<AABB>, entity: Entity?, isActualState: Boolean
    ) {
        when (shapeOf(state)) {
            tFloor -> addCollisionBoxToList(pos, entityBox, collidingBoxes, FLOOR_BOX)
            tLadder -> Unit  // a ladder ghost is passable
            else -> {
                val element = getElement(world, pos.x, pos.y, pos.z)
                val te = element?.observatorCoordonate?.tileEntity
                if (te is TransparentNodeEntity) {
                    @Suppress("UNCHECKED_CAST")
                    te.addCollisionBoxesToList(
                        entityBox, collidingBoxes as MutableList<AABB?>, element.elementCoordinate
                    )
                } else {
                    super.addCollisionBoxToList(state, world, pos, entityBox, collidingBoxes, entity, isActualState)
                }
            }
        }
    }

    override fun getSelectedBoundingBox(state: BlockState, world: Level, pos: BlockPos): AABB =
        when (shapeOf(state)) {
            tFloor -> FLOOR_BOX.offset(pos)
            tLadder -> LADDER_BOX.offset(pos)
            else -> super.getSelectedBoundingBox(state, world, pos)
        }

    override fun collisionRayTrace(
        blockState: BlockState, world: Level, pos: BlockPos, startVec: Vec3, endVec: Vec3
    ): HitResult? = rayTrace(pos, startVec, endVec, getBoundingBox(blockState, world, pos))

    override fun isLadder(state: BlockState, world: BlockGetter, pos: BlockPos, entity: LivingEntity?): Boolean =
        shapeOf(state) == tLadder

    override fun isOpaqueCube(state: BlockState): Boolean = false

    override fun isFullCube(state: BlockState): Boolean = false

    override fun getRenderType(state: BlockState): RenderShape = RenderShape.INVISIBLE

    override fun getPickBlock(
        state: BlockState, target: HitResult, world: Level, pos: BlockPos, player: Player
    ): ItemStack = ItemStack.EMPTY

    override fun getBlockFaceShape(
        world: BlockGetter, state: BlockState, pos: BlockPos, face: EnumFacing
    ): BlockFaceShape = BlockFaceShape.UNDEFINED

    override fun breakBlock(world: Level, pos: BlockPos, state: BlockState) {
        if (!world.isClientSide) {
            getElement(world, pos.x, pos.y, pos.z)?.breakBlock()
        }
        super.breakBlock(world, pos, state)
    }

    override fun onBlockActivated(
        world: Level, pos: BlockPos, state: BlockState, player: Player,
        hand: InteractionHand, side: EnumFacing, vx: Float, vy: Float, vz: Float
    ): Boolean {
        if (!world.isClientSide) {
            val element = getElement(world, pos.x, pos.y, pos.z)
            if (element != null) return element.onBlockActivated(player, fromFacing(side), vx, vy, vz)
        }
        return true
    }

    fun getElement(world: Level?, x: Int, y: Int, z: Int): GhostElement? {
        return Eln.ghostManager.getGhost(Coordinate(x, y, z, world!!))
    }

    override fun getBlockHardness(blockState: BlockState, world: Level, pos: BlockPos): Float = 0.5f

    val nodeUuid: String
        get() = "g"

    companion object {
        const val tCube = 0
        const val tFloor = 1
        const val tLadder = 2

        @JvmField
        val SHAPE: IntegerProperty = IntegerProperty.create("shape", tCube, tLadder)

        private val FLOOR_BOX = AABB(0.0, 0.0, 0.0, 1.0, 0.0625, 1.0)
        private val LADDER_BOX = AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }
}
