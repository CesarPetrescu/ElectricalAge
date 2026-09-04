package mods.eln.node.six

import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
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
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.block.state.IBlockState
import net.minecraft.client.Minecraft
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.init.Blocks
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumBlockRenderType
import net.minecraft.util.EnumFacing
import net.minecraft.util.NonNullList
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
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
    override fun getPickBlock(state: IBlockState, target: RayTraceResult, world: World, pos: BlockPos, player: EntityPlayer): ItemStack {
        val entity = world.getTileEntity(pos) as SixNodeEntity?
        if (entity != null) {
            val render = entity.elementRenderList[fromFacing(target.sideHit).int]
            if (render != null) {
                findClosestMatchingHotbarStack(player, render.sixNodeDescriptor)?.let { return it.copy() }
                return render.sixNodeDescriptor.newCreativeTabStack()
            }
        }
        return super.getPickBlock(state, target, world, pos, player)
    }

    private fun findClosestMatchingHotbarStack(player: EntityPlayer, descriptor: SixNodeDescriptor): ItemStack? {
        val inventory = player.inventory ?: return null
        val currentSlot = inventory.currentItem
        var bestStack: ItemStack? = null
        var bestDistance = Int.MAX_VALUE
        for (slot in 0 until 9) {
            val stack = inventory.getStackInSlot(slot).takeUnless { it.isEmpty } ?: continue
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


    override fun getCollisionBoundingBox(state: IBlockState, world: IBlockAccess, pos: BlockPos): AxisAlignedBB? {
        val hasBody = nodeHasCache(world, pos.x, pos.y, pos.z) ||
            (world is World && hasVolume(world, pos.x, pos.y, pos.z))
        return if (hasBody) super.getCollisionBoundingBox(state, world, pos) else NULL_AABB
    }

    fun hasVolume(world: World, x: Int, y: Int, z: Int): Boolean {
        val entity = getEntity(world, x, y, z) ?: return false
        return entity.hasVolume(world, x, y, z)
    }

    override fun getBlockHardness(blockState: IBlockState, world: World, pos: BlockPos): Float {
        return 0.3f
    }

    /**
     * 1.7.10's Block.getDamageValue(world, x, y, z) is gone; the pick-block and drop paths take
     * an IBlockState instead. This stays as a plain helper because getPickBlock() above already
     * builds the right stack from the node's descriptor, which the block state cannot carry.
     */
    fun getDamageValue(world: World, pos: BlockPos): Int {
        val entity = getEntity(world, pos.x, pos.y, pos.z)
        return entity?.getDamageValue(world, pos.x, pos.y, pos.z) ?: 0
    }

    fun getEntity(world: IBlockAccess, x: Int, y: Int, z: Int): SixNodeEntity? {
        val tileEntity = world.getTileEntity(x, y, z)
        if (tileEntity != null && tileEntity is SixNodeEntity) return tileEntity
        println("ASSERTSixNodeEntity getEntity() null")
        return null
    }

    // @SideOnly(Side.CLIENT)
    override fun getSubBlocks(tab: CreativeTabs, subItems: NonNullList<ItemStack>) {
        Eln.sixNodeItem.getSubItems(tab, subItems)
    }

    override fun isOpaqueCube(state: IBlockState): Boolean {
        return false
    }

    override fun isFullCube(state: IBlockState): Boolean {
        return false
    }

    /**
     * Six-nodes draw entirely from their TESR, including the camouflage block a cable can be
     * hidden inside. Phase 3 replaces this with an IExtendedBlockState carrying the camouflaged
     * state plus a delegating baked model.
     */
    override fun getRenderType(state: IBlockState): EnumBlockRenderType {
        return EnumBlockRenderType.INVISIBLE
    }

    /**
     * 1.8 moved per-block tinting out of Block and into BlockColors/IBlockColor, registered on
     * the client. Camouflaged six-nodes need the camouflage block's tint (grass, leaves); that
     * handler is registered in phase 3 alongside the camouflage model.
     */

    /*
	 * @Override public int getLightOpacity(World world, int x, int y, int z) {
	 *
	 * return 255; }
	 */
    override fun getItemDropped(state: IBlockState, rand: Random, fortune: Int): Item? {
        return null
    }

    override fun quantityDropped(par1Random: Random): Int {
        return 0
    }


    override fun isReplaceable(world: IBlockAccess, pos: BlockPos): Boolean {
        return false
    }

    override fun canPlaceBlockOnSide(world: World, pos: BlockPos, side: EnumFacing): Boolean {
        /* see canPlaceBlockAt; it needs changing if this method is fixed */
        return true /*
					 * if(par1World.isRemote) return true; SixNodeEntity tileEntity = (SixNodeEntity) par1World.getBlockTileEntity(par2, par3, par4); if(tileEntity == null || (tileEntity instanceof SixNodeEntity) == false) return true; Direction direction = Direction.fromIntMinecraftSide(par5); SixNode node = (SixNode) tileEntity.getNode(); if(node == null) return true; if(node.getSideEnable(direction))return false;
					 *
					 * return true;
					 */
    }

    override fun canPlaceBlockAt(world: World, pos: BlockPos): Boolean {
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

    override fun onBlockPlacedBy(world: World, pos: BlockPos, front: Direction?, entityLiving: EntityLivingBase?, metadata: Int): Boolean {
        return true
    }

    /*
     * @Override public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer entityPlayer, int minecraftSide, float vx, float vy, float vz) { SixNodeEntity tileEntity = (SixNodeEntity) world.getBlockTileEntity(x, y, z);
     *
     * return tileEntity.onBlockActivated(entityPlayer, Direction.fromIntMinecraftSide(minecraftSide),vx,vy,vz); }
     */
    override fun removedByPlayer(state: IBlockState, world: World, pos: BlockPos, entityPlayer: EntityPlayer, willHarvest: Boolean): Boolean {
        if (world.isRemote) return false
        val x = pos.x; val y = pos.y; val z = pos.z
        val tileEntity = world.getTileEntity(pos) as SixNodeEntity
        val sixNode = tileEntity.node as SixNode? ?: return true
        if (sixNode.sixNodeCacheBlock !== Blocks.AIR) {
            if (isCreative((entityPlayer as EntityPlayerMP)) == false) {
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
        if (!sixNode.playerAskToBreakSubBlock(entityPlayer as EntityPlayerMP, breakDirection)) return false
        return if (sixNode.ifSideRemain) true else super.removedByPlayer(state, world, pos, entityPlayer, willHarvest)
    }

    override fun breakBlock(world: World, pos: BlockPos, state: IBlockState) {
        if (!world.isRemote) {
            val x = pos.x; val y = pos.y; val z = pos.z
            val tileEntity = world.getTileEntity(pos) as SixNodeEntity
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

    override fun neighborChanged(state: IBlockState, world: World, pos: BlockPos, b: Block, fromPos: BlockPos) {
        val x = pos.x; val y = pos.y; val z = pos.z
        val tileEntity = world.getTileEntity(pos) as SixNodeEntity
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
    override fun collisionRayTrace(blockState: IBlockState, world: World, pos: BlockPos, start: Vec3d, end: Vec3d): RayTraceResult? {
        val x = pos.x; val y = pos.y; val z = pos.z
        if (nodeHasCache(world, x, y, z)) return super.collisionRayTrace(blockState, world, pos, start, end)
        val tileEntity = world.getTileEntity(pos) as SixNodeEntity? ?: return null
        if (world.isRemote) {
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
                    return RayTraceResult(Vec3d(0.5, 0.5, 0.5), Direction.YN.toFacing(), pos)
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
                    if (element != null && element.sixNodeElementDescriptor.hasVolume()) return RayTraceResult(Vec3d(0.5, 0.5, 0.5), Direction.YN.toFacing(), pos)
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
                if (isIn(hitY, y + w, y + 1 - w) && isIn(hitZ, z + w, z + 1 - w)) return RayTraceResult(Vec3d(hitX, hitY, hitZ), Direction.XN.toFacing(), pos)
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
                if (isIn(hitY, y + w, y + 1 - w) && isIn(hitZ, z + w, z + 1 - w)) return RayTraceResult(Vec3d(hitX, hitY, hitZ), Direction.XP.toFacing(), pos)
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
                if (isIn(hitX, x + w, x + 1 - w) && isIn(hitZ, z + w, z + 1 - w)) return RayTraceResult(Vec3d(hitX, hitY, hitZ), Direction.YN.toFacing(), pos)
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
                if (isIn(hitX, x + w, x + 1 - w) && isIn(hitZ, z + w, z + 1 - w)) return RayTraceResult(Vec3d(hitX, hitY, hitZ), Direction.YP.toFacing(), pos)
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
                if (isIn(hitY, y + w, y + 1 - w) && isIn(hitX, x + w, x + 1 - w)) return RayTraceResult(Vec3d(hitX, hitY, hitZ), Direction.ZN.toFacing(), pos)
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
                if (isIn(hitY, y + w, y + 1 - w) && isIn(hitX, x + w, x + 1 - w)) return RayTraceResult(Vec3d(hitX, hitY, hitZ), Direction.ZP.toFacing(), pos)
            }
        }
        return null
    }

    fun collisionRayTrace(world: World, pos: BlockPos, entityLiving: EntityPlayer): RayTraceResult? {
        // double distanceMax = (double)Minecraft.getMinecraft().playerController.getBlockReachDistance();
        // Server player state can lag a few ticks behind under low TPS; add margin so raytrace still resolves a side.
        val distanceMax = 8.0
        // Vec3d is immutable on 1.12, so the eye-height offset builds a new vector.
        var start = Vec3d(entityLiving.posX, entityLiving.posY, entityLiving.posZ)
        if (!world.isRemote) start = start.add(0.0, 1.62, 0.0)
        val look = entityLiving.getLook(0.5f)
        val end = start.add(look.x * distanceMax, look.y * distanceMax, look.z * distanceMax)
        return collisionRayTrace(world.getBlockState(pos), world, pos, start, end)
    }

    private fun resolveBreakDirection(world: World, pos: BlockPos, entityPlayer: EntityPlayer, sixNode: SixNode): Direction? {
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

    private fun faceFacingPlayerScore(direction: Direction, look: Vec3d): Double {
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

    fun getIfOtherBlockIsSolid(world: World, x: Int, y: Int, z: Int, direction: Direction): Boolean {
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

    fun nodeHasCache(world: IBlockAccess, x: Int, y: Int, z: Int): Boolean {
        if (isRemote(world)) {
            val tileEntity = world.getTileEntity(x, y, z)
            if (tileEntity != null && tileEntity is SixNodeEntity) return tileEntity.sixNodeCacheBlock !== Blocks.AIR else println("ASSERT B public boolean nodeHasCache(World world, int x, int y, int z) ")
        } else {
            val tileEntity = world.getTileEntity(x, y, z) as SixNodeEntity
            val sixNode = tileEntity.node as SixNode?
            if (sixNode != null) return sixNode.sixNodeCacheBlock !== Blocks.AIR else println("ASSERT A public boolean nodeHasCache(World world, int x, int y, int z) ")
        }
        return false
    }

    override fun getLightOpacity(state: IBlockState, w: IBlockAccess, pos: BlockPos): Int {
        val sne = w.getTileEntity(pos) as? SixNodeEntity ?: return 0
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

    @SideOnly(Side.CLIENT)
    override fun getSelectedBoundingBox(state: IBlockState, w: World, pos: BlockPos): AxisAlignedBB {
        val x = pos.x; val y = pos.y; val z = pos.z
        if (hasVolume(w, x, y, z)) return super.getSelectedBoundingBox(state, w, pos)
        val col = collisionRayTrace(w, pos, Minecraft.getMinecraft().player)
        val h = 0.2
        val hn = 1 - h
        val b = 0.02
        val bn = 1 - 0.02
        if (col != null) {
            // Utils.println(Direction.fromIntMinecraftSide(col.sideHit));
            when (fromFacing(col.sideHit)) {
                Direction.XN -> return AxisAlignedBB(x.toDouble() + b, y.toDouble(), z.toDouble(), x.toDouble() + h, y.toDouble() + 1, z.toDouble() + 1)
                Direction.XP -> return AxisAlignedBB(x.toDouble() + hn, y.toDouble(), z.toDouble(), x.toDouble() + bn, y.toDouble() + 1, z.toDouble() + 1)
                Direction.YN -> return AxisAlignedBB(x.toDouble(), y.toDouble() + b, z.toDouble(), x.toDouble() + 1, y.toDouble() + h, z.toDouble() + 1)
                Direction.YP -> return AxisAlignedBB(x.toDouble(), y.toDouble() + hn, z.toDouble(), x.toDouble() + 1, y.toDouble() + bn, z.toDouble() + 1)
                Direction.ZN -> return AxisAlignedBB(x.toDouble(), y.toDouble(), z.toDouble() + b, x.toDouble() + 1, y.toDouble() + 1, z.toDouble() + h)
                Direction.ZP -> return AxisAlignedBB(x.toDouble(), y.toDouble(), z.toDouble() + hn, x.toDouble() + 1, y.toDouble() + 1, z.toDouble() + bn)
                null -> TODO()
            }
        }
        return AxisAlignedBB(0.5, 0.5, 0.5, 0.5, 0.5, 0.5) //super.getSelectedBoundingBoxFromPool(w, x, y, z);
        // return AxisAlignedBB((double)p_149633_2_ , (double)p_149633_3_ , (double)p_149633_4_ + this.minZ+0.2, (double)p_149633_2_ + this.maxX, (double)p_149633_3_ + this.maxY, (double)p_149633_4_ + this.maxZ);
        // return super.getSelectedBoundingBoxFromPool(w, x, y, z);
    }

    companion object {
        fun isIn(value: Double, min: Double, max: Double): Boolean {
            return if (value >= min && value <= max) true else false
        }
    }
}
