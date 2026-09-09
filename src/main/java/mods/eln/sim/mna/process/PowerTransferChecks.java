package mods.eln.sim.mna.process;

import mods.eln.sim.mna.SubSystem;
import mods.eln.sim.mna.component.VoltageSource;

/** Speculative power accounting. No State.state writes, flushing or heat integration. */
public final class PowerTransferChecks {
    private PowerTransferChecks() {}
    public static double deliveredCurrent(VoltageSource source) {
        if (!source.isEnabled()) return 0.0;
        SubSystem s = source.getSubSystem();
        return s == null ? Double.NaN : -s.solveChecked(source.getCurrentState());
    }
    public static boolean balanced(VoltageSource input, VoltageSource output, boolean oneWay,
            double inputLimit, double outputLimit) {
        double iin = -deliveredCurrent(input), iout = deliveredCurrent(output);
        double pin = input.getVoltage() * iin, pout = output.getVoltage() * iout;
        if (!Double.isFinite(pin) || !Double.isFinite(pout)) return false;
        if (oneWay && (iin < -1.0e-9 || iout < -1.0e-9 || pin < -1.0e-8 || pout < -1.0e-8)) return false;
        if (Math.abs(iin) > inputLimit * (1.0 + 1.0e-7) + 1.0e-9) return false;
        if (Math.abs(iout) > outputLimit * (1.0 + 1.0e-7) + 1.0e-9) return false;
        return Math.abs(pin - pout) <= 1.0e-7 * Math.max(1.0, Math.max(Math.abs(pin), Math.abs(pout)));
    }
}
