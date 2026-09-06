package mods.eln.misc

import net.minecraft.world.level.block.state.BlockState

/**
 * A block whose 1.7.10 metadata became a block-state property. [McBridge]'s
 * `setBlock(x, y, z, block, meta)` and [Coordinate.meta] go through this so the placement
 * code that still passes a meta keeps working.
 */
interface IMetaBlock {
    fun stateForMeta(meta: Int): BlockState
    fun metaOfState(state: BlockState): Int
}
