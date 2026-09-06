package mods.eln.node.simple

import mods.eln.misc.Direction
import mods.eln.misc.Direction.Companion.fromFacing
import mods.eln.misc.Direction.Companion.fromIntMinecraftSide
import mods.eln.misc.Utils.entityLivingViewDirection
import mods.eln.misc.Utils.isRemote
import net.minecraft.world.level.block.Block
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.core.Direction as EnumFacing
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.block.BlockContainer
import net.minecraft.block.material.Material
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import mods.eln.misc.getTileEntity

abstract class SimpleNodeBlock protected constructor(material: Material?) : BlockContainer(material) {
    var descriptorKey: String? = null
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

    fun getNode(world: Level, x: Int, y: Int, z: Int): SimpleNode? {
        val entity = world.getBlockEntity(x, y, z) as SimpleNodeEntity?
        return entity?.node
    }

    fun getEntity(world: Level, x: Int, y: Int, z: Int): SimpleNodeEntity {
        return world.getBlockEntity(x, y, z) as SimpleNodeEntity
    }

    override fun removedByPlayer(state: BlockState, world: Level, pos: BlockPos, entityPlayer: Player, willHarvest: Boolean): Boolean {
        if (!world.isClientSide) {
            val node = getNode(world, pos.x, pos.y, pos.z)
            if (node != null) {
                node.removedByPlayer = entityPlayer as ServerPlayer
            }
        }
        return super.removedByPlayer(state, world, pos, entityPlayer, willHarvest)
    }

    // server
    override fun onBlockAdded(world: Level, pos: BlockPos, state: BlockState) {
        if (!world.isClientSide) {
            val entity = world.getBlockEntity(pos) as SimpleNodeEntity
            entity.onBlockAdded()
        }
    }

    // server
    override fun breakBlock(world: Level, pos: BlockPos, state: BlockState) {
        val entity = world.getBlockEntity(pos) as SimpleNodeEntity
        entity.onBreakBlock()
        super.breakBlock(world, pos, state)
    }

    override fun neighborChanged(state: BlockState, world: Level, pos: BlockPos, b: Block, fromPos: BlockPos) {
        if (!isRemote(world)) {
            val entity = world.getBlockEntity(pos) as SimpleNodeEntity
            entity.onNeighborBlockChange()
        }
    }

    // client server
    override fun onBlockActivated(
        world: Level, pos: BlockPos, state: BlockState, entityPlayer: Player,
        hand: InteractionHand, side: EnumFacing, vx: Float, vy: Float, vz: Float
    ): Boolean {
        val entity = world.getBlockEntity(pos) as SimpleNodeEntity
        return entity.onBlockActivated(entityPlayer, fromFacing(side), vx, vy, vz)
    }
}
