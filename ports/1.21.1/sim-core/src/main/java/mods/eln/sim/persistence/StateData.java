package mods.eln.sim.persistence;
/** Small persistence boundary; deliberately independent of Minecraft and its NBT API. */
public interface StateData {
 double getDouble(String key);
 void setDouble(String key,double value);
 boolean getBoolean(String key);
 void setBoolean(String key,boolean value);
}
