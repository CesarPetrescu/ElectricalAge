package mods.eln.node.six

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import mods.eln.Eln
import mods.eln.misc.Direction
import mods.eln.misc.Direction.Companion.fromFacing
import mods.eln.misc.Direction.Companion.fromIntMinecraftSide
import mods.eln.misc.Utils.generateHeightMap
import mods.eln.misc.Utils.isCreative
import mods.eln.misc.Utils.isRemote
import mods.eln.misc.Utils.println
import mods.eln.misc.Utils.updateAllLightTypes
import mods.eln.misc.Utils.updateSkylight
import mods.eln.node.NodeBase
import mods.eln.node.NodeBlock
import net.minecraft.world.level.block.Block
import net.minecraft.block.material.Material
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.client.Minecraft
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.RenderShape
import net.minecraft.core.Direction as EnumFacing
import net.minecraft.core.NonNullList
import net.minecraft.world.phys.AABB
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import java.util.*
import mods.eln.misc.getBlock
import mods.eln.misc.getTileEntity
import mods.eln.misc.isBlockLoaded
import mods.eln.misc.setBlockToAir
import mods.eln.misc.xCoord
import mods.eln.misc.yCoord
import mods.eln.misc.zCoord
import mods.eln.misc.getBlockState
import mods.eln.misc.isReplaceable

