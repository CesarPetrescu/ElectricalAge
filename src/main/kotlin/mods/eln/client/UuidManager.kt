package mods.eln.client

import net.neoforged.neoforge.common.NeoForge

import net.neoforged.bus.api.SubscribeEvent
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
