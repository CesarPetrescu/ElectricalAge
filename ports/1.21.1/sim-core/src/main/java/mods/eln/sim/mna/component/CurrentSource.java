package mods.eln.sim.mna.component;

import mods.eln.sim.persistence.StateData;
import mods.eln.sim.persistence.StateSerializable;
import mods.eln.sim.mna.SubSystem;
import mods.eln.sim.mna.misc.ISubSystemProcessI;
import mods.eln.sim.mna.state.State;

/** Primitive adapted from age-series 6a8cd0d; retains the audited lifecycle correction. */
public class CurrentSource extends Bipole implements ISubSystemProcessI, StateSerializable {
    private double current;
    private final String name;
    public CurrentSource(String name) { this.name = name; }
    public CurrentSource(String name, State pinA, State pinB) { super(pinA, pinB); this.name = name; }
    public CurrentSource setCurrent(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Current must be finite");
        current = value;
        return this;
    }
    @Override public double getCurrent() { return current; }
    @Override public void applyTo(SubSystem system) { }
    @Override public void addedTo(SubSystem system) { super.addedTo(system); system.addProcess(this); }
    @Override public void quitSubSystem() {
        if (subSystem != null) subSystem.removeProcess(this);
        super.quitSubSystem();
    }
    @Override public void simProcessI(SubSystem system) {
        system.addToI(aPin, current);
        system.addToI(bPin, -current);
    }
    @Override public void readState(StateData data, String prefix) { setCurrent(data.getDouble(prefix + name + "I")); }
    @Override public StateData writeState(StateData data, String prefix) { data.setDouble(prefix + name + "I", current); return data; }
}
