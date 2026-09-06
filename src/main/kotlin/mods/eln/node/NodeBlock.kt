package mods.eln.node

import mods.eln.misc.Direction
import mods.eln.misc.Direction.Companion.fromFacing
import mods.eln.misc.Utils.isRemote
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction as EnumFacing
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
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

/**
 * The block behind every node. 1.21: block entities come from [EntityBlock] (with a ticker instead
 * of ITickable), the redstone, light and interaction hooks changed names, and the interaction
 * hook is split by whether an item is held; both halves feed the node's single
 * `onBlockActivated`, as 1.7.10 did.
 */
abstract class NodeBlock(properties: Properties, blockItemNbr: Int) : Block(properties), EntityBlock {

    var blockItemNbr: Int = blockItemNbr

    abstract override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? =
        BlockEntityTicker { _, _, _, entity -> (entity as? NodeBlockEntity)?.update() }

    override fun getSignal(blockState: BlockState, world: BlockGetter, pos: BlockPos, side: EnumFacing): Int {
        val entity = world.getBlockEntity(pos) as? NodeBlockEntity ?: return 0
        return entity.isProvidingWeakPower(fromFacing(side))
    }

    /** Without this the world never asks [getSignal]. */
    override fun isSignalSource(state: BlockState): Boolean = true

    override fun canConnectRedstone(state: BlockState, world: BlockGetter, pos: BlockPos, side: EnumFacing?): Boolean {
        val entity = world.getBlockEntity(pos) as? NodeBlockEntity ?: return false
        return entity.canConnectRedstone(Direction.XN)
    }

    /**
     * Every node draws itself from a BlockEntityRenderer, so the block itself contributes no
     * baked quads.
     */
    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.INVISIBLE
    }

    override fun getLightEmission(state: BlockState, world: BlockGetter, pos: BlockPos): Int {
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
    override fun onPlace(state: BlockState, world: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
        if (!world.isClientSide) {
            val entity = world.getBlockEntity(pos) as? NodeBlockEntity ?: return
            entity.onBlockAdded()
        }
    }

    //server
    override fun onRemove(state: BlockState, world: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!state.`is`(newState.block)) {
            val entity = world.getBlockEntity(pos) as? NodeBlockEntity
            entity?.onBreakBlock()
        }
        super.onRemove(state, world, pos, newState, movedByPiston)
    }

    override fun neighborChanged(state: BlockState, world: Level, pos: BlockPos, block: Block, fromPos: BlockPos, movedByPiston: Boolean) {
        if (!isRemote(world)) {
            val entity = world.getBlockEntity(pos) as? NodeBlockEntity ?: return
            entity.onNeighborBlockChange()
        }
    }

    /** 1.7.10's single onBlockActivated: the node decides, whatever is in the hand. */
    open fun onBlockActivated(world: Level, pos: BlockPos, state: BlockState, entityPlayer: Player, hand: InteractionHand, side: EnumFacing, vx: Float, vy: Float, vz: Float): Boolean {
        val entity = world.getBlockEntity(pos) as? NodeBlockEntity ?: return false
        return entity.onBlockActivated(entityPlayer, fromFacing(side), vx, vy, vz)
    }

    override fun useItemOn(stack: ItemStack, state: BlockState, world: Level, pos: BlockPos, player: Player, hand: InteractionHand, hit: BlockHitResult): ItemInteractionResult {
        val (vx, vy, vz) = hitFractions(hit, pos)
        return if (onBlockActivated(world, pos, state, player, hand, hit.direction, vx, vy, vz)) ItemInteractionResult.sidedSuccess(world.isClientSide)
        else ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
    }

    override fun useWithoutItem(state: BlockState, world: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        val (vx, vy, vz) = hitFractions(hit, pos)
        return if (onBlockActivated(world, pos, state, player, InteractionHand.MAIN_HAND, hit.direction, vx, vy, vz)) InteractionResult.sidedSuccess(world.isClientSide)
        else InteractionResult.PASS
    }

    companion object {
        /** The 1.7.10 hit fractions (0..1 inside the block) from a hit result. */
        @JvmStatic
        fun hitFractions(hit: BlockHitResult, pos: BlockPos): Triple<Float, Float, Float> {
            val l = hit.location
            return Triple((l.x - pos.x).toFloat(), (l.y - pos.y).toFloat(), (l.z - pos.z).toFloat())
        }

        /** 1.7.10's `new Block(material).setHardness(1).setResistance(1)` with `useNeighborBrightness`. */
        @JvmStatic
        fun nodeProperties(): Properties = Properties.of().strength(1.0f, 1.0f).noOcclusion().dynamicShape()
    }
}
