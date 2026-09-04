package mods.eln.lightblock

import mods.eln.misc.Coordinate
import net.minecraft.block.BlockContainer
import net.minecraft.block.material.Material
import net.minecraft.block.properties.PropertyInteger
import net.minecraft.block.state.BlockStateContainer
import net.minecraft.block.state.IBlockState
import net.minecraft.item.Item
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumBlockRenderType
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import java.util.Random

/**
 * The invisible block Electrical Age places to light a room.
 *
 * On 1.7.10 the light level lived in the block's metadata nibble. 1.8 replaced metadata with
 * block states, so the level is a [PropertyInteger] instead; [getMetaFromState] and
 * [getStateFromMeta] keep the 0..15 mapping the rest of the lighting code assumes.
 */
class LightBlock : BlockContainer(Material.AIR) {

    init {
        defaultState = blockState.baseState.withProperty(LIGHT, 0)
    }

    override fun createBlockState(): BlockStateContainer = BlockStateContainer(this, LIGHT)

    override fun getStateFromMeta(meta: Int): IBlockState =
        defaultState.withProperty(LIGHT, meta.coerceIn(0, 15))

    override fun getMetaFromState(state: IBlockState): Int = state.getValue(LIGHT)

    override fun getLightValue(state: IBlockState, world: IBlockAccess, pos: BlockPos): Int =
        state.getValue(LIGHT)

    override fun collisionRayTrace(
        blockState: IBlockState, world: World, pos: BlockPos, start: Vec3d, end: Vec3d
    ): RayTraceResult? = null

    override fun getCollisionBoundingBox(
        blockState: IBlockState, world: IBlockAccess, pos: BlockPos
    ): AxisAlignedBB? = NULL_AABB

    override fun isOpaqueCube(state: IBlockState): Boolean = false

    override fun isFullCube(state: IBlockState): Boolean = false

    override fun getRenderType(state: IBlockState): EnumBlockRenderType = EnumBlockRenderType.INVISIBLE

    override fun getItemDropped(state: IBlockState, random: Random, fortune: Int): Item? = null

    override fun quantityDropped(random: Random): Int = 0

    override fun isReplaceable(access: IBlockAccess, pos: BlockPos): Boolean = true

    override fun createNewTileEntity(world: World, meta: Int): TileEntity = LightBlockEntity()

    override fun breakBlock(world: World, pos: BlockPos, state: IBlockState) {
        val coord = Coordinate(pos.x, pos.y, pos.z, world)

        for (o in LightBlockEntity.observers) {
            o.lightBlockDestructor(coord)
        }

        super.breakBlock(world, pos, state)
    }

    override fun getLightOpacity(state: IBlockState): Int = 0

    companion object {
        @JvmField
        val LIGHT: PropertyInteger = PropertyInteger.create("light", 0, 15)
    }
}
