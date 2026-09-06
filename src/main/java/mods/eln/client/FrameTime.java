package mods.eln.client;

import net.neoforged.neoforge.common.NeoForge;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
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
    public void tick(RenderFrameEvent.Post event) {
        if (event.phase != Phase.START) return;

        long nanoTime = System.nanoTime();

        if (boot) {
            boot = false;
        } else {
            deltaT = (nanoTime - oldNanoTime) * 0.000000001f;
            //	Utils.println(deltaT);
        }
        oldNanoTime = nanoTime;
        Iterator<NodeBlockEntity> i = NodeBlockEntity.clientList.iterator();
        Level w = Minecraft.getInstance().level();

        if (!Utils.isGameInPause()) {
            float deltaTcaped = getNotCaped2();
            while (i.hasNext()) {
                NodeBlockEntity e = i.next();
                if (e.getLevel() != w) {
                    i.remove();
                    continue;
                }
                e.clientRefresh(deltaTcaped);
            }
        }
    }
}
