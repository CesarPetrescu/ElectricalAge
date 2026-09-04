@file:JvmName("McBridge")

package mods.eln.misc

import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World

/**
 * Bridges between the 1.7.10 shapes Electrical Age is written against and the 1.12.2 API.
 *
 * Two different things live here and they age differently:
 *
 *  - Pure renames ([xCoord] and friends on [Vec3d]). These are marked deprecated with a
 *    `ReplaceWith`, so `Code > Inspect > Run inspection by name` clears them mechanically once
 *    the port compiles. Nothing is lost by keeping them in the meantime.
 *
 *  - Genuine `(x, y, z)` -> [BlockPos] adapters on [World]/[IBlockAccess]. Roughly 1,600 call
 *    sites in this mod pass loose integer coordinates, most of them derived from
 *    [mods.eln.misc.Coordinate], which stores ints because the simulation indexes by them.
 *    Rewriting every one to allocate a BlockPos would be a large diff that also allocates in
 *    tick-rate paths. These adapters keep the call sites and do the conversion in one place.
 *
 * The adapters deliberately do *not* cover mutating block placement with neighbour
 * notification flags, block breaking, or anything that changes behaviour between versions -
 * those call sites should be looked at individually.
 */

// ----------------------------------------------------------------- Vec3d renames

@Deprecated("Vec3.xCoord became Vec3d.x", ReplaceWith("this.x"))
inline val Vec3d.xCoord: Double get() = x

@Deprecated("Vec3.yCoord became Vec3d.y", ReplaceWith("this.y"))
inline val Vec3d.yCoord: Double get() = y

@Deprecated("Vec3.zCoord became Vec3d.z", ReplaceWith("this.z"))
inline val Vec3d.zCoord: Double get() = z

// -------------------------------------------------------- TileEntity coordinates

/** 1.7.10's `TileEntity.xCoord`. The TE knows its own [BlockPos] on 1.12.2. */
inline val TileEntity.xCoord: Int get() = pos.x
inline val TileEntity.yCoord: Int get() = pos.y
inline val TileEntity.zCoord: Int get() = pos.z

// ------------------------------------------------------------ block accessors

fun IBlockAccess.getBlock(x: Int, y: Int, z: Int): Block =
    getBlockState(BlockPos(x, y, z)).block

fun IBlockAccess.getBlockMetadata(x: Int, y: Int, z: Int): Int =
    getBlockState(BlockPos(x, y, z)).let { it.block.getMetaFromState(it) }

fun IBlockAccess.getBlockState(x: Int, y: Int, z: Int): IBlockState =
    getBlockState(BlockPos(x, y, z))

@JvmOverloads
fun World.setBlock(x: Int, y: Int, z: Int, block: Block, meta: Int = 0, flags: Int = 3): Boolean =
    setBlockState(BlockPos(x, y, z), block.getStateFromMeta(meta), flags)

fun World.setBlockToAir(x: Int, y: Int, z: Int): Boolean =
    setBlockToAir(BlockPos(x, y, z))

fun World.isBlockLoaded(x: Int, y: Int, z: Int): Boolean =
    isBlockLoaded(BlockPos(x, y, z))

fun World.isAirBlock(x: Int, y: Int, z: Int): Boolean =
    isAirBlock(BlockPos(x, y, z))

fun IBlockAccess.getTileEntity(x: Int, y: Int, z: Int): TileEntity? =
    getTileEntity(BlockPos(x, y, z))

fun World.getIndirectPowerLevelTo(x: Int, y: Int, z: Int, side: Int): Int =
    getRedstonePower(BlockPos(x, y, z), EnumFacing.byIndex(side))

/** 1.7.10's `World.markBlockForUpdate`: re-send the block to watching clients. */
fun World.markBlockForUpdate(pos: BlockPos) {
    val state = getBlockState(pos)
    notifyBlockUpdate(pos, state, state, 3)
}

fun World.markBlockForUpdate(x: Int, y: Int, z: Int) = markBlockForUpdate(BlockPos(x, y, z))

// ------------------------------------------------------------- block queries

/** 1.7.10's `Block.isReplaceable(world, x, y, z)`. */
fun Block.isReplaceable(world: IBlockAccess, x: Int, y: Int, z: Int): Boolean =
    isReplaceable(world, BlockPos(x, y, z))
