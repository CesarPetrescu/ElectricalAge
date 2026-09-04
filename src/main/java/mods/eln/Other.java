package mods.eln;

import net.minecraftforge.fml.common.Loader;

public class Other {

    public static boolean ic2Loaded = false;
    public static boolean ocLoaded = false;
    public static boolean ccLoaded = false;
    public static boolean teLoaded = false;

    public static double wattsToEu;
    public static double wattsToOC;
    public static double wattsToRf;

    // 1.12.2 mod ids are lower-case (ic2.api.info.Info.MOD_ID, li.cil.oc.api.API.ID_OWNER, CC-Tweaked's mcmod.info).
    public static final String modIdIc2 = "ic2";
    public static final String modIdOc = "opencomputers";
    public static final String modIdCc = "computercraft";

    public static void check() {
        ic2Loaded = Loader.isModLoaded(modIdIc2);
        ocLoaded = Loader.isModLoaded(modIdOc);
        ccLoaded = Loader.isModLoaded(modIdCc);
        // The RF bridge now speaks Forge Energy, which ships with Forge itself.
        teLoaded = true;
    }

    public static double getWattsToEu() {
        return wattsToEu;
    }

    public static double getWattsToOC() {
        return wattsToOC;
    }

    public static double getWattsToRf() {
        return wattsToRf;
    }
}
