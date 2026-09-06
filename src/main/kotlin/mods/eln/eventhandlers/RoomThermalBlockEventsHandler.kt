package mods.eln.eventhandlers

import mods.eln.environment.RoomThermalManager
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.level.ExplosionEvent
import net.neoforged.bus.api.SubscribeEvent

class RoomThermalBlockEventsHandler {
    @SubscribeEvent
    fun onBlockBreak(event: BlockEvent.BreakEvent) = onChanged(event)

    @SubscribeEvent
    fun onBlockPlace(event: BlockEvent.PlaceEvent) = onChanged(event)

    @SubscribeEvent
    fun onBlockMultiPlace(event: BlockEvent.MultiPlaceEvent) = onChanged(event)

    private fun onChanged(event: BlockEvent) {
        val pos = event.pos
        RoomThermalManager.onBlockChanged(event.level, pos.x, pos.y, pos.z)
    }

    @SubscribeEvent
    fun onExplosionDetonate(event: ExplosionEvent.Detonate) {
        val world = event.level
        if (world == null || world.isClientSide) return
        // 1.8 replaced ChunkPosition with BlockPos throughout, including the explosion's
        // affected-block list.
        for (pos in event.affectedBlocks) {
            RoomThermalManager.onBlockChanged(world, pos.x, pos.y, pos.z)
        }
    }
}
