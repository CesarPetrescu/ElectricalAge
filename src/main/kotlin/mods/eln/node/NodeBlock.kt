package mods.eln.node

import mods.eln.misc.Direction
import mods.eln.misc.Direction.Companion.fromFacing
import mods.eln.misc.Utils.isRemote
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumBlockRenderType
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World

abstract class NodeBlock(material: Material?, tileEntityClass: Class<*>, blockItemNbr: Int) : Block(material) {

    var blockItemNbr: Int
    var tileEntityClass: Class<*>

    override fun getBlockHardness(blockState: IBlockState, world: World, pos: BlockPos): Float {
        return 1.0f
    }

    override fun getWeakPower(blockState: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Int {
        val entity = world.getTileEntity(pos) as? NodeBlockEntity ?: return 0
        return entity.isProvidingWeakPower(fromFacing(side))
    }

    /**
     * 1.8 split "can this block emit redstone at all" out of the per-side power query; without
     * it the world never asks [getWeakPower].
     */
    override fun canProvidePower(state: IBlockState): Boolean = true

    override fun canConnectRedstone(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing?): Boolean {
        val entity = world.getTileEntity(pos) as? NodeBlockEntity ?: return false
        return entity.canConnectRedstone(Direction.XN)
    }

    override fun isOpaqueCube(state: IBlockState): Boolean {
        return true
    }

    /**
     * Every node draws itself from a TileEntitySpecialRenderer, so the block itself contributes
     * no baked quads. This replaces both `renderAsNormalBlock` and the old integer render type.
     */
    override fun getRenderType(state: IBlockState): EnumBlockRenderType {
        return EnumBlockRenderType.INVISIBLE
    }

    override fun getLightValue(state: IBlockState, world: IBlockAccess, pos: BlockPos): Int {
        val entity = world.getTileEntity(pos)
        if (entity !is NodeBlockEntity) return 0
        return entity.lightValue
    }

    //client server
    open fun onBlockPlacedBy(world: World, pos: BlockPos, front: Direction?, entityLiving: EntityLivingBase?, metadata: Int): Boolean {
        // If you're getting a mysterious NPE here, it's probably because your ghost group overrides the base node. You're welcome.
        val tileEntity = world.getTileEntity(pos) as NodeBlockEntity
        tileEntity.onBlockPlacedBy(front, entityLiving, metadata)
        return true
    }

    //server
    override fun onBlockAdded(world: World, pos: BlockPos, state: IBlockState) {
        if (!world.isRemote) {
            val entity = world.getTileEntity(pos) as? NodeBlockEntity ?: return
            entity.onBlockAdded()
        }
    }

    //server
    override fun breakBlock(world: World, pos: BlockPos, state: IBlockState) {
        val entity = world.getTileEntity(pos) as? NodeBlockEntity
        entity?.onBreakBlock()
        super.breakBlock(world, pos, state)
    }

    override fun neighborChanged(state: IBlockState, world: World, pos: BlockPos, block: Block, fromPos: BlockPos) {
        if (!isRemote(world)) {
            val entity = world.getTileEntity(pos) as? NodeBlockEntity ?: return
            entity.onNeighborBlockChange()
        }
    }

    override fun damageDropped(state: IBlockState): Int {
        return getMetaFromState(state)
    }

    //client server
    override fun onBlockActivated(
        world: World, pos: BlockPos, state: IBlockState, entityPlayer: EntityPlayer,
        hand: EnumHand, side: EnumFacing, vx: Float, vy: Float, vz: Float
    ): Boolean {
        val entity = world.getTileEntity(pos) as? NodeBlockEntity ?: return false
        return entity.onBlockActivated(entityPlayer, fromFacing(side), vx, vy, vz)
    }

    override fun hasTileEntity(state: IBlockState): Boolean {
        return true
    }

    override fun createTileEntity(world: World, state: IBlockState): TileEntity {
        return tileEntityClass.getConstructor().newInstance() as TileEntity
    }

    init {
        setTranslationKey("NodeBlock")
        this.tileEntityClass = tileEntityClass
        useNeighborBrightness = true
        this.blockItemNbr = blockItemNbr
        setHardness(1.0f)
        setResistance(1.0f)
    }
}
