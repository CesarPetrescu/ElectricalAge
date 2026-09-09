package mods.eln.sim.mna.misc;

import mods.eln.sim.mna.SubSystem;
import mods.eln.sim.mna.component.VoltageSource;

/** A split-port converter must balance its actual electrical power before a step is committed. */
public interface IPowerTransferProcess extends IRootSystemPreStepProcess {
    default void beginTransferStep(double seconds) {}
    boolean isTransferBalanced();
    SubSystem[] transferSystems();
    /** Trial-only commands available for bounded coupled fixed-point acceleration. */
    VoltageSource[] transferSources();
    /** Fail open for this step; never commit an unconverged pair of ideal sources. */
    void suspendTransfer();
}
