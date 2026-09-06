package mods.eln.client;

import net.neoforged.neoforge.common.NeoForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;
import mods.eln.misc.Utils;
import mods.eln.node.NodeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.util.Iterator;

public class FrameTime {
    static FrameTime instance;

    public FrameTime() {
        instance = this;
        NeoForge.EVENT_BUS.register(this);
    }

    public void init() {
        //	NodeBlockEntity.nodeAddedList.clear();
    }

    public void stop() {
        //	NodeBlockEntity.nodeAddedList.clear();
    }

    public static float get2() {
        if (Utils.isGameInPause())
            return 0f;
        return Math.min(0.1f, instance.deltaT);
    }

    public static float getNotCaped2() {
        float value = get2();
        return value;
    }

    float deltaT = 0.02f;
    long oldNanoTime = 0;
    boolean boot = true;

    @SubscribeEvent
    public void tick(RenderTickEvent event) {
        if (event.phase != Phase.START) return;

        long nanoTime = System.nanoTime();

        if (boot) {
            boot = false;
        } else {
            deltaT = (nanoTime - oldNanoTime) * 0.000000001f;
            //	Utils.println(deltaT);
        }

        oldNanoTime = nanoTime;

        //Utils.println(NodeBlockEntity.clientList.size());
        Iterator<NodeBlockEntity> i = NodeBlockEntity.clientList.iterator();
        Level w = Minecraft.getInstance().world;

        if (!Utils.isGameInPause()) {
            float deltaTcaped = getNotCaped2();
            while (i.hasNext()) {
                NodeBlockEntity e = i.next();
                if (e.getWorld() != w) {
                    i.remove();
                    continue;
                }
                e.clientRefresh(deltaTcaped);
            }
        }
        //Minecraft.getMinecraft().world.getChunkFromChunkCoords(1, 1).
        //	Utils.println("delta T : " + deltaT + "   " + event);
    }
}
