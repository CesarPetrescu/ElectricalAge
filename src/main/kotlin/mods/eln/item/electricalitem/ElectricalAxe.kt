package mods.eln.item.electricalitem

import mods.eln.i18n.I18N.tr
import mods.eln.misc.Utils
import mods.eln.sim.IProcess
import mods.eln.wiki.Data
import net.minecraft.world.level.block.Block
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.Level
import java.util.*
import kotlin.collections.HashMap
import mods.eln.misc.getBlock
import mods.eln.misc.getBlockMetadata
import mods.eln.misc.isBlockLoaded
import mods.eln.misc.setBlockToAir
import mods.eln.misc.getBlockState
import mods.eln.misc.getBlockEntity


class ElectricalAxe(name: String, strengthOn: Float, strengthOff: Float,
                    energyStorage: Double, energyPerBlock: Double, chargePower: Double)
    : ElectricalTool(name, strengthOn, strengthOff, energyStorage, energyPerBlock, chargePower) {

    override fun setParent(item: Item?, damage: Int) {
        super.setParent(item, damage)
        Data.addPortable(newItemStack())
    }

    override fun addInformation(itemStack: ItemStack?, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)
        list.add(tr("Cuts down trees. Right-click to make it act like a regular axe."))
    }

    override fun getDestroySpeed(stack: ItemStack, state: BlockState): Float {
        // 1.20+: block materials are gone; the axe's targets are the mineable/axe tag plus plants and vines.
        return when {
            state.`is`(BlockTags.MINEABLE_WITH_AXE) || state.`is`(BlockTags.REPLACEABLE_BY_TREES) || state.`is`(BlockTags.LEAVES) || state.`is`(BlockTags.SAPLINGS) -> getStrength(stack)
            else -> super.getDestroySpeed(stack, state)
        }
    }

    override fun onItemRightClick(s: ItemStack, w: Level, p: Player): ItemStack {
        if (!w.isClientSide) {
            setCapitation(p, s, !getCapitation(s))
        }
        return s
    }

    private fun getCapitation(stack: ItemStack): Boolean {
        val nbt = getNbt(stack)
        if (!nbt.contains("capitation")) {
            nbt.putBoolean("capitation", true)
        }
        return nbt.getBoolean("capitation")
    }

    private fun setCapitation(p: Player?, stack: ItemStack, capitation: Boolean) {
        updateNbt(stack) { it.putBoolean("capitation", capitation) }
        if (p != null) {
            Utils.sendMessage(p, "Set treecapitation to $capitation")
        }
    }

    override fun onBlockDestroyed(stack: ItemStack, w: Level, state: BlockState, pos: BlockPos, entity: LivingEntity): Boolean {
        return if (entity is Player && getCapitation(stack)) {
            TreeCapitation.addBlockSwapper(
                world = w,
                player = entity,
                tool = this,
                stack = stack,
                leaves = true,
                origCoords = pos
            )
            true
        } else {
            super.onBlockDestroyed(stack, w, state, pos, entity)
        }
    }
}


/**
 * This code was copied from the Botania Terrasteel Axe, with permission from the Botania license.
 * It was converted to Kotlin, modified to fit the Eln environment, and some Botania-specific code removed.
 *
 * It still refers to Terrasteel, because it behaves like a terrasteel axe. And not...
 * TODO: While this method works, it's not very Eln-y. I'd prefer to drop the tree on the ground... then pop it again.
 *
 * The original code is here: https://github.com/Vazkii/Botania/blob/1.7.10-final/src/main/java/vazkii/botania/common/item/equipment/tool/terrasteel/ItemTerraAxe.java
 */
object TreeCapitation : IProcess {
    /**
     * The number of blocks per tick which the Terra Truncator will
     * collect.
     */
    const val BLOCK_SWAP_RATE = 10

    /**
     * The maximum radius (in blocks) which the Terra Truncator will go
     * in order to try and murder/cut down the tree.
     */
    const val BLOCK_RANGE = 32

    /**
     * The maximum number of leaf blocks which the Terra Truncator will chew/go
     * through once a leaf block is encountered.
     */
    const val LEAF_BLOCK_RANGE = 3

    /**
     * Represents the range which a single block will scan when looking
     * for the next candidates for swapping. 1 is a good default.
     */
    const val SINGLE_BLOCK_RADIUS = 1

    /**
     * Represents a map of dimension IDs to a set of all block swappers
     * active in that dimension.
     */
    private val blockSwappers: MutableMap<Int, List<BlockSwapper>> = HashMap()

