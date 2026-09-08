package mods.eln.node

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.chunk.LevelChunk

/** Passive shape/light/redstone queries must not request chunk generation during unload. */
object LoadedBlockEntities {
    fun get(world: BlockGetter, pos: BlockPos): BlockEntity? = when (world) {
        is ServerLevel -> world.chunkSource.getChunkNow(pos.x shr 4, pos.z shr 4)
            ?.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK)
        is Level -> if (world.hasChunkAt(pos)) world.getBlockEntity(pos) else null
        else -> world.getBlockEntity(pos) // Bounded chunk/render views cannot request world generation.
    }
}
