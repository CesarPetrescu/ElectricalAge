package mods.eln.client

import net.neoforged.neoforge.common.NeoForge

import net.minecraftforge.fml.common.FMLCommonHandler
import net.neoforged.bus.api.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase
import java.util.*

class UuidManager {
    internal val entities = HashMap <Int, IUuidEntity>()
    internal val uuids = HashMap <IUuidEntity, ArrayList<Int>>()

    init {
        NeoForge.EVENT_BUS.register(this)
    }

    fun add(uuid: ArrayList<Int>, e: IUuidEntity) {
        uuid.forEach {
            entities.put(it, e)
            uuids.getOrPut(e, { ArrayList() }).add(it)
        }
    }

    @SubscribeEvent
    fun tick(event: ClientTickEvent.Post) {
        if (event.phase == Phase.END) return

        val i = entities.iterator()

        while (i.hasNext()) {
            val p = i.next()
            if (!p.value.isAlive) {
                uuids.remove(p.value)
                i.remove()
            }
        }
    }

    fun kill(uuid: Int) {
        entities.remove(uuid)?.apply {
            kill()
            uuids.remove(this)?.forEach { entities.remove(it) }
        }
    }
}
