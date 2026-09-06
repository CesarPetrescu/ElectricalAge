package mods.eln.node.simple

import mods.eln.misc.Direction
import mods.eln.misc.Direction.Companion.fromFacing
import mods.eln.misc.Utils.entityLivingViewDirection
import mods.eln.misc.Utils.isRemote
import mods.eln.node.NodeBlock
import net.minecraft.world.level.block.Block
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.core.Direction as EnumFacing
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.BlockHitResult
import mods.eln.misc.getBlockEntity

/**
 * The block of a single-purpose node (the energy converter, the dev conduit). 1.7.10's
 * `BlockContainer` is [EntityBlock]; the tile is looked up the same way.
 */
abstract class SimpleNodeBlock protected constructor(properties: Properties) : Block(properties), EntityBlock {
    var descriptorKey: String? = null

    /** 1.7.10's `setBlockName(name)`: the block and its item read as `tile.<name>.name` in the lang files. */
    var translationName: String? = null

    override fun getDescriptionId(): String = translationName?.let { "tile.$it.name" } ?: super.getDescriptionId()
    fun setDescriptorKey(descriptorKey: String?): SimpleNodeBlock {
        this.descriptorKey = descriptorKey
        return this
    }

    fun setDescriptor(descriptor: DescriptorBase): SimpleNodeBlock {
        descriptorKey = descriptor.descriptorKey
        return this
    }

    fun getFrontForPlacement(e: LivingEntity?): Direction {
        return entityLivingViewDirection(e!!).inverse
    }

    abstract fun newNode(): SimpleNode?

    abstract override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? =
        BlockEntityTicker { _, _, _, entity -> (entity as? SimpleNodeEntity)?.update() }

    fun getNode(world: Level, x: Int, y: Int, z: Int): SimpleNode? {
        val entity = world.getBlockEntity(x, y, z) as SimpleNodeEntity?
        return entity?.node
    }

    fun getEntity(world: Level, x: Int, y: Int, z: Int): SimpleNodeEntity {
        return world.getBlockEntity(x, y, z) as SimpleNodeEntity
    }

    override fun onDestroyedByPlayer(state: BlockState, world: Level, pos: BlockPos, entityPlayer: Player, willHarvest: Boolean, fluid: FluidState): Boolean {
        if (!world.isClientSide) {
            val node = getNode(world, pos.x, pos.y, pos.z)
            if (node != null) {
                node.removedByPlayer = entityPlayer as ServerPlayer
            }
        }
        return super.onDestroyedByPlayer(state, world, pos, entityPlayer, willHarvest, fluid)
    }

    // server
    override fun onPlace(state: BlockState, world: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
        if (!world.isClientSide) {
            val entity = world.getBlockEntity(pos) as? SimpleNodeEntity ?: return
            entity.onBlockAdded()
        }
    }

    // server
    override fun onRemove(state: BlockState, world: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!state.`is`(newState.block)) {
            (world.getBlockEntity(pos) as? SimpleNodeEntity)?.onBreakBlock()
        }
        super.onRemove(state, world, pos, newState, movedByPiston)
    }

    override fun neighborChanged(state: BlockState, world: Level, pos: BlockPos, b: Block, fromPos: BlockPos, movedByPiston: Boolean) {
        if (!isRemote(world)) {
            val entity = world.getBlockEntity(pos) as? SimpleNodeEntity ?: return
            entity.onNeighborBlockChange()
        }
    }

    // client server
    open fun onBlockActivated(
        world: Level, pos: BlockPos, state: BlockState, entityPlayer: Player,
        hand: InteractionHand, side: EnumFacing, vx: Float, vy: Float, vz: Float
    ): Boolean {
        val entity = world.getBlockEntity(pos) as? SimpleNodeEntity ?: return false
        return entity.onBlockActivated(entityPlayer, fromFacing(side), vx, vy, vz)
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
}
