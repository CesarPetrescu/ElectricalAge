package mods.eln.sim.mna.component;

import mods.eln.sim.persistence.StateSerializable;
import mods.eln.sim.mna.misc.MnaConst;
import mods.eln.sim.mna.state.State;
import mods.eln.sim.persistence.StateData;

public class ResistorSwitch extends Resistor implements StateSerializable {

    boolean ultraImpedance = false;
    String name;

    boolean state = false;

    protected double baseR = 1;

    public ResistorSwitch(String name, State aPin, State bPin) {
        super(aPin, bPin);
        this.name = name;
    }

    public void setState(boolean state) {
        this.state = state;
        setR(baseR);
    }

    @Override
    public Resistor setR(double r) {
        baseR = r;
        return super.setR(state ? r : (ultraImpedance ? MnaConst.ultraImpedance : MnaConst.highImpedance));
    }

    public boolean getState() {
        return state;
    }

    @Override
    public void readState(StateData nbt, String str) {
        str += name;
        setR(nbt.getDouble(str + "R"));
        if (Double.isNaN(baseR) || baseR == 0) {
            if (ultraImpedance) ultraImpedance();
            else highImpedance();
        }
        setState(nbt.getBoolean(str + "State"));
    }

    @Override
    public StateData writeState(StateData nbt, String str) {
        str += name;
        nbt.setDouble(str + "R", baseR);
        nbt.setBoolean(str + "State", getState());
        return nbt;
    }

    public void mustUseUltraImpedance() {
        ultraImpedance = true;
    }
}
