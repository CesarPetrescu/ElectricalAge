package mods.eln.sim.mna.component;

import mods.eln.sim.persistence.StateSerializable;
import mods.eln.sim.mna.SubSystem;
import mods.eln.sim.mna.misc.ISubSystemProcessI;
import mods.eln.sim.mna.state.CurrentState;
import mods.eln.sim.mna.state.State;
import mods.eln.sim.persistence.StateData;

public class Inductor extends Bipole implements ISubSystemProcessI, StateSerializable {

    String name;

    private double l = 0;
    double ldt;

    private CurrentState currentState = new CurrentState();

    public Inductor(String name) {
        this.name = name;
    }

    public Inductor(String name, State aPin, State bPin) {
        super(aPin, bPin);
        this.name = name;
    }

    @Override
    public double getCurrent() {
        return currentState.state;
    }

    public double getL() {
        return l;
    }

    public void setL(double l) {
        if (!Double.isFinite(l) || l < 0) throw new IllegalArgumentException("Inductance must be finite and nonnegative");
        this.l = l;
        dirty();
    }

    public double getE() {
        final double i = getCurrent();
        return i * i * l / 2;
    }

    @Override
    public void applyTo(SubSystem s) {
        ldt = -l / s.getDt();

        s.addToA(aPin, currentState, 1);
        s.addToA(bPin, currentState, -1);
        s.addToA(currentState, aPin, 1);
        s.addToA(currentState, bPin, -1);
        s.addToA(currentState, currentState, ldt);
    }

    @Override
    public void simProcessI(SubSystem s) {
        s.addToI(currentState, ldt * currentState.state);
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

    public CurrentState getCurrentState() {
        return currentState;
    }

    @Override
    public void readState(StateData nbt, String str) {
        str += name;
        currentState.state = (nbt.getDouble(str + "Istate"));
    }

    @Override
    public StateData writeState(StateData nbt, String str) {
        str += name;
        nbt.setDouble(str + "Istate", currentState.state);
        return nbt;
    }

    public void resetStates() {
        currentState.state = 0;
    }
}
