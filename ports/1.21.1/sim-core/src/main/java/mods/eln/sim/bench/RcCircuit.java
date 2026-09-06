package mods.eln.sim.bench;

import java.util.Objects;
import mods.eln.sim.mna.SubSystem;
import mods.eln.sim.mna.component.Capacitor;
import mods.eln.sim.mna.component.Resistor;
import mods.eln.sim.mna.component.VoltageSource;
import mods.eln.sim.mna.state.VoltageState;

/** A deliberately bounded integration fixture using the inherited MNA implementation.
 * Each instance is server-owned. It has no world references or global registry.
 * This is not the future world-wide circuit graph manager.
 */
public final class RcCircuit implements AutoCloseable {
    public static final double DT = 0.05, SOURCE_VOLTS = 10.0, RESISTANCE = 10.0, CAPACITANCE = 1.0;
    private final SubSystem system = new SubSystem(null, DT);
    private final VoltageState input = new VoltageState(), output = new VoltageState();
    private final VoltageSource source = new VoltageSource("bench", input, null);
    private final Capacitor capacitor = new Capacitor(output, null);
    private boolean powered = true, closed;
    private long steps;

    public RcCircuit() {
        system.addState(input);
        system.addState(output);
        capacitor.setC(CAPACITANCE);
        system.addComponent(source);
        system.addComponent(new Resistor(input, output).setR(RESISTANCE));
        system.addComponent(capacitor);
        source.setU(SOURCE_VOLTS);
    }
    public void step() {
        requireOpen();
        source.setU(powered ? SOURCE_VOLTS : 0);
        system.step();
        double value = output.getU();
        if (!Double.isFinite(value) || value < -1e-8 || value > SOURCE_VOLTS + 1e-8) {
            throw new ArithmeticException("Bench circuit left its physical voltage bounds: " + value);
        }
        // Remove only round-off at a physical boundary, never mask a divergent solution.
        output.setU(Math.max(0, Math.min(SOURCE_VOLTS, value)));
        steps++;
    }
    public void setPowered(boolean powered) {
        requireOpen();
        this.powered = powered;
        source.setU(powered ? SOURCE_VOLTS : 0);
    }
    public Snapshot snapshot() { requireOpen(); return new Snapshot(voltage(), powered); }
    public void restore(Snapshot state) {
        requireOpen(); Objects.requireNonNull(state, "state");
        output.setU(state.voltage());
        capacitor.setInitialVoltage(state.voltage());
        setPowered(state.powered());
        steps = 0;
    }
    public double voltage() { return output.getU(); }
    public double current() { return ((powered ? SOURCE_VOLTS : 0) - voltage()) / RESISTANCE; }
    public double energy() { return capacitor.getE(); }
    public boolean powered() { return powered; }
    public long steps() { return steps; }
    private void requireOpen() { if (closed) throw new IllegalStateException("Circuit is closed"); }
    @Override public void close() { if (!closed) { system.breakSystem(); closed = true; } }
    public record Snapshot(double voltage, boolean powered) {
        public Snapshot {
            if (!Double.isFinite(voltage) || voltage < 0 || voltage > SOURCE_VOLTS) {
                throw new IllegalArgumentException("Saved bench voltage must be finite and between 0 and 10 V");
            }
        }
    }
}
