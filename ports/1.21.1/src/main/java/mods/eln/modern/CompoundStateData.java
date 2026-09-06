package mods.eln.modern;

import java.util.Objects;
import mods.eln.sim.persistence.StateData;
import net.minecraft.nbt.CompoundTag;

/** Real 1.21.1 NBT adapter for inherited component persistence, not a replacement NBT stub.
 * An enclosing machine schema must decide defaults and validate required fields.
 */
public final class CompoundStateData implements StateData {
    private final CompoundTag tag;
    public CompoundStateData(CompoundTag tag) { this.tag = Objects.requireNonNull(tag); }
    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Component state must be finite");
        return value;
    }
    @Override public double getDouble(String key) { return finite(tag.getDouble(key)); }
    @Override public void setDouble(String key, double value) { tag.putDouble(key, finite(value)); }
    @Override public boolean getBoolean(String key) { return tag.getBoolean(key); }
    @Override public void setBoolean(String key, boolean value) { tag.putBoolean(key, value); }
}
