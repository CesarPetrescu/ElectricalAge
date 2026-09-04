@file:Suppress("NAME_SHADOWING")
package mods.eln.ghost

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction.Companion.fromFacing
import mods.eln.node.transparent.TransparentNodeEntity
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.block.properties.PropertyInteger
import net.minecraft.block.state.BlockFaceShape
import net.minecraft.block.state.BlockStateContainer
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumBlockRenderType
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
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

    override fun createBlockState(): BlockStateContainer = BlockStateContainer(this, SHAPE)

    override fun getStateFromMeta(meta: Int): IBlockState =
        defaultState.withProperty(SHAPE, meta.coerceIn(tCube, tLadder))

    override fun getMetaFromState(state: IBlockState): Int = state.getValue(SHAPE)

    private fun shapeOf(state: IBlockState) = state.getValue(SHAPE)

    override fun getBoundingBox(state: IBlockState, world: IBlockAccess, pos: BlockPos): AxisAlignedBB =
        when (shapeOf(state)) {
            tFloor -> FLOOR_BOX
            tLadder -> LADDER_BOX
            else -> FULL_BLOCK_AABB
        }

    override fun getItemDropped(state: IBlockState, random: Random, fortune: Int): Item? = null

    override fun addCollisionBoxToList(
        state: IBlockState, world: World, pos: BlockPos, entityBox: AxisAlignedBB,
        collidingBoxes: MutableList<AxisAlignedBB>, entity: Entity?, isActualState: Boolean
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
                        entityBox, collidingBoxes as MutableList<AxisAlignedBB?>, element.elementCoordinate
                    )
                } else {
                    super.addCollisionBoxToList(state, world, pos, entityBox, collidingBoxes, entity, isActualState)
                }
            }
        }
    }

    override fun getSelectedBoundingBox(state: IBlockState, world: World, pos: BlockPos): AxisAlignedBB =
        when (shapeOf(state)) {
            tFloor -> FLOOR_BOX.offset(pos)
            tLadder -> LADDER_BOX.offset(pos)
            else -> super.getSelectedBoundingBox(state, world, pos)
        }

    override fun collisionRayTrace(
        blockState: IBlockState, world: World, pos: BlockPos, startVec: Vec3d, endVec: Vec3d
    ): RayTraceResult? = rayTrace(pos, startVec, endVec, getBoundingBox(blockState, world, pos))

    override fun isLadder(state: IBlockState, world: IBlockAccess, pos: BlockPos, entity: EntityLivingBase?): Boolean =
        shapeOf(state) == tLadder

    override fun isOpaqueCube(state: IBlockState): Boolean = false

    override fun isFullCube(state: IBlockState): Boolean = false

    override fun getRenderType(state: IBlockState): EnumBlockRenderType = EnumBlockRenderType.INVISIBLE

    override fun getPickBlock(
        state: IBlockState, target: RayTraceResult, world: World, pos: BlockPos, player: EntityPlayer
    ): ItemStack = ItemStack.EMPTY

    override fun getBlockFaceShape(
        world: IBlockAccess, state: IBlockState, pos: BlockPos, face: EnumFacing
    ): BlockFaceShape = BlockFaceShape.UNDEFINED

    override fun breakBlock(world: World, pos: BlockPos, state: IBlockState) {
        if (!world.isRemote) {
            getElement(world, pos.x, pos.y, pos.z)?.breakBlock()
        }
        super.breakBlock(world, pos, state)
    }

    override fun onBlockActivated(
        world: World, pos: BlockPos, state: IBlockState, player: EntityPlayer,
        hand: EnumHand, side: EnumFacing, vx: Float, vy: Float, vz: Float
    ): Boolean {
        if (!world.isRemote) {
            val element = getElement(world, pos.x, pos.y, pos.z)
            if (element != null) return element.onBlockActivated(player, fromFacing(side), vx, vy, vz)
        }
        return true
    }

    fun getElement(world: World?, x: Int, y: Int, z: Int): GhostElement? {
        return Eln.ghostManager.getGhost(Coordinate(x, y, z, world!!))
    }

    override fun getBlockHardness(blockState: IBlockState, world: World, pos: BlockPos): Float = 0.5f

    val nodeUuid: String
        get() = "g"

    companion object {
        const val tCube = 0
        const val tFloor = 1
        const val tLadder = 2

        @JvmField
        val SHAPE: PropertyInteger = PropertyInteger.create("shape", tCube, tLadder)

        private val FLOOR_BOX = AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 0.0625, 1.0)
        private val LADDER_BOX = AxisAlignedBB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }
}
