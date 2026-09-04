package mods.eln.server

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.FMLCommonHandler
import java.util.ArrayList
import java.util.concurrent.ConcurrentLinkedQueue

class DelayedTaskManager {
    private val tasks = ConcurrentLinkedQueue<ITask>()
    fun clear() {
        tasks.clear()
    }

    @SubscribeEvent
    fun tick(event: ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
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
        MinecraftForge.EVENT_BUS.register(this)
        MinecraftForge.EVENT_BUS.register(this)
    }
}
