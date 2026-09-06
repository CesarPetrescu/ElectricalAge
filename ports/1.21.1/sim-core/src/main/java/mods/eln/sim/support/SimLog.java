package mods.eln.sim.support;
public final class SimLog {
 private SimLog() {}
 public static void println(Object message) { System.getLogger("eln.sim").log(System.Logger.Level.DEBUG,String.valueOf(message)); }
 public static void print(Object message) { println(message); }
}
