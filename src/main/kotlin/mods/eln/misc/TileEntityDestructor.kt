package mods.eln.misc

import net.neoforged.neoforge.common.NeoForge

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.minecraft.world.level.block.entity.BlockEntity
import java.util.*

class TileEntityDestructor {
    var destroyList = ArrayList<BlockEntity>()
    fun clear() {
        destroyList.clear()
    }

    fun add(tile: BlockEntity) {
        destroyList.add(tile)
    }

    @SubscribeEvent
    fun tick(event: ServerTickEvent.Pre) {
        for (t in destroyList) {
            if (t.level != null && t.level!!.getBlockEntity(t.xCoord, t.yCoord, t.zCoord) === t) {
                t.level!!.setBlockToAir(t.xCoord, t.yCoord, t.zCoord)
                Utils.println("destroy light at " + t.xCoord + " " + t.yCoord + " " + t.zCoord)
            }
        }
        destroyList.clear()
    }

    init {
        NeoForge.EVENT_BUS.register(this)
    }
}
