package mods.eln.server

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.common.NeoForge
import java.util.ArrayList
import java.util.concurrent.ConcurrentLinkedQueue

class DelayedTaskManager {
    private val tasks = ConcurrentLinkedQueue<ITask>()
    fun clear() {
        tasks.clear()
    }

    @SubscribeEvent
    fun tick(event: ServerTickEvent.Post) {
        val cpy = ArrayList<ITask>()
        while (true) {
            val task = tasks.poll() ?: break
            cpy.add(task)
        }
        for (t in cpy) {
            t.run()
        }
    }

    interface ITask {
        fun run()
    }

    fun add(t: ITask) {
        tasks.add(t)
    }

    init {
        NeoForge.EVENT_BUS.register(this)
    }
}
