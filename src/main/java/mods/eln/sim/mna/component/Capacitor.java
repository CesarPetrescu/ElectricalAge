package mods.eln.sim.mna.component;

import mods.eln.sim.mna.SubSystem;
import mods.eln.sim.mna.misc.ISubSystemProcessI;
import mods.eln.sim.mna.misc.ISubSystemProcessFlush;
import mods.eln.sim.mna.state.State;

public class Capacitor extends Bipole implements ISubSystemProcessI, ISubSystemProcessFlush {

    private double coulombs = 0;
    double coulombsPerStep;
    private double current;
    private double stepVoltage;
    private double stepConductance;
    private boolean sampledStep;

    public Capacitor() {}

    public Capacitor(State aPin, State bPin) {
        connectTo(aPin, bPin);
    }

    @Override
    public double getCurrent() {
        return current;
    }

    public void setCoulombs(double coulombs) {
        this.coulombs = coulombs;
        dirty();
    }

    @Override
    public void applyToSubsystem(SubSystem s) {
        coulombsPerStep = coulombs / s.getDt();

        s.addToA(aPin, aPin, coulombsPerStep);
        s.addToA(aPin, bPin, -coulombsPerStep);
        s.addToA(bPin, bPin, coulombsPerStep);
        s.addToA(bPin, aPin, -coulombsPerStep);
    }

    @Override
    public void simProcessI(SubSystem s) {
        double voltage = s.getXSafe(aPin) - s.getXSafe(bPin);
        if (s.isCalculatingStep()) {
            stepVoltage = voltage;
            stepConductance = coulombsPerStep;
            sampledStep = true;
        }
        double add = voltage * coulombsPerStep;
        s.addToI(aPin, add);
        s.addToI(bPin, -add);
    }

    @Override
    public void simProcessFlush() {
        SubSystem s = getLocalSubSystem();
        double solvedCurrent = sampledStep && s != null && s.hasValidStepSolution()
                ? (getVoltage() - stepVoltage) * stepConductance : 0;
        current = Double.isFinite(solvedCurrent) ? solvedCurrent : 0;
        sampledStep = false;
    }

    @Override
    public void quitSubSystem() {
        SubSystem localSubSystem = getLocalSubSystem();
        if (localSubSystem != null) {
            localSubSystem.removeProcess((ISubSystemProcessI) this);
            localSubSystem.removeProcess((ISubSystemProcessFlush) this);
        }
        current = 0;
        sampledStep = false;
        super.quitSubSystem();
    }

    @Override
    public void addToSubsystem(SubSystem s) {
        super.addToSubsystem(s);
        current = 0;
        sampledStep = false;
        s.addProcess((ISubSystemProcessI) this);
        s.addProcess((ISubSystemProcessFlush) this);
    }

    /**
     * getEnergy
     * (V^2 * C) / 2 = E
     *
     * @return energy, in joules
     */
    public double getEnergy() {
        double voltage = getVoltage();
        return voltage * voltage * coulombs / 2;
    }

    public double getCoulombs() {
        return coulombs;
    }
}
