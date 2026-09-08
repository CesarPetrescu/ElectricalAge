package mods.eln.sim.nbt;

import mods.eln.misc.INBTTReady;
import mods.eln.sim.mna.component.Resistor;
import mods.eln.sim.mna.state.State;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public class NbtResistor extends Resistor implements INBTTReady {

    final String name;

    public NbtResistor(String name, State aPin, State bPin) {
        super(aPin, bPin);
        this.name = name;
    }

    @Override
    public void readFromNBT(CompoundTag nbt, String str) {
        // Keep the legacy prefix + R key: the component name was never part of it.
        String key = str + "R";
        if (!nbt.contains(key)) return;
        if (!nbt.contains(key, Tag.TAG_ANY_NUMERIC)) {
            reportInvalidResistance("NBT " + name + "/" + key + " is not numeric");
            return;
        }
        setResistance(nbt.getDouble(key));
    }

    @Override
    public void writeToNBT(CompoundTag nbt, String str) {
        nbt.putDouble(str + "R", getResistance());
    }
}
