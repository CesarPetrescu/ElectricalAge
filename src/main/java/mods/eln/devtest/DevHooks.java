package mods.eln.devtest;

import mods.eln.Eln;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Headless-run helpers, active only under system properties (see tools/port/headless.md):
 * `-Deln.stopAfterStart=<ticks>` halts the dedicated server that many ticks after it has started,
 * which turns `runServer` into a pass/fail boot check.
 */
public final class DevHooks {
    private final int stopAfterTicks;
    private int ticksSinceStart = -1;

    private DevHooks(int stopAfterTicks) {
        this.stopAfterTicks = stopAfterTicks;
    }

    public static void registerIfRequested() {
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) ClientDevHooks.registerIfRequested();
        String stop = System.getProperty("eln.stopAfterStart");
        if (stop == null) return;
        NeoForge.EVENT_BUS.register(new DevHooks(Integer.parseInt(stop)));
    }

    @SubscribeEvent
    public void onStarted(ServerStartedEvent event) {
        ticksSinceStart = 0;
        Eln.LOGGER.info("DEVHOOK server started, halting in {} ticks", stopAfterTicks);
    }

    @SubscribeEvent
    public void onTick(ServerTickEvent.Post event) {
        if (ticksSinceStart < 0) return;
        if (ticksSinceStart++ == stopAfterTicks) {
            Eln.LOGGER.info("DEVHOOK halting server");
            event.getServer().halt(false);
        }
    }
}
