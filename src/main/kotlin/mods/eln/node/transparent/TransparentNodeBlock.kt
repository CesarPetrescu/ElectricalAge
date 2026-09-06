package mods.eln.node.transparent

import mods.eln.misc.IMetaBlock
import mods.eln.node.NodeBase
import mods.eln.node.NodeBlock
import mods.eln.node.NodeBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import mods.eln.misc.getBlockEntity

/**
 * The block every transparent-node element lives in. The 1.7.10 metadata chose the block entity
 * subclass ([EntityMetaTag]) and the light opacity; it is the [META] state property now.
 */
class TransparentNodeBlock : NodeBlock(nodeProperties().lightLevel { 0 }, 0), IMetaBlock {

    init {
        registerDefaultState(stateDefinition.any().setValue(META, EntityMetaTag.Basic.meta))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState>) {
        builder.add(META)
    }

    override fun stateForMeta(meta: Int): BlockState = defaultBlockState().setValue(META, meta and 15)

    override fun metaOfState(state: BlockState): Int = state.getValue(META)

    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.INVISIBLE
    }

    override fun onDestroyedByPlayer(state: BlockState, world: Level, pos: BlockPos, entityPlayer: Player, willHarvest: Boolean, fluid: FluidState): Boolean {
        if (!world.isClientSide) {
            val entity = world.getBlockEntity(pos) as? NodeBlockEntity
            if (entity != null) {
                val nodeBase: NodeBase? = entity.node
                if (nodeBase is TransparentNode) {
                    nodeBase.removedByPlayer = entityPlayer as ServerPlayer
                }
            }
        }
        return super.onDestroyedByPlayer(state, world, pos, entityPlayer, willHarvest, fluid)
    }

    /** See the note on SixNodeBlock.getDamageValue: no longer an override. */
    fun getDamageValue(world: Level, pos: BlockPos): Int {
        val tile = world.getBlockEntity(pos)
        return if (tile is TransparentNodeEntity) tile.getDamageValue(world, pos.x, pos.y, pos.z) else 0
    }

    override fun getLightBlock(state: BlockState, world: BlockGetter, pos: BlockPos): Int {
        // 1.7.10 encoded the opacity in the metadata's top bits; 0..15 in state terms.
        return ((state.getValue(META) and 3) shl 6) shr 4
    }

    // No loot table: the block itself never drops (the element drops through the node).

    override fun getCollisionShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        val tileEntity = world.getBlockEntity(pos)
        if (tileEntity !is TransparentNodeEntity) return Shapes.block()
        val boxes = ArrayList<AABB?>()
        val query = AABB(pos).inflate(4.0)
        tileEntity.addCollisionBoxesToList(query, boxes, null)
        var shape = Shapes.empty()
        for (bb in boxes) {
            if (bb == null) continue
            shape = Shapes.or(shape, Shapes.create(bb.move(-pos.x.toDouble(), -pos.y.toDouble(), -pos.z.toDouble())))
        }
        return shape
    }

    override fun getShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        val collision = getCollisionShape(state, world, pos, context)
        return if (collision.isEmpty) Shapes.block() else collision
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        val meta = state.getValue(META)
        for (tag in EntityMetaTag.values()) {
            if (tag.meta == meta) return tag.create(pos, state)
        }
        // Sadly, this will happen a lot with pre-metatag worlds.
        println("Unknown block meta-tag: $meta")
        return EntityMetaTag.Basic.create(pos, state)
    }

    val nodeUuid: String
        get() = NODE_UUID

    companion object {
        const val NODE_UUID = "t"

        @JvmField
        val META: IntegerProperty = IntegerProperty.create("meta", 0, 15)
    }
}
