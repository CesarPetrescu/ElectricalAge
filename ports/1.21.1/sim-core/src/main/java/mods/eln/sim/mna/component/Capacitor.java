package mods.eln.sim.mna.component;

import mods.eln.sim.mna.SubSystem;
import mods.eln.sim.mna.misc.ISubSystemProcessI;
import mods.eln.sim.mna.state.State;

public class Capacitor extends Bipole implements ISubSystemProcessI, mods.eln.sim.mna.misc.ISubSystemProcessFlush {

    private double c = 0;
    double cdt;
    private double previousVoltage, current;
    private boolean historyInitialized;

    /** Explicit differential history survives graph rebuilds and reference-node changes. */
    public void setInitialVoltage(double voltage) {
        if (!Double.isFinite(voltage)) throw new IllegalArgumentException("Capacitor voltage must be finite");
        previousVoltage = voltage;
        current = 0;
        historyInitialized = true;
    }

    @Override public void simProcessFlush() {
        current = cdt * (getU() - previousVoltage);
        previousVoltage = getU();
    }

    public Capacitor() {
    }

    public Capacitor(State aPin, State bPin) {
        connectTo(aPin, bPin);
    }

    @Override
    public double getCurrent() {
        return current;
    }

    public void setC(double c) {
        if (!Double.isFinite(c) || c < 0) throw new IllegalArgumentException("Capacitance must be finite and nonnegative");
        this.c = c;
        dirty();
    }

    @Override
    public void applyTo(SubSystem s) {
        cdt = c / s.getDt();

        s.addToA(aPin, aPin, cdt);
        s.addToA(aPin, bPin, -cdt);
        s.addToA(bPin, bPin, cdt);
        s.addToA(bPin, aPin, -cdt);
    }

    @Override
    public void simProcessI(SubSystem s) {
        if (!historyInitialized) setInitialVoltage(getU());
        double add = previousVoltage * cdt;
        s.addToI(aPin, add);
        s.addToI(bPin, -add);
    }

    @Override
    public void quitSubSystem() {
        if (subSystem != null) {
            subSystem.removeProcess((ISubSystemProcessI) this);
            subSystem.removeProcess((mods.eln.sim.mna.misc.ISubSystemProcessFlush) this);
        }
        super.quitSubSystem();
    }

    @Override
    public void addedTo(SubSystem s) {
        super.addedTo(s);
        s.addProcess((ISubSystemProcessI) this);
        s.addProcess((mods.eln.sim.mna.misc.ISubSystemProcessFlush) this);
    }

    public double getE() {
        double u = getU();
        return u * u * c / 2;
    }

    public double getC() {
        return c;
    }
}
