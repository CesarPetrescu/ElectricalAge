package mods.eln.eventhandlers

import mods.eln.environment.RoomThermalManager
import net.minecraftforge.event.world.BlockEvent
import net.minecraftforge.event.world.ExplosionEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

class RoomThermalBlockEventsHandler {
    @SubscribeEvent
    fun onBlockBreak(event: BlockEvent.BreakEvent) = onChanged(event)

    @SubscribeEvent
    fun onBlockPlace(event: BlockEvent.PlaceEvent) = onChanged(event)

    @SubscribeEvent
    fun onBlockMultiPlace(event: BlockEvent.MultiPlaceEvent) = onChanged(event)

    private fun onChanged(event: BlockEvent) {
        val pos = event.pos
        RoomThermalManager.onBlockChanged(event.world, pos.x, pos.y, pos.z)
    }

    @SubscribeEvent
    fun onExplosionDetonate(event: ExplosionEvent.Detonate) {
        val world = event.world
        if (world == null || world.isRemote) return
        // 1.8 replaced ChunkPosition with BlockPos throughout, including the explosion's
        // affected-block list.
        for (pos in event.affectedBlocks) {
            RoomThermalManager.onBlockChanged(world, pos.x, pos.y, pos.z)
        }
    }
}
