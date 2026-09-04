package mods.eln.misc

import net.minecraftforge.common.MinecraftForge

import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent
import net.minecraft.tileentity.TileEntity
import java.util.*

class TileEntityDestructor {
    var destroyList = ArrayList<TileEntity>()
    fun clear() {
        destroyList.clear()
    }

    fun add(tile: TileEntity) {
        destroyList.add(tile)
    }

    @SubscribeEvent
    fun tick(event: ServerTickEvent) {
        if (event.phase != TickEvent.Phase.START) return
        for (t in destroyList) {
            if (t.world != null && t.world.getTileEntity(t.xCoord, t.yCoord, t.zCoord) === t) {
                t.world.setBlockToAir(t.xCoord, t.yCoord, t.zCoord)
                Utils.println("destroy light at " + t.xCoord + " " + t.yCoord + " " + t.zCoord)
            }
        }
        destroyList.clear()
    }

    init {
        MinecraftForge.EVENT_BUS.register(this)
    }
}
