package mods.eln.node

import mods.eln.misc.Direction
import mods.eln.misc.Direction.Companion.fromFacing
import mods.eln.misc.Utils.isRemote
import net.minecraft.world.level.block.Block
import net.minecraft.block.material.Material
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.RenderShape
import net.minecraft.core.Direction as EnumFacing
import net.minecraft.world.InteractionHand
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import mods.eln.misc.getTileEntity

abstract class NodeBlock(material: Material?, tileEntityClass: Class<*>, blockItemNbr: Int) : Block(material) {

    var blockItemNbr: Int
    var tileEntityClass: Class<*>

    override fun getBlockHardness(blockState: BlockState, world: Level, pos: BlockPos): Float {
        return 1.0f
    }

    override fun getWeakPower(blockState: BlockState, world: BlockGetter, pos: BlockPos, side: EnumFacing): Int {
        val entity = world.getBlockEntity(pos) as? NodeBlockEntity ?: return 0
        return entity.isProvidingWeakPower(fromFacing(side))
    }

    /**
     * 1.8 split "can this block emit redstone at all" out of the per-side power query; without
     * it the world never asks [getWeakPower].
     */
    override fun canProvidePower(state: BlockState): Boolean = true

    override fun canConnectRedstone(state: BlockState, world: BlockGetter, pos: BlockPos, side: EnumFacing?): Boolean {
        val entity = world.getBlockEntity(pos) as? NodeBlockEntity ?: return false
        return entity.canConnectRedstone(Direction.XN)
    }

    override fun isOpaqueCube(state: BlockState): Boolean {
        return true
    }

    /**
     * Every node draws itself from a TileEntitySpecialRenderer, so the block itself contributes
     * no baked quads. This replaces both `renderAsNormalBlock` and the old integer render type.
     */
    override fun getRenderType(state: BlockState): RenderShape {
        return RenderShape.INVISIBLE
    }

    override fun getLightValue(state: BlockState, world: BlockGetter, pos: BlockPos): Int {
        val entity = world.getBlockEntity(pos)
        if (entity !is NodeBlockEntity) return 0
        return entity.lightValue
    }

    //client server
    open fun onBlockPlacedBy(world: Level, pos: BlockPos, front: Direction?, entityLiving: LivingEntity?, metadata: Int): Boolean {
        // If you're getting a mysterious NPE here, it's probably because your ghost group overrides the base node. You're welcome.
        val tileEntity = world.getBlockEntity(pos) as NodeBlockEntity
        tileEntity.onBlockPlacedBy(front, entityLiving, metadata)
        return true
    }

    //server
    override fun onBlockAdded(world: Level, pos: BlockPos, state: BlockState) {
        if (!world.isClientSide) {
            val entity = world.getBlockEntity(pos) as? NodeBlockEntity ?: return
            entity.onBlockAdded()
        }
    }

    //server
    override fun breakBlock(world: Level, pos: BlockPos, state: BlockState) {
        val entity = world.getBlockEntity(pos) as? NodeBlockEntity
        entity?.onBreakBlock()
        super.breakBlock(world, pos, state)
    }

    override fun neighborChanged(state: BlockState, world: Level, pos: BlockPos, block: Block, fromPos: BlockPos) {
        if (!isRemote(world)) {
            val entity = world.getBlockEntity(pos) as? NodeBlockEntity ?: return
            entity.onNeighborBlockChange()
        }
    }

    override fun damageDropped(state: BlockState): Int {
        return getMetaFromState(state)
    }

    //client server
    override fun onBlockActivated(
        world: Level, pos: BlockPos, state: BlockState, entityPlayer: Player,
        hand: InteractionHand, side: EnumFacing, vx: Float, vy: Float, vz: Float
    ): Boolean {
        val entity = world.getBlockEntity(pos) as? NodeBlockEntity ?: return false
        return entity.onBlockActivated(entityPlayer, fromFacing(side), vx, vy, vz)
    }

    override fun hasTileEntity(state: BlockState): Boolean {
        return true
    }

    override fun createTileEntity(world: Level, state: BlockState): BlockEntity {
        return tileEntityClass.getConstructor().newInstance() as BlockEntity
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
