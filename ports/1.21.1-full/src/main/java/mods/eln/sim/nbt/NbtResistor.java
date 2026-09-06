package mods.eln.sim.nbt;

import mods.eln.misc.INBTTReady;
import mods.eln.sim.mna.component.Resistor;
import mods.eln.sim.mna.state.State;
import net.minecraft.nbt.CompoundTag;

public class NbtResistor extends Resistor implements INBTTReady {

    String name;

    public NbtResistor(String name, State aPin, State bPin) {
        super(aPin, bPin);
        this.name = name;
    }

    @Override
    public void readFromNBT(CompoundTag nbt, String str) {
        name += str;
        setR(nbt.getDouble(str + "R"));
    }

    @Override
    public CompoundTag writeToNBT(CompoundTag nbt, String str) {
        name += str;
        nbt.setDouble(str + "R", getR());
        return nbt;
    }
}