class SixNodeBlock  // public static ArrayList<Integer> repertoriedItemStackId = new ArrayList<Integer>();
// private IIcon icon;
(material: Material?, tileEntityClass: Class<*>?) : NodeBlock(material, tileEntityClass!!, 0) {
    override fun getPickBlock(state: BlockState, target: HitResult, world: Level, pos: BlockPos, player: Player): ItemStack {
        val entity = world.getBlockEntity(pos) as SixNodeEntity?
        if (entity != null) {
            val render = entity.elementRenderList[fromFacing(target.sideHit).int]
            if (render != null) {
                findClosestMatchingHotbarStack(player, render.sixNodeDescriptor)?.let { return it.copy() }
                return render.sixNodeDescriptor.newCreativeTabStack()
            }
        }
        return super.getPickBlock(state, target, world, pos, player)
    }

    private fun findClosestMatchingHotbarStack(player: Player, descriptor: SixNodeDescriptor): ItemStack? {
        val inventory = player.inventory ?: return null
        val currentSlot = inventory.currentItem
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


    override fun getCollisionBoundingBox(state: BlockState, world: BlockGetter, pos: BlockPos): AABB? {
        val hasBody = nodeHasCache(world, pos.x, pos.y, pos.z) ||
            (world is Level && hasVolume(world, pos.x, pos.y, pos.z))
        return if (hasBody) super.getCollisionBoundingBox(state, world, pos) else NULL_AABB
    }

    fun hasVolume(world: Level, x: Int, y: Int, z: Int): Boolean {
        val entity = getEntity(world, x, y, z) ?: return false
        return entity.hasVolume(world, x, y, z)
    }

    override fun getBlockHardness(blockState: BlockState, world: Level, pos: BlockPos): Float {
        return 0.3f
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

    // @OnlyIn(Dist.CLIENT)
    override fun getSubBlocks(tab: CreativeModeTab, subItems: NonNullList<ItemStack>) {
        Eln.sixNodeItem.getSubItems(tab, subItems)
    }

    override fun isOpaqueCube(state: BlockState): Boolean {
        return false
    }

    override fun isFullCube(state: BlockState): Boolean {
        return false
    }

    /**
     * Six-nodes draw entirely from their TESR, including the camouflage block a cable can be
     * hidden inside. Phase 3 replaces this with an IExtendedBlockState carrying the camouflaged
     * state plus a delegating baked model.
     */
    override fun getRenderType(state: BlockState): RenderShape {
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
    override fun getItemDropped(state: BlockState, rand: Random, fortune: Int): Item? {
        return null
    }

    override fun quantityDropped(par1Random: Random): Int {
        return 0
    }


    override fun isReplaceable(world: BlockGetter, pos: BlockPos): Boolean {
        return false
    }

    override fun canPlaceBlockOnSide(world: Level, pos: BlockPos, side: EnumFacing): Boolean {
        /* see canPlaceBlockAt; it needs changing if this method is fixed */
        return true /*
					 * if(par1World.isClientSide) return true; SixNodeEntity tileEntity = (SixNodeEntity) par1World.getBlockTileEntity(par2, par3, par4); if(tileEntity == null || (tileEntity instanceof SixNodeEntity) == false) return true; Direction direction = Direction.fromIntMinecraftSide(par5); SixNode node = (SixNode) tileEntity.getNode(); if(node == null) return true; if(node.getSideEnable(direction))return false;
					 *
					 * return true;
					 */
    }

    override fun canPlaceBlockAt(world: Level, pos: BlockPos): Boolean {
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
    override fun removedByPlayer(state: BlockState, world: Level, pos: BlockPos, entityPlayer: Player, willHarvest: Boolean): Boolean {
        if (world.isClientSide) return false
        val x = pos.x; val y = pos.y; val z = pos.z
        val tileEntity = world.getBlockEntity(pos) as SixNodeEntity
        val sixNode = tileEntity.node as SixNode? ?: return true
        if (sixNode.sixNodeCacheBlock !== Blocks.AIR) {
            if (isCreative((entityPlayer as ServerPlayer)) == false) {
                val stack = ItemStack(sixNode.sixNodeCacheBlock, 1, sixNode.sixNodeCacheBlockMeta.toInt())
                sixNode.dropItem(stack)
            }
            sixNode.sixNodeCacheBlock = Blocks.AIR
            val chunk = world.getChunk(pos)
            generateHeightMap(chunk)
            updateSkylight(chunk)
            chunk.generateSkylightMap()
            updateAllLightTypes(world, x, y, z)
            sixNode.needPublish = true
            return false
        }
        val breakDirection = resolveBreakDirection(world, pos, entityPlayer, sixNode) ?: return false
        if (!sixNode.playerAskToBreakSubBlock(entityPlayer as ServerPlayer, breakDirection)) return false
        return if (sixNode.ifSideRemain) true else super.removedByPlayer(state, world, pos, entityPlayer, willHarvest)
    }

    override fun breakBlock(world: Level, pos: BlockPos, state: BlockState) {
        if (!world.isClientSide) {
            val x = pos.x; val y = pos.y; val z = pos.z
            val tileEntity = world.getBlockEntity(pos) as SixNodeEntity
            val sixNode = tileEntity.node as SixNode? ?: return
            for (direction in Direction.values()) {
                if (sixNode.getSideEnable(direction)) {
                    println("SixNodeBlock.breakBlock deleting side=$direction at $x,$y,$z block=${state.block.javaClass.simpleName}")
                    sixNode.deleteSubBlock(null, direction)
                }
            }
        }
        super.breakBlock(world, pos, state)
    }

    override fun neighborChanged(state: BlockState, world: Level, pos: BlockPos, b: Block, fromPos: BlockPos) {
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
            world.setBlockToAir(pos)
        } else {
            super.neighborChanged(state, world, pos, b, fromPos)
        }
    }

    var w = 0.0
    var booltemp = BooleanArray(6)
    override fun collisionRayTrace(blockState: BlockState, world: Level, pos: BlockPos, start: Vec3, end: Vec3): HitResult? {
        val x = pos.x; val y = pos.y; val z = pos.z
        if (nodeHasCache(world, x, y, z)) return super.collisionRayTrace(blockState, world, pos, start, end)
        val tileEntity = world.getBlockEntity(pos) as SixNodeEntity? ?: return null
        if (world.isClientSide) {
            booltemp[0] = tileEntity.getSyncronizedSideEnable(Direction.XN)
            booltemp[1] = tileEntity.getSyncronizedSideEnable(Direction.XP)
            booltemp[2] = tileEntity.getSyncronizedSideEnable(Direction.YN)
            booltemp[3] = tileEntity.getSyncronizedSideEnable(Direction.YP)
            booltemp[4] = tileEntity.getSyncronizedSideEnable(Direction.ZN)
            booltemp[5] = tileEntity.getSyncronizedSideEnable(Direction.ZP)
            val entity = getEntity(world, x, y, z)
            if (entity != null) {
                val element = entity.elementRenderList[Direction.YN.int]
                // setBlockBounds(0, 0, 0, 1, 1, 1);
                if (element != null && element.sixNodeDescriptor.hasVolume()) {
                    return HitResult(Vec3(0.5, 0.5, 0.5), Direction.YN.toFacing(), pos)
                }
            }
        } else {
            val sixNode = tileEntity.node as SixNode? ?: return null
            booltemp[0] = sixNode.getSideEnable(Direction.XN)
            booltemp[1] = sixNode.getSideEnable(Direction.XP)
            booltemp[2] = sixNode.getSideEnable(Direction.YN)
            booltemp[3] = sixNode.getSideEnable(Direction.YP)
            booltemp[4] = sixNode.getSideEnable(Direction.ZN)
            booltemp[5] = sixNode.getSideEnable(Direction.ZP)
            val entity = getEntity(world, x, y, z)
            if (entity != null) {
                val node: NodeBase? = entity.node
                if (node != null && node is SixNode) {
                    val element = node.sideElementList[Direction.YN.int]
                    if (element != null && element.sixNodeElementDescriptor.hasVolume()) return HitResult(Vec3(0.5, 0.5, 0.5), Direction.YN.toFacing(), pos)
                }
            }
        }
        // XN
        if (isIn(x.toDouble(), end.xCoord, start.xCoord) && booltemp[0]) {
            val hitX: Double
            val hitY: Double
            val hitZ: Double
            val ratio: Double
            ratio = (x - start.xCoord) / (end.xCoord - start.xCoord)
            if (ratio <= 1.1) {
                hitX = start.xCoord + ratio * (end.xCoord - start.xCoord)
                hitY = start.yCoord + ratio * (end.yCoord - start.yCoord)
                hitZ = start.zCoord + ratio * (end.zCoord - start.zCoord)
                if (isIn(hitY, y + w, y + 1 - w) && isIn(hitZ, z + w, z + 1 - w)) return HitResult(Vec3(hitX, hitY, hitZ), Direction.XN.toFacing(), pos)
            }
        }
        // XP
        if (isIn((x + 1).toDouble(), start.xCoord, end.xCoord) && booltemp[1]) {
            val hitX: Double
            val hitY: Double
            val hitZ: Double
            val ratio: Double
            ratio = (x + 1 - start.xCoord) / (end.xCoord - start.xCoord)
            if (ratio <= 1.1) {
                hitX = start.xCoord + ratio * (end.xCoord - start.xCoord)
                hitY = start.yCoord + ratio * (end.yCoord - start.yCoord)
                hitZ = start.zCoord + ratio * (end.zCoord - start.zCoord)
                if (isIn(hitY, y + w, y + 1 - w) && isIn(hitZ, z + w, z + 1 - w)) return HitResult(Vec3(hitX, hitY, hitZ), Direction.XP.toFacing(), pos)
            }
        }
        // YN
        if (isIn(y.toDouble(), end.yCoord, start.yCoord) && booltemp[2]) {
            val hitX: Double
            val hitY: Double
            val hitZ: Double
            val ratio: Double
            ratio = (y - start.yCoord) / (end.yCoord - start.yCoord)
            if (ratio <= 1.1) {
                hitX = start.xCoord + ratio * (end.xCoord - start.xCoord)
                hitY = start.yCoord + ratio * (end.yCoord - start.yCoord)
                hitZ = start.zCoord + ratio * (end.zCoord - start.zCoord)
                if (isIn(hitX, x + w, x + 1 - w) && isIn(hitZ, z + w, z + 1 - w)) return HitResult(Vec3(hitX, hitY, hitZ), Direction.YN.toFacing(), pos)
            }
        }
        // YP
        if (isIn((y + 1).toDouble(), start.yCoord, end.yCoord) && booltemp[3]) {
            val hitX: Double
            val hitY: Double
            val hitZ: Double
            val ratio: Double
            ratio = (y + 1 - start.yCoord) / (end.yCoord - start.yCoord)
            if (ratio <= 1.1) {
                hitX = start.xCoord + ratio * (end.xCoord - start.xCoord)
                hitY = start.yCoord + ratio * (end.yCoord - start.yCoord)
                hitZ = start.zCoord + ratio * (end.zCoord - start.zCoord)
                if (isIn(hitX, x + w, x + 1 - w) && isIn(hitZ, z + w, z + 1 - w)) return HitResult(Vec3(hitX, hitY, hitZ), Direction.YP.toFacing(), pos)
            }
        }
        // ZN
        if (isIn(z.toDouble(), end.zCoord, start.zCoord) && booltemp[4]) {
            val hitX: Double
            val hitY: Double
            val hitZ: Double
            val ratio: Double
            ratio = (z - start.zCoord) / (end.zCoord - start.zCoord)
            if (ratio <= 1.1) {
                hitX = start.xCoord + ratio * (end.xCoord - start.xCoord)
                hitY = start.yCoord + ratio * (end.yCoord - start.yCoord)
                hitZ = start.zCoord + ratio * (end.zCoord - start.zCoord)
                if (isIn(hitY, y + w, y + 1 - w) && isIn(hitX, x + w, x + 1 - w)) return HitResult(Vec3(hitX, hitY, hitZ), Direction.ZN.toFacing(), pos)
            }
        }
        // ZP
        if (isIn((z + 1).toDouble(), start.zCoord, end.zCoord) && booltemp[5]) {
            val hitX: Double
            val hitY: Double
            val hitZ: Double
            val ratio: Double
            ratio = (z + 1 - start.zCoord) / (end.zCoord - start.zCoord)
            if (ratio <= 1.1) {
                hitX = start.xCoord + ratio * (end.xCoord - start.xCoord)
                hitY = start.yCoord + ratio * (end.yCoord - start.yCoord)
                hitZ = start.zCoord + ratio * (end.zCoord - start.zCoord)
                if (isIn(hitY, y + w, y + 1 - w) && isIn(hitX, x + w, x + 1 - w)) return HitResult(Vec3(hitX, hitY, hitZ), Direction.ZP.toFacing(), pos)
            }
        }
        return null
    }

    fun collisionRayTrace(world: Level, pos: BlockPos, entityLiving: Player): HitResult? {
        // double distanceMax = (double)Minecraft.getInstance().playerController.getBlockReachDistance();
        // Server player state can lag a few ticks behind under low TPS; add margin so raytrace still resolves a side.
        val distanceMax = 8.0
        // Vec3 is immutable on 1.12, so the eye-height offset builds a new vector.
        var start = Vec3(entityLiving.x, entityLiving.y, entityLiving.z)
        if (!world.isClientSide) start = start.add(0.0, 1.62, 0.0)
        val look = entityLiving.getLook(0.5f)
        val end = start.add(look.x * distanceMax, look.y * distanceMax, look.z * distanceMax)
        return collisionRayTrace(world.getBlockState(pos), world, pos, start, end)
    }

    private fun resolveBreakDirection(world: Level, pos: BlockPos, entityPlayer: Player, sixNode: SixNode): Direction? {
        val ray = collisionRayTrace(world, pos, entityPlayer)
        val rayDirection = ray?.let { fromFacing(it.sideHit) }
        if (rayDirection != null && sixNode.getSideEnable(rayDirection)) {
            return rayDirection
        }

        // Fallback: pick the enabled face most directly facing the player's look vector.
        val look = entityPlayer.getLook(0.5f)
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
        return -(look.xCoord * nx + look.yCoord * ny + look.zCoord * nz)
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
        val block = world.getBlock(vect[0], vect[1], vect[2])
        if (block === Blocks.AIR) return false
        return block.defaultState.isOpaqueCube
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

    override fun getLightOpacity(state: BlockState, w: BlockGetter, pos: BlockPos): Int {
        val sne = w.getBlockEntity(pos) as? SixNodeEntity ?: return 0
        val b = sne.sixNodeCacheBlock
        return if (b === Blocks.AIR) 0 else try {
            b.getLightOpacity(b.getStateFromMeta(sne.sixNodeCacheBlockMeta.toInt()), w, pos)
        } catch (e2: Exception) {
            255
        }
        // return b.getIcon(w, x, y, z, side);
    }

    val nodeUuid: String
        get() = "s"

    @OnlyIn(Dist.CLIENT)
    override fun getSelectedBoundingBox(state: BlockState, w: Level, pos: BlockPos): AABB {
        val x = pos.x; val y = pos.y; val z = pos.z
        if (hasVolume(w, x, y, z)) return super.getSelectedBoundingBox(state, w, pos)
        val col = collisionRayTrace(w, pos, Minecraft.getInstance().player)
        val h = 0.2
        val hn = 1 - h
        val b = 0.02
        val bn = 1 - 0.02
        if (col != null) {
            // Utils.println(Direction.fromIntMinecraftSide(col.sideHit));
            when (fromFacing(col.sideHit)) {
                Direction.XN -> return AABB(x.toDouble() + b, y.toDouble(), z.toDouble(), x.toDouble() + h, y.toDouble() + 1, z.toDouble() + 1)
                Direction.XP -> return AABB(x.toDouble() + hn, y.toDouble(), z.toDouble(), x.toDouble() + bn, y.toDouble() + 1, z.toDouble() + 1)
                Direction.YN -> return AABB(x.toDouble(), y.toDouble() + b, z.toDouble(), x.toDouble() + 1, y.toDouble() + h, z.toDouble() + 1)
                Direction.YP -> return AABB(x.toDouble(), y.toDouble() + hn, z.toDouble(), x.toDouble() + 1, y.toDouble() + bn, z.toDouble() + 1)
                Direction.ZN -> return AABB(x.toDouble(), y.toDouble(), z.toDouble() + b, x.toDouble() + 1, y.toDouble() + 1, z.toDouble() + h)
                Direction.ZP -> return AABB(x.toDouble(), y.toDouble(), z.toDouble() + hn, x.toDouble() + 1, y.toDouble() + 1, z.toDouble() + bn)
                null -> TODO()
            }
        }
        return AABB(0.5, 0.5, 0.5, 0.5, 0.5, 0.5) //super.getSelectedBoundingBoxFromPool(w, x, y, z);
        // return AABB((double)p_149633_2_ , (double)p_149633_3_ , (double)p_149633_4_ + this.minZ+0.2, (double)p_149633_2_ + this.maxX, (double)p_149633_3_ + this.maxY, (double)p_149633_4_ + this.maxZ);
        // return super.getSelectedBoundingBoxFromPool(w, x, y, z);
    }

    companion object {
        fun isIn(value: Double, min: Double, max: Double): Boolean {
            return if (value >= min && value <= max) true else false
        }
    }
}
