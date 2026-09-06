package mods.eln;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Common proxy for server/client code separation.
 * Server side does nothing, client side registers renderers.
 */
public class CommonProxy {

    /**
     * Called during preInit phase.
     */
    public void preInit(FMLPreInitializationEvent event) {
        // Nothing needed in common preInit
    }

    /**
     * Called during init phase.
     */
    public void init(FMLInitializationEvent event) {
        // Nothing needed in common init
    }
}
