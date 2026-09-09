package net.minecraft.nbt;
import java.util.HashMap;
import java.util.Map;
/** Minimal offline NBT storage, not a replacement for Minecraft serialization tests. */
public final class CompoundTag {
 private final Map<String,Object> values = new HashMap<>();
 public boolean contains(String key) { return values.containsKey(key); }
 public boolean contains(String key,int type) { return contains(key); }
 public void putFloat(String key,float value) { values.put(key,value); }
 public float getFloat(String key) { return (float)getDouble(key); }
 public void putDouble(String key,double value) { values.put(key,value); }
 public double getDouble(String key) { Object value=values.get(key); return value instanceof Number ? ((Number)value).doubleValue() : 0; }
 public void putBoolean(String key,boolean value) { values.put(key,value); }
 public boolean getBoolean(String key) { return Boolean.TRUE.equals(values.get(key)); }
 public void putInt(String key,int value) { values.put(key,value); }
 public int getInt(String key) { return (int)getDouble(key); }
 public void putString(String key,String value) { values.put(key,value); }
 public String getString(String key) { Object value=values.get(key); return value instanceof String ? (String)value : ""; }
}
