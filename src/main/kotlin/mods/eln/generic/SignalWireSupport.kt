package mods.eln.generic

import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.BlockState

/** An inset native housing with explicit mounting faces for ELN signal wire only. */
interface SignalWireSupport {
    fun acceptsSignalWire(state: BlockState, face: Direction): Boolean
}