    override fun process(time: Double) {
        for ((dim, swappers) in blockSwappers) {
            // Iterate through all of our swappers, removing any
            // which no longer need to tick.
            blockSwappers[dim] = swappers.filter { it.tick() }
        }
    }

    /**
     * Adds a new block swapper to the provided world as the provided player.
     * Block swappers are only added on the server, and a marker instance
     * which is not actually ticked but contains the proper passed in
     * information will be returned to the client.
     *
     * @param world The world to add the swapper to.
     * @param player The player who is responsible for this swapper.
     * @param tool The electrical tool which caused this swapper.
     * @param origCoords The original coordinates the swapper should start at.
     * @param leaves If true, will treat leaves specially (see the BlockSwapper
     * documentation).
     * @return The created block swapper.
     */
    fun addBlockSwapper(world: Level, player: Player, tool: ElectricalTool, origCoords: BlockPos, leaves: Boolean, stack: ItemStack) {
        val swapper = BlockSwapper(world, player, tool, origCoords, BLOCK_RANGE, leaves, stack)

        // Block swapper registration should only occur on the server
        if (world.isClientSide)
            return

        val dim = mods.eln.misc.DimensionIds.id(world)
        blockSwappers[dim] = blockSwappers[dim]?.plus(swapper) ?: listOf(swapper)
    }

    /**
     * A block swapper for the Terra Truncator, which uses a standard
     * Breadth First Search to try and murder/cut down trees.
     *
     * The Terra Truncator will look up to BLOCK_RANGE blocks to find wood
     * to cut down (only cutting down adjacent pieces of wood, so it doesn't
     * jump through the air). However, the tool will only go through
     * LEAF_BLOCK_RANGE leave blocks in order to prevent adjacent trees which
     * are connected only by leaves from being devoured as well.
     *
     * The leaf restriction is implemented by reducing the number of remaining
     * steps to the min of LEAF_BLOCK_RANGE and the current range. The restriction
     * can be removed entirely by setting the "leaves" variable to true, in which
     * case leaves will be treated normally.
     */
    private class BlockSwapper
    /**
     * Creates a new block swapper with the provided parameters.
     * @param world The world the swapper is in.
     * @param player The player responsible for creating this swapper.
     * @param tool The electrical tool responsible for creating this swapper.
     * @param origCoords The original coordinates this swapper should start at.
     * @param range The range this swapper should swap in.
     * @param leaves If true, leaves will be treated specially and
     * severely reduce the radius of further spreading when encountered.
     */
    (
        /**
         * The world the block swapper is doing the swapping in.
         */
        private val world: Level,
        /**
         * The player the swapper is swapping for.
         */
        private val player: Player,
        /**
         * The Terra Truncator which created this swapper.
         */
        private val tool: ElectricalTool,
        /**
         * The origin of the swapper (eg, where it started).
         */
        private val origin: BlockPos,
        /**
         * The initial range which this block swapper starts with.
         */
        private val range: Int,
        /**
         * Denotes whether leaves should be treated specially.
         */
        private val treatLeavesSpecial: Boolean,

        /**
         * The electrical tool which crated this swapper.
         */
        val stack: ItemStack) {

        /**
         * The priority queue of all possible candidates for swapping.
         */
        private val candidateQueue: PriorityQueue<SwapCandidate>

        /**
         * The set of already swaps coordinates which do not have
         * to be revisited.
         */
        private val completedCoords: MutableSet<BlockPos>

        init {

            this.candidateQueue = PriorityQueue<SwapCandidate>()
            this.completedCoords = HashSet()

            // Add the origin to our candidate queue with the original range
            candidateQueue.offer(SwapCandidate(this.origin, this.range))
        }

        /**
         * Ticks this Block Swapper, which allows it to swap BLOCK_SWAP_RATE
         * further blocks and expands the breadth first search. The return
         * value signifies whether or not the block swapper has more blocks
         * to swap, or if it has finished swapping.
         * @return True if the block swapper has more blocks to swap, false
         * otherwise (implying it can be safely removed).
         */
        fun tick(): Boolean {
            // If empty, this swapper is done.
            if (candidateQueue.isEmpty())
                return false

            var remainingSwaps = BLOCK_SWAP_RATE
            while (remainingSwaps > 0 && !candidateQueue.isEmpty()) {
                val candidate = candidateQueue.poll()

                // If we've already completed this location, move along, as this
                // is just a suboptimal one.
                if (completedCoords.contains(candidate.coordinates))
                    continue

                // If this candidate is out of range, discard it.
                if (candidate.range <= 0)
                    continue

                // Otherwise, perform the break and then look at the adjacent tiles.
                // This is a ridiculous function call here.
                removeBlockWithDrops(player, tool, stack, world, candidate.coordinates)

                remainingSwaps--

                completedCoords.add(candidate.coordinates)

                // Then, go through all of the adjacent blocks and look if
                // any of them are any good.
                for (adj in adjacent(candidate.coordinates)) {
                    val adjState = world.getBlockState(adj)
                    val block = adjState.block

                    val isWood = adjState.`is`(BlockTags.LOGS)
                    val isLeaf = adjState.`is`(BlockTags.LEAVES)

                    // If it's not wood or a leaf, we aren't interested.
                    if (!isWood && !isLeaf)
                        continue

                    // If we treat leaves specially and this is a leaf, it gets
                    // the minimum of the leaf range and the current range - 1.
                    // Otherwise, it gets the standard range - 1.
                    val newRange = if (treatLeavesSpecial && isLeaf)
                        Math.min(LEAF_BLOCK_RANGE, candidate.range - 1)
                    else
                        candidate.range - 1

                    candidateQueue.offer(SwapCandidate(adj, newRange))
                }
            }

            // If we did any iteration, then hang around until next tick.
            return true
        }

        fun adjacent(original: BlockPos): List<BlockPos> {
            val coords = ArrayList<BlockPos>()
            // Visit all the surrounding blocks in the provided radius.
            // Gotta love these nested loops, right?
            for (dx in -SINGLE_BLOCK_RADIUS..SINGLE_BLOCK_RADIUS)
                for (dy in -SINGLE_BLOCK_RADIUS..SINGLE_BLOCK_RADIUS)
                    for (dz in -SINGLE_BLOCK_RADIUS..SINGLE_BLOCK_RADIUS) {
                        // Skip the central tile.
                        if (dx == 0 && dy == 0 && dz == 0)
                            continue

                        coords.add(BlockPos(original.x + dx, original.y + dy, original.z + dz))
                    }

            return coords
        }

        /**
         * Represents a potential candidate for swapping/removal. Sorted by
         * range (where a larger range is more preferable). As we're using
         * a priority queue, which is a min-heap internally, larger ranges
         * are considered "smaller" than smaller ranges (so they show up in the
         * min-heap first).
         */
        class SwapCandidate
        /**
         * Constructs a new Swap Candidate with the provided
         * coordinates and range.
         * @param coordinates The coordinates of this candidate.
         * @param range The remaining range of this candidate.
         */
        (
            /**
             * The location of this swap candidate.
             */
            var coordinates: BlockPos,
            /**
             * The remaining range of this swap candidate.
             */
            var range: Int) : Comparable<SwapCandidate> {

            override fun compareTo(other: SwapCandidate): Int {
                // Aka, a bigger range implies a smaller value, meaning
                // bigger ranges will be preferred in a min-heap
                return other.range - range
            }

            override fun equals(other: Any?): Boolean {
                if (other !is SwapCandidate) return false

                val cand = other as SwapCandidate?
                return coordinates == cand!!.coordinates && range == cand.range
            }

            override fun hashCode(): Int {
                var result = coordinates.hashCode()
                result = 31 * result + range
                return result
            }
        }
    }

