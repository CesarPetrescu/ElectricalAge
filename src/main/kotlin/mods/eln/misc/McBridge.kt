@file:JvmName("McBridge")

package mods.eln.misc

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.core.Direction
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Bridges between the 1.7.10 shapes Electrical Age is written against and the 1.12.2 API.
 *
 * Two different things live here and they age differently:
 *
 *  - Pure renames ([xCoord] and friends on [Vec3]). These are marked deprecated with a
 *    `ReplaceWith`, so `Code > Inspect > Run inspection by name` clears them mechanically once
 *    the port compiles. Nothing is lost by keeping them in the meantime.
 *
 *  - Genuine `(x, y, z)` -> [BlockPos] adapters on [Level]/[BlockGetter]. Roughly 1,600 call
 *    sites in this mod pass loose integer coordinates, most of them derived from
 *    [mods.eln.misc.Coordinate], which stores ints because the simulation indexes by them.
 *    Rewriting every one to allocate a BlockPos would be a large diff that also allocates in
 *    tick-rate paths. These adapters keep the call sites and do the conversion in one place.
 *
 * The adapters deliberately do *not* cover mutating block placement with neighbour
 * notification flags, block breaking, or anything that changes behaviour between versions -
 * those call sites should be looked at individually.
 */

// ----------------------------------------------------------------- Vec3 renames

@Deprecated("Vec3.xCoord became Vec3.x", ReplaceWith("this.x"))
inline val Vec3.xCoord: Double get() = x

@Deprecated("Vec3.yCoord became Vec3.y", ReplaceWith("this.y"))
inline val Vec3.yCoord: Double get() = y

@Deprecated("Vec3.zCoord became Vec3.z", ReplaceWith("this.z"))
inline val Vec3.zCoord: Double get() = z

// -------------------------------------------------------- BlockEntity coordinates

/** 1.7.10's `BlockEntity.xCoord`. The TE knows its own [BlockPos] on 1.12.2. */
inline val BlockEntity.xCoord: Int get() = pos.x
inline val BlockEntity.yCoord: Int get() = pos.y
inline val BlockEntity.zCoord: Int get() = pos.z

// ------------------------------------------------------------ block accessors

fun BlockGetter.getBlock(x: Int, y: Int, z: Int): Block =
    getBlockState(BlockPos(x, y, z)).block

fun BlockGetter.getBlockMetadata(x: Int, y: Int, z: Int): Int =
    getBlockState(BlockPos(x, y, z)).let { it.block.getMetaFromState(it) }

fun BlockGetter.getBlockState(x: Int, y: Int, z: Int): BlockState =
    getBlockState(BlockPos(x, y, z))

@JvmOverloads
fun Level.setBlock(x: Int, y: Int, z: Int, block: Block, meta: Int = 0, flags: Int = 3): Boolean =
    setBlockState(BlockPos(x, y, z), block.getStateFromMeta(meta), flags)

fun Level.setBlockToAir(x: Int, y: Int, z: Int): Boolean =
    setBlockToAir(BlockPos(x, y, z))

fun Level.isBlockLoaded(x: Int, y: Int, z: Int): Boolean =
    isBlockLoaded(BlockPos(x, y, z))

fun Level.isEmptyBlock(x: Int, y: Int, z: Int): Boolean =
    isAirBlock(BlockPos(x, y, z))

fun BlockGetter.getBlockEntity(x: Int, y: Int, z: Int): BlockEntity? =
    getTileEntity(BlockPos(x, y, z))

fun Level.getIndirectPowerLevelTo(x: Int, y: Int, z: Int, side: Int): Int =
    getRedstonePower(BlockPos(x, y, z), Direction.byIndex(side))

/** 1.7.10's `Level.markBlockForUpdate`: re-send the block to watching clients. */
fun Level.markBlockForUpdate(pos: BlockPos) {
    val state = getBlockState(pos)
    notifyBlockUpdate(pos, state, state, 3)
}

fun Level.markBlockForUpdate(x: Int, y: Int, z: Int) = markBlockForUpdate(BlockPos(x, y, z))

// ------------------------------------------------------------- block queries

/** 1.7.10's `Block.isReplaceable(world, x, y, z)`. */
fun Block.isReplaceable(world: BlockGetter, x: Int, y: Int, z: Int): Boolean =
    isReplaceable(world, BlockPos(x, y, z))

// ------------------------------------------------------------- empty stacks

/**
 * 1.11+: an empty slot, an empty hand and a failed lookup are [ItemStack.EMPTY], never null.
 * Every 1.7.10 `stack == null` test means this. The contract keeps Kotlin's smart cast for the
 * `!isNothing()` branch, exactly like `String?.isNullOrEmpty()`.
 */
@OptIn(ExperimentalContracts::class)
fun ItemStack?.isNothing(): Boolean {
    contract { returns(false) implies (this@isNothing != null) }
    return this == null || this.isEmpty
}
