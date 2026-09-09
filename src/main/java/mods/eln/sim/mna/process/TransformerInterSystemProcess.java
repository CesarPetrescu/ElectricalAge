package mods.eln.sim.mna.process;

import mods.eln.sim.mna.SubSystem;
import mods.eln.sim.mna.SubSystem.Thevenin;
import mods.eln.sim.mna.component.VoltageSource;
import mods.eln.sim.mna.misc.IPowerTransferProcess;
import mods.eln.sim.mna.state.State;

/** Reversible fixed-ratio inter-system coupling, with fail-open lifecycle handling. */
public class TransformerInterSystemProcess implements IPowerTransferProcess {
    final State aState, bState;
    final VoltageSource aVoltgeSource, bVoltgeSource;
    private double ratio = 1.0;
    private boolean enabled = true;
    private double retryAfterSeconds;

    public TransformerInterSystemProcess(State aState, State bState,
            VoltageSource aVoltgeSource, VoltageSource bVoltgeSource) {
        this.aState = aState;
        this.bState = bState;
        this.aVoltgeSource = aVoltgeSource;
        this.bVoltgeSource = bVoltgeSource;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) { retryAfterSeconds = 0.0; open(); }
    }

    public boolean isEnabled() {
        return enabled && retryAfterSeconds <= 0.0 && aVoltgeSource.isEnabled() && bVoltgeSource.isEnabled();
    }

    private void open() {
        aVoltgeSource.setEnabled(false);
        bVoltgeSource.setEnabled(false);
        aVoltgeSource.setVoltage(0.0);
        bVoltgeSource.setVoltage(0.0);
    }

    @Override
    public void rootSystemPreStepProcess() {
        SubSystem sa = aVoltgeSource.getSubSystem();
        SubSystem sb = bVoltgeSource.getSubSystem();
        if (!enabled || retryAfterSeconds > 0.0 || sa == null || sb == null) { open(); return; }
        Thevenin a = sa.getTh(aState, aVoltgeSource);
        Thevenin b = sb.getTh(bState, bVoltgeSource);
        double denominator = b.resistance + ratio * ratio * a.resistance;
        if (!a.valid || !b.valid || !(denominator > 0.0) || !Double.isFinite(denominator)) {
            open(); return;
        }
        // Weighted form avoids multiplying HV values by enormous open-circuit resistances.
        double voltage = a.voltage * (b.resistance / denominator)
                + b.voltage * (ratio * a.resistance / denominator);
        double secondary = voltage * ratio;
        if (!Double.isFinite(voltage) || !Double.isFinite(secondary)) { open(); return; }
        aVoltgeSource.setVoltage(voltage);
        bVoltgeSource.setVoltage(secondary);
        aVoltgeSource.setEnabled(true);
        bVoltgeSource.setEnabled(true);
    }

    public void setRatio(double ratio) {
        if (!Double.isFinite(ratio) || ratio <= 0.0) {
            throw new IllegalArgumentException("Transformer ratio must be finite and positive");
        }
        this.ratio = ratio;
    }

    public double getRatio() { return ratio; }

    @Override public void beginTransferStep(double seconds) {
        retryAfterSeconds = Math.max(0.0, retryAfterSeconds - seconds);
    }
    @Override public boolean isTransferBalanced() {
        return PowerTransferChecks.balanced(aVoltgeSource, bVoltgeSource, false,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }
    @Override public SubSystem[] transferSystems() {
        return new SubSystem[] { aVoltgeSource.getSubSystem(), bVoltgeSource.getSubSystem() };
    }
    @Override public VoltageSource[] transferSources() {
        return new VoltageSource[] { aVoltgeSource, bVoltgeSource };
    }
    @Override public void suspendTransfer() {
        open();
        retryAfterSeconds = 1.0;
    }

}
