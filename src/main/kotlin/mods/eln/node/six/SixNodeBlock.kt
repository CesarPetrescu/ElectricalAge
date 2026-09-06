package mods.eln.node.six

import mods.eln.Eln
import mods.eln.misc.Direction
import mods.eln.misc.Direction.Companion.fromFacing
import mods.eln.misc.Utils.isCreative
import mods.eln.misc.Utils.isRemote
import mods.eln.misc.Utils.println
import mods.eln.misc.Utils.updateAllLightTypes
import mods.eln.node.NodeBase
import mods.eln.node.NodeBlock
import net.minecraft.world.InteractionHand
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction as EnumFacing
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import mods.eln.misc.getBlock
import mods.eln.misc.getBlockEntity
import mods.eln.misc.isBlockLoaded
import mods.eln.misc.setBlockToAir
import mods.eln.misc.getBlockState

/**
 * The block every six-node element lives in. 1.21: the per-face selection that used to come from
 * a hand-written collisionRayTrace comes from [getShape] (one thin slab per enabled face; vanilla
 * clips the look ray against it and reports the face), collision is a full cube only when the
 * node has a volume or a camouflage block, and light opacity follows the camouflage block.
 */
class SixNodeBlock : NodeBlock(nodeProperties().strength(0.3f, 1.0f), 0) {
    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = SixNodeEntity(pos, state)

    override fun getCloneItemStack(state: BlockState, target: HitResult, world: LevelReader, pos: BlockPos, player: Player): ItemStack {
        val entity = world.getBlockEntity(pos) as SixNodeEntity?
        if (entity != null && target is BlockHitResult) {
            val render = entity.elementRenderList[fromFacing(target.direction).int]
            if (render != null) {
                findClosestMatchingHotbarStack(player, render.sixNodeDescriptor)?.let { return it.copy() }
                return render.sixNodeDescriptor.newCreativeTabStack()
            }
        }
        return ItemStack.EMPTY
    }

    private fun findClosestMatchingHotbarStack(player: Player, descriptor: SixNodeDescriptor): ItemStack? {
        val inventory = player.inventory ?: return null
        val currentSlot = inventory.selected
        var bestStack: ItemStack? = null
        var bestDistance = Int.MAX_VALUE
        for (slot in 0 until 9) {
            val stack = inventory.getItem(slot).takeUnless { it.isEmpty } ?: continue
            if (!descriptor.checkSameItemStack(stack)) continue
            val distance = hotbarDistance(currentSlot, slot)
            if (distance < bestDistance) {
                bestDistance = distance
                bestStack = stack
            }
        }
        return bestStack
    }

    private fun hotbarDistance(a: Int, b: Int): Int {
        val direct = Math.abs(a - b)
        return Math.min(direct, 9 - direct)
    }


    private fun hasBody(world: BlockGetter, pos: BlockPos): Boolean =
        nodeHasCache(world, pos.x, pos.y, pos.z) || (world is Level && hasVolume(world, pos.x, pos.y, pos.z))

    override fun getCollisionShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        if (hasBody(world, pos)) Shapes.block() else Shapes.empty()