    /**
     * The bits below, however, are from ToolCommons.java. Mostly. Maybe about half, by now.
     */
    fun removeBlockWithDrops(player: Player, tool: ElectricalTool, stack: ItemStack, world: Level, pos: BlockPos) {
        if (world.isClientSide || !world.isBlockLoaded(pos))
            return

        val state = world.getBlockState(pos)
        val block = state.block

        if (!state.isAir && state.getDestroyProgress(player, world, pos) > 0) {
            if (!block.canHarvestBlock(state, world, pos, player))
                return

            if (!player.isCreative()) {
                val energy = tool.getEnergy(stack)
                tool.subtractEnergyForBlockBreak(stack, state)
                val newEnergy = tool.getEnergy(stack)
                if (newEnergy > 0 && newEnergy < energy) {
                    // Same sequence as PlayerInteractionManager.tryHarvestBlock. The tile entity
                    // is captured before removedByPlayer() discards it, because harvestBlock()
                    // hands it to the block so drops that depend on it (inventories, our own
                    // node blocks) can still read it.
                    val tileEntity = world.getBlockEntity(pos)
                    block.playerWillDestroy(world, pos, state, player)

                    if (block.onDestroyedByPlayer(state, world, pos, player, true, world.getFluidState(pos))) {
                        block.destroy(world, pos, state)
                        block.playerDestroy(world, player, pos, state, tileEntity, stack)
                    }
                }
            } else {
                world.removeBlock(pos, false)
            }
        }
    }
}
