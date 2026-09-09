package mods.eln;
/** Offline-only logging facade: no game bootstrap. */
public final class Eln {
 public static final Log LOGGER = new Log();
 public static final Log logger = LOGGER;
 public static int simMetricsPublishIntervalTicks = 20;
 public static final class Log { public void warn(String s,Object... v) {} public void info(String s,Object... v) {} }
}