    /** The outline and ray-trace shape: a full cube for bodies, else one slab per populated face. */
    override fun getShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        if (hasBody(world, pos)) return Shapes.block()
        val entity = world.getBlockEntity(pos) as? SixNodeEntity ?: return Shapes.empty()
        var shape = Shapes.empty()
        for (direction in Direction.values()) {
            val enabled = if (world is Level && !world.isClientSide) {
                (entity.node as? SixNode)?.getSideEnable(direction) ?: false
            } else entity.getSyncronizedSideEnable(direction)
            if (enabled) shape = Shapes.or(shape, faceSlab(direction))
        }
        return shape
    }

    override fun getVisualShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = Shapes.empty()

    fun hasVolume(world: Level, x: Int, y: Int, z: Int): Boolean {
        val entity = getEntity(world, x, y, z) ?: return false
        return entity.hasVolume(world, x, y, z)
    }

    /**
     * 1.7.10's Block.getDamageValue(world, x, y, z) is gone; the pick-block and drop paths take
     * an BlockState instead. This stays as a plain helper because getPickBlock() above already
     * builds the right stack from the node's descriptor, which the block state cannot carry.
     */
    fun getDamageValue(world: Level, pos: BlockPos): Int {
        val entity = getEntity(world, pos.x, pos.y, pos.z)
        return entity?.getDamageValue(world, pos.x, pos.y, pos.z) ?: 0
    }

    fun getEntity(world: BlockGetter, x: Int, y: Int, z: Int): SixNodeEntity? {
        val tileEntity = world.getBlockEntity(x, y, z)
        if (tileEntity != null && tileEntity is SixNodeEntity) return tileEntity
        println("ASSERTSixNodeEntity getEntity() null")
        return null
    }


    /**
     * Six-nodes draw entirely from their TESR, including the camouflage block a cable can be
     * hidden inside. Phase 3 replaces this with an IExtendedBlockState carrying the camouflaged
     * state plus a delegating baked model.
     */
    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.INVISIBLE
    }

    /**
     * 1.8 moved per-block tinting out of Block and into BlockColors/IBlockColor, registered on
     * the client. Camouflaged six-nodes need the camouflage block's tint (grass, leaves); that
     * handler is registered in phase 3 alongside the camouflage model.
     */

    /*
	 * @Override public int getLightOpacity(Level world, int x, int y, int z) {
	 *
	 * return 255; }
	 */
    // No loot table: the block itself never drops (the elements drop through the node).

    override fun canBeReplaced(state: BlockState, context: BlockPlaceContext): Boolean {
        return false
    }

    override fun canSurvive(state: BlockState, world: LevelReader, pos: BlockPos): Boolean {
        /* This should probably call canPlaceBlockOnSide with each
		 * appropriate side to see if it can go somewhere.
		 * (cf. BlockLever, BlockTorch, etc)

		 * Currently, canPlaceBlockOnSide returns true and defers
		 * check to other code.  The rest of the sixnode code isn't
		 * expecting blind canPlaceBlockAt to work, so things that
		 * call it (e.g. Rannuncarpus) confuse it terribly and leak
		 * cables and nodepieces.

		 * So for now, make the Rannuncarpus et al ignore it.
		 */
        return false
    }

    override fun onBlockPlacedBy(world: Level, pos: BlockPos, front: Direction?, entityLiving: LivingEntity?, metadata: Int): Boolean {
        return true
    }

    /*
     * @Override public boolean onBlockActivated(Level world, int x, int y, int z, Player entityPlayer, int minecraftSide, float vx, float vy, float vz) { SixNodeEntity tileEntity = (SixNodeEntity) world.getBlockTileEntity(x, y, z);
     *
     * return tileEntity.onBlockActivated(entityPlayer, Direction.fromIntMinecraftSide(minecraftSide),vx,vy,vz); }
     */
    override fun onDestroyedByPlayer(state: BlockState, world: Level, pos: BlockPos, entityPlayer: Player, willHarvest: Boolean, fluid: FluidState): Boolean {
        if (world.isClientSide) return false
        val x = pos.x; val y = pos.y; val z = pos.z
        val tileEntity = world.getBlockEntity(pos) as SixNodeEntity
        val sixNode = tileEntity.node as SixNode? ?: return true
        if (sixNode.sixNodeCacheBlock !== Blocks.AIR) {
            if (isCreative((entityPlayer as ServerPlayer)) == false) {
                val stack = ItemStack(sixNode.sixNodeCacheBlock, 1)
                sixNode.dropItem(stack)
            }
            sixNode.sixNodeCacheBlock = Blocks.AIR
            updateAllLightTypes(world, x, y, z)
            sixNode.needPublish = true
            return false
        }
        val breakDirection = resolveBreakDirection(world, pos, entityPlayer, sixNode) ?: return false
        if (!sixNode.playerAskToBreakSubBlock(entityPlayer as ServerPlayer, breakDirection)) return false
        return if (sixNode.ifSideRemain) true else super.onDestroyedByPlayer(state, world, pos, entityPlayer, willHarvest, fluid)
    }

    override fun onRemove(state: BlockState, world: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!world.isClientSide && !state.`is`(newState.block)) {
            val x = pos.x; val y = pos.y; val z = pos.z
            val tileEntity = world.getBlockEntity(pos) as? SixNodeEntity
            val sixNode = tileEntity?.node as SixNode?
            if (sixNode != null) {
                for (direction in Direction.values()) {
                    if (sixNode.getSideEnable(direction)) {
                        println("SixNodeBlock.breakBlock deleting side=$direction at $x,$y,$z block=${state.block.javaClass.simpleName}")
                        sixNode.deleteSubBlock(null, direction)
                    }
                }
            }
        }
        super.onRemove(state, world, pos, newState, movedByPiston)
    }

    override fun neighborChanged(state: BlockState, world: Level, pos: BlockPos, b: Block, fromPos: BlockPos, movedByPiston: Boolean) {
        if (world.isClientSide) return
        val x = pos.x; val y = pos.y; val z = pos.z
        val tileEntity = world.getBlockEntity(pos) as SixNodeEntity
        val sixNode = tileEntity.node as SixNode? ?: return
        for (direction in Direction.values()) {
            if (sixNode.getSideEnable(direction)) {
                if (!getIfOtherBlockIsSolid(world, x, y, z, direction)) {
                    println("SixNodeBlock.onNeighborBlockChange deleting side=$direction at $x,$y,$z dueTo=${b.javaClass.simpleName}")
                    sixNode.deleteSubBlock(null, direction)
                }
            }
        }
        if (!sixNode.ifSideRemain) {
            world.removeBlock(pos, false)
        } else {
            super.neighborChanged(state, world, pos, b, fromPos, movedByPiston)
        }
    }

    /** Where the player's look ray hits this block's [getShape]; null when it misses. */
    fun collisionRayTrace(world: Level, pos: BlockPos, entityLiving: Player): BlockHitResult? {
        // Server player state can lag a few ticks behind under low TPS; add margin so raytrace still resolves a side.
        val distanceMax = 8.0
        val start = entityLiving.getEyePosition(0.5f)
        val look = entityLiving.getViewVector(0.5f)
        val end = start.add(look.x * distanceMax, look.y * distanceMax, look.z * distanceMax)
        val shape = getShape(world.getBlockState(pos), world, pos, CollisionContext.of(entityLiving))
        val hit = shape.clip(start, end, pos) ?: return null
        return if (hit.type == HitResult.Type.MISS) null else hit
    }

    /**
     * 1.7.10's ray trace answered with the block face an element sits on; 1.21's answers with the
     * face of the element's slab that was hit (the top of a floor element is UP). This maps a hit
     * back to the element: the enabled side whose slab holds the hit point, else the hit face.
     */
    fun elementSide(hitSide: Direction, vx: Float, vy: Float, vz: Float, enabled: (Direction) -> Boolean): Direction {
        val e = 1e-3f
        val candidates = listOf(
            Direction.XN to (vx <= 0.2f + e), Direction.XP to (vx >= 0.8f - e),
            Direction.YN to (vy <= 0.2f + e), Direction.YP to (vy >= 0.8f - e),
            Direction.ZN to (vz <= 0.2f + e), Direction.ZP to (vz >= 0.8f - e)
        )
        if (enabled(hitSide) && candidates.first { it.first == hitSide }.second) return hitSide
        for ((direction, inside) in candidates) if (inside && enabled(direction)) return direction
        return hitSide
    }

    override fun onBlockActivated(world: Level, pos: BlockPos, state: BlockState, entityPlayer: Player, hand: InteractionHand, side: EnumFacing, vx: Float, vy: Float, vz: Float): Boolean {
        val entity = world.getBlockEntity(pos) as? SixNodeEntity ?: return false
        val enabled: (Direction) -> Boolean = if (world.isClientSide) entity::getSyncronizedSideEnable
        else { d -> (entity.node as? SixNode)?.getSideEnable(d) ?: false }
        val elementSide = if (nodeHasCache(world, pos.x, pos.y, pos.z) || hasVolume(world, pos.x, pos.y, pos.z)) fromFacing(side)
        else elementSide(fromFacing(side), vx, vy, vz, enabled)
        return entity.onBlockActivated(entityPlayer, elementSide, vx, vy, vz)
    }

    private fun resolveBreakDirection(world: Level, pos: BlockPos, entityPlayer: Player, sixNode: SixNode): Direction? {
        val ray = collisionRayTrace(world, pos, entityPlayer)
        val rayDirection = ray?.let {
            val (vx, vy, vz) = NodeBlock.hitFractions(it, pos)
            elementSide(fromFacing(it.direction), vx, vy, vz) { d -> sixNode.getSideEnable(d) }
        }
        if (rayDirection != null && sixNode.getSideEnable(rayDirection)) {
            return rayDirection
        }

        // Fallback: pick the enabled face most directly facing the player's look vector.
        val look = entityPlayer.getViewVector(0.5f)
        var bestDirection: Direction? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (direction in Direction.values()) {
            if (!sixNode.getSideEnable(direction)) continue
            val score = faceFacingPlayerScore(direction, look)
            if (score > bestScore) {
                bestScore = score
                bestDirection = direction
            }
        }
        return bestDirection
    }

    private fun faceFacingPlayerScore(direction: Direction, look: Vec3): Double {
        val nx: Double
        val ny: Double
        val nz: Double
        when (direction) {
            Direction.XN -> {
                nx = -1.0
                ny = 0.0
                nz = 0.0
            }
            Direction.XP -> {
                nx = 1.0
                ny = 0.0
                nz = 0.0
            }
            Direction.YN -> {
                nx = 0.0
                ny = -1.0
                nz = 0.0
            }
            Direction.YP -> {
                nx = 0.0
                ny = 1.0
                nz = 0.0
            }
            Direction.ZN -> {
                nx = 0.0
                ny = 0.0
                nz = -1.0
            }
            Direction.ZP -> {
                nx = 0.0
                ny = 0.0
                nz = 1.0
            }
        }
        // The clicked face points against the player look direction.
        return -(look.x * nx + look.y * ny + look.z * nz)
    }

    fun getIfOtherBlockIsSolid(world: Level, x: Int, y: Int, z: Int, direction: Direction): Boolean {
        val vect = IntArray(3)
        vect[0] = x
        vect[1] = y
        vect[2] = z
        direction.applyTo(vect, 1)
        // During chunk load, neighboring chunks can still be unavailable. Treat that as
        // "unknown" instead of "air" so attached six-node parts do not self-delete
        // before their support block has actually loaded.
        if (!world.isBlockLoaded(vect[0], vect[1], vect[2])) return true
        val other = BlockPos(vect[0], vect[1], vect[2])
        val state = world.getBlockState(other)
        if (state.isAir) return false
        return state.isSolidRender(world, other)
    }

    fun nodeHasCache(world: BlockGetter, x: Int, y: Int, z: Int): Boolean {
        if (isRemote(world)) {
            val tileEntity = world.getBlockEntity(x, y, z)
            if (tileEntity != null && tileEntity is SixNodeEntity) return tileEntity.sixNodeCacheBlock !== Blocks.AIR else println("ASSERT B public boolean nodeHasCache(Level world, int x, int y, int z) ")
        } else {
            val tileEntity = world.getBlockEntity(x, y, z) as SixNodeEntity
            val sixNode = tileEntity.node as SixNode?
            if (sixNode != null) return sixNode.sixNodeCacheBlock !== Blocks.AIR else println("ASSERT A public boolean nodeHasCache(Level world, int x, int y, int z) ")
        }
        return false
    }

    override fun getLightBlock(state: BlockState, w: BlockGetter, pos: BlockPos): Int {
        val sne = w.getBlockEntity(pos) as? SixNodeEntity ?: return 0
        val b = sne.sixNodeCacheBlock
        return if (b === Blocks.AIR) 0 else try {
            b.defaultBlockState().getLightBlock(w, pos)
        } catch (e2: Exception) {
            15
        }
    }

    override fun propagatesSkylightDown(state: BlockState, world: BlockGetter, pos: BlockPos): Boolean {
        return !nodeHasCache(world, pos.x, pos.y, pos.z)
    }

    val nodeUuid: String
        get() = NODE_UUID

    companion object {
        const val NODE_UUID = "s"

        fun isIn(value: Double, min: Double, max: Double): Boolean {
            return if (value >= min && value <= max) true else false
        }

        /** The outline slab of one face: 0.2 deep, inset 0.02 like the 1.7.10 selection box. */
        private val SLABS: Map<Direction, VoxelShape> = mapOf(
            Direction.XN to Shapes.box(0.02, 0.0, 0.0, 0.2, 1.0, 1.0),
            Direction.XP to Shapes.box(0.8, 0.0, 0.0, 0.98, 1.0, 1.0),
            Direction.YN to Shapes.box(0.0, 0.02, 0.0, 1.0, 0.2, 1.0),
            Direction.YP to Shapes.box(0.0, 0.8, 0.0, 1.0, 0.98, 1.0),
            Direction.ZN to Shapes.box(0.0, 0.0, 0.02, 1.0, 1.0, 0.2),
            Direction.ZP to Shapes.box(0.0, 0.0, 0.8, 1.0, 1.0, 0.98),
        )

        fun faceSlab(direction: Direction): VoxelShape = SLABS[direction] ?: Shapes.empty()
    }
}
