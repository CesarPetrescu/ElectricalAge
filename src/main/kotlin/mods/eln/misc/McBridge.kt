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
 * Bridges between the 1.7.10 shapes Electrical Age is written against and the 1.21 API.
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

/** 1.7.10's `BlockEntity.xCoord`. The block entity knows its own [BlockPos]. */
inline val BlockEntity.xCoord: Int get() = blockPos.x
inline val BlockEntity.yCoord: Int get() = blockPos.y
inline val BlockEntity.zCoord: Int get() = blockPos.z

// ------------------------------------------------------------ block accessors

fun BlockGetter.getBlock(x: Int, y: Int, z: Int): Block =
    getBlockState(BlockPos(x, y, z)).block

/** 1.13 removed block metadata; the blocks that still have one carry it as a state property ([IMetaBlock]). */
fun BlockGetter.getBlockMetadata(x: Int, y: Int, z: Int): Int {
    val state = getBlockState(BlockPos(x, y, z))
    return (state.block as? IMetaBlock)?.metaOfState(state) ?: 0
}

fun BlockGetter.getBlockState(x: Int, y: Int, z: Int): BlockState =
    getBlockState(BlockPos(x, y, z))

@JvmOverloads
fun Level.setBlock(x: Int, y: Int, z: Int, block: Block, meta: Int = 0, flags: Int = 3): Boolean =
    setBlock(BlockPos(x, y, z), (block as? IMetaBlock)?.stateForMeta(meta) ?: block.defaultBlockState(), flags)

fun Level.setBlockToAir(x: Int, y: Int, z: Int): Boolean =
    removeBlock(BlockPos(x, y, z), false)

fun Level.isBlockLoaded(x: Int, y: Int, z: Int): Boolean =
    isLoaded(BlockPos(x, y, z))

fun Level.isBlockLoaded(pos: BlockPos): Boolean = isLoaded(pos)

fun Level.isEmptyBlock(x: Int, y: Int, z: Int): Boolean =
    isEmptyBlock(BlockPos(x, y, z))

fun BlockGetter.getBlockEntity(x: Int, y: Int, z: Int): BlockEntity? =
    getBlockEntity(BlockPos(x, y, z))

fun Level.getIndirectPowerLevelTo(x: Int, y: Int, z: Int, side: Int): Int =
    getSignal(BlockPos(x, y, z), Direction.from3DDataValue(side))

/** 1.7.10's `Level.markBlockForUpdate`: re-send the block to watching clients. */
fun Level.markBlockForUpdate(pos: BlockPos) {
    val state = getBlockState(pos)
    sendBlockUpdated(pos, state, state, 3)
}

fun Level.markBlockForUpdate(x: Int, y: Int, z: Int) = markBlockForUpdate(BlockPos(x, y, z))

// ------------------------------------------------------------- block queries

/** 1.7.10's `Block.isReplaceable(world, x, y, z)`. */
fun Block.isReplaceable(world: BlockGetter, x: Int, y: Int, z: Int): Boolean =
    world.getBlockState(BlockPos(x, y, z)).canBeReplaced()

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

// ------------------------------------------------------------- 1.20.5+ item stack (de)serialisation

/** ItemStack NBT needs a registry lookup since 1.20.5; this finds the current one on either side. */
object McRegistries {
    @JvmStatic
    fun access(): net.minecraft.core.RegistryAccess {
        net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()?.let { return it.registryAccess() }
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient) ClientRegistries.access()?.let { return it }
        return net.minecraft.core.RegistryAccess.EMPTY
    }
}

private object ClientRegistries {
    fun access(): net.minecraft.core.RegistryAccess? = net.minecraft.client.Minecraft.getInstance().level?.registryAccess()
}

/** 1.7.10's `ItemStack.writeToNBT(tag)`. */
fun ItemStack.writeToNBT(tag: net.minecraft.nbt.CompoundTag): net.minecraft.nbt.CompoundTag {
    if (isEmpty) return tag
    val saved = save(McRegistries.access(), tag)
    return saved as? net.minecraft.nbt.CompoundTag ?: tag
}

/** 1.7.10's `ItemStack.loadItemStackFromNBT(tag)`: an unknown item reads as EMPTY, as before it read as null. */
@JvmName("stackFromNbt")
fun stackFromNbt(tag: net.minecraft.nbt.CompoundTag): ItemStack = ItemStack.parseOptional(McRegistries.access(), tag)

// ------------------------------------------------------------- 1.7.10 idioms

/** 1.7.10's `ItemStack.isItemEqual`: same item (damage no longer exists). */
fun ItemStack.isItemEqual(other: ItemStack?): Boolean = other != null && ItemStack.isSameItem(this, other)

/** `Level.rand` became `Level.random` (a RandomSource; nextFloat/nextInt are unchanged). */
inline val Level.rand: net.minecraft.util.RandomSource get() = random

/** Item numeric ids, as `Item.getIdFromItem`/`getItemById` gave them; the byte protocol sends them. */
fun itemId(item: net.minecraft.world.item.Item): Int = net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(item)
fun itemById(id: Int): net.minecraft.world.item.Item = net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(id)
fun blockById(id: Int): Block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.byId(id)

// ------------------------------------------------------------- item NBT (data components since 1.20.5)

/**
 * 1.7.10's `stack.stackTagCompound`. Item NBT is the CUSTOM_DATA component now and immutable in
 * place: the getter returns a *copy*, the setter stores one. Mutating the copy does nothing -
 * use [editTag] for read-modify-write.
 */
var ItemStack.tagCompound: net.minecraft.nbt.CompoundTag?
    get() = get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)?.copyTag()
    set(value) {
        if (value == null) remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
        else set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(value))
    }

fun ItemStack.hasTagCompound(): Boolean = has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)

/** Read-modify-write of the stack's NBT; creates the tag when the stack has none. */
inline fun ItemStack.editTag(edit: (net.minecraft.nbt.CompoundTag) -> Unit) {
    val tag = tagCompound ?: net.minecraft.nbt.CompoundTag()
    edit(tag)
    tagCompound = tag
}

// ------------------------------------------------------------ entity dimension

/** 1.7.10's `entity.dimension`: the integer dimension id of the level the entity is in (see [DimensionIds]). */
val net.minecraft.world.entity.Entity.dimension: Int get() = DimensionIds.id(level())
