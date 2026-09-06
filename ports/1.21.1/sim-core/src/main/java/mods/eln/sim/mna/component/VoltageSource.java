package mods.eln.sim.mna.component;

import mods.eln.sim.persistence.StateSerializable;
import mods.eln.sim.mna.SubSystem;
import mods.eln.sim.mna.misc.ISubSystemProcessI;
import mods.eln.sim.mna.state.CurrentState;
import mods.eln.sim.mna.state.State;
import mods.eln.sim.persistence.StateData;


public class VoltageSource extends Bipole implements ISubSystemProcessI, StateSerializable {

    String name;

    double u = 0;
    private CurrentState currentState = new CurrentState();

    public VoltageSource(String name) {
        this.name = name;
    }

    public VoltageSource(String name, State aPin, State bPin) {
        super(aPin, bPin);
        this.name = name;
    }

    public VoltageSource setU(double u) {
        if (!Double.isFinite(u)) throw new IllegalArgumentException("Voltage must be finite");
        this.u = u;
        return this;
    }

    public double getU() {
        return u;
    }

    @Override
    public void quitSubSystem() {
        if (subSystem != null) {
            subSystem.removeState(getCurrentState());
            subSystem.removeProcess(this);
        }
        super.quitSubSystem();
    }

    @Override
    public void addedTo(SubSystem s) {
        super.addedTo(s);
        s.addState(getCurrentState());
        s.addProcess(this);
    }

    @Override
    public void applyTo(SubSystem s) {
        s.addToA(aPin, getCurrentState(), 1.0);
        s.addToA(bPin, getCurrentState(), -1.0);
        s.addToA(getCurrentState(), aPin, 1.0);
        s.addToA(getCurrentState(), bPin, -1.0);
    }

    @Override
    public void simProcessI(SubSystem s) {
        s.addToI(getCurrentState(), u);
    }

    public double getI() {
        return -getCurrentState().state;
    }

    @Override
    public double getCurrent() {
        return -getCurrentState().state;
    }

    public CurrentState getCurrentState() {
        return currentState;
    }

    @Override
    public void readState(StateData nbt, String str) {
        str += name;
        // Validate the complete record before mutating either field.
        double voltage = nbt.getDouble(str + "U");
        double current = nbt.getDouble(str + "Istate");
        if (!Double.isFinite(voltage) || !Double.isFinite(current))
            throw new IllegalArgumentException("Source state must be finite");
        setU(voltage);
        currentState.state = current;
    }

    @Override
    public StateData writeState(StateData nbt, String str) {
        str += name;
        nbt.setDouble(str + "U", u);
        nbt.setDouble(str + "Istate", currentState.state);
        return nbt;
    }

    public double getP() {
        return getU() * getI();
    }
}
