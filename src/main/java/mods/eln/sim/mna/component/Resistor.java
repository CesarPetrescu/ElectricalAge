package mods.eln.sim.mna.component;

import mods.eln.Eln;
import mods.eln.sim.mna.SubSystem;
import mods.eln.sim.mna.misc.MnaConst;
import mods.eln.sim.mna.state.State;

public class Resistor extends Bipole {

    public Resistor() {
    }

    public Resistor(State aPin, State bPin) {
        super(aPin, bPin);
    }

    private double resistance = MnaConst.highImpedance;
    private double resistanceInverse = 1 / MnaConst.highImpedance;
    private String lastRejectedResistance;


    public double getResistanceInverse() {
        return resistanceInverse;
    }

    public double getResistance() {
        return resistance;
    }

    public double getPower() {
        return getVoltage() * getCurrent();
    }

    public Resistor setResistance(double resistance) {
        double inverse = 1 / resistance;
        if (!(resistance > 0) || !Double.isFinite(resistance)
                || !(inverse > 0) || !Double.isFinite(inverse)) {
            reportInvalidResistance("value=" + resistance);
            return this;
        }
        if (this.resistance != resistance) {
            this.resistance = resistance;
            this.resistanceInverse = inverse;
            lastRejectedResistance = null;
            dirty();
        }
        return this;
    }

    /** Invalid runtime or saved inputs must not corrupt a valid solver component. */
    protected void reportInvalidResistance(String reason) {
        if (!reason.equals(lastRejectedResistance)) {
            lastRejectedResistance = reason;
            Eln.LOGGER.warn("Rejected resistance for {}: {}; retaining {} ohms", this, reason, resistance);
        }
    }

    public void highImpedance() {
        setResistance(MnaConst.highImpedance);
    }

    public Resistor pullDown() {
        setResistance(MnaConst.pullDown);
        return this;
    }

    @Override
    public void applyToSubsystem(SubSystem s) {
        s.addToA(aPin, aPin, resistanceInverse);
        s.addToA(aPin, bPin, -resistanceInverse);
        s.addToA(bPin, bPin, resistanceInverse);
        s.addToA(bPin, aPin, -resistanceInverse);
    }

    @Override
    public double getCurrent() {
        return getVoltage() * resistanceInverse;
    }
}
