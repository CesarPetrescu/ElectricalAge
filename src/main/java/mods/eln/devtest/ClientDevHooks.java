package mods.eln.devtest;

import mods.eln.Eln;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client side of {@link DevHooks}: `-Deln.stopAtTitle=true` closes the game a few ticks after the title screen is up. */
public final class ClientDevHooks {
    private int ticksAtTitle;

    public static void registerIfRequested() {
        if (System.getProperty("eln.stopAtTitle") == null) return;
        NeoForge.EVENT_BUS.register(new ClientDevHooks());
    }

    private int ticks;

    @SubscribeEvent
    public void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks++ % 200 == 0) {
            Eln.LOGGER.info("DEVHOOK tick {} screen={} overlay={}", ticks, mc.screen == null ? null : mc.screen.getClass().getName(), mc.getOverlay());
        }
        // Wait for the loading overlay too: model baking and its "missing texture" warnings end with it.
        if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
        if (ticksAtTitle++ == 20) {
            Eln.LOGGER.info("DEVHOOK title screen reached, stopping the client");
            mc.stop();
        }
    }
}
