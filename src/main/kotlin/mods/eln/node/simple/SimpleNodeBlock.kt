package mods.eln.node.simple

import mods.eln.misc.Direction
import mods.eln.misc.Direction.Companion.fromFacing
import mods.eln.misc.Direction.Companion.fromIntMinecraftSide
import mods.eln.misc.Utils.entityLivingViewDirection
import mods.eln.misc.Utils.isRemote
import net.minecraft.block.Block
import net.minecraft.util.math.BlockPos
import net.minecraft.util.EnumHand
import net.minecraft.util.EnumFacing
import net.minecraft.block.state.IBlockState
import net.minecraft.block.BlockContainer
import net.minecraft.block.material.Material
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.world.World
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

    fun getFrontForPlacement(e: EntityLivingBase?): Direction {
        return entityLivingViewDirection(e!!).inverse
    }

    abstract fun newNode(): SimpleNode?

    fun getNode(world: World, x: Int, y: Int, z: Int): SimpleNode? {
        val entity = world.getTileEntity(x, y, z) as SimpleNodeEntity?
        return entity?.node
    }

    fun getEntity(world: World, x: Int, y: Int, z: Int): SimpleNodeEntity {
        return world.getTileEntity(x, y, z) as SimpleNodeEntity
    }

    override fun removedByPlayer(state: IBlockState, world: World, pos: BlockPos, entityPlayer: EntityPlayer, willHarvest: Boolean): Boolean {
        if (!world.isRemote) {
            val node = getNode(world, pos.x, pos.y, pos.z)
            if (node != null) {
                node.removedByPlayer = entityPlayer as EntityPlayerMP
            }
        }
        return super.removedByPlayer(state, world, pos, entityPlayer, willHarvest)
    }

    // server
    override fun onBlockAdded(world: World, pos: BlockPos, state: IBlockState) {
        if (!world.isRemote) {
            val entity = world.getTileEntity(pos) as SimpleNodeEntity
            entity.onBlockAdded()
        }
    }

    // server
    override fun breakBlock(world: World, pos: BlockPos, state: IBlockState) {
        val entity = world.getTileEntity(pos) as SimpleNodeEntity
        entity.onBreakBlock()
        super.breakBlock(world, pos, state)
    }

    override fun neighborChanged(state: IBlockState, world: World, pos: BlockPos, b: Block, fromPos: BlockPos) {
        if (!isRemote(world)) {
            val entity = world.getTileEntity(pos) as SimpleNodeEntity
            entity.onNeighborBlockChange()
        }
    }

    // client server
    override fun onBlockActivated(
        world: World, pos: BlockPos, state: IBlockState, entityPlayer: EntityPlayer,
        hand: EnumHand, side: EnumFacing, vx: Float, vy: Float, vz: Float
    ): Boolean {
        val entity = world.getTileEntity(pos) as SimpleNodeEntity
        return entity.onBlockActivated(entityPlayer, fromFacing(side), vx, vy, vz)
    }
}
