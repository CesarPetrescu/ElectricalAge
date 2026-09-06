package mods.eln.sim.network;

import java.util.*;
import mods.eln.sim.mna.SubSystem;
import mods.eln.sim.mna.component.*;
import mods.eln.sim.mna.state.VoltageState;

/** Bounded, thread-confined netlist of independent MNA islands. No world objects or hidden ground.
 * Each island selects an arbitrary reference; only differential measurements leave the core.
 * A failed island is latched and retains its last good readings, without stopping other islands.
 */
public final class CircuitNetwork implements AutoCloseable {
    public static final int MAX_BRANCHES = 1024, MAX_STATES_PER_ISLAND = 128;
    public enum Kind { RESISTOR, CAPACITOR, VOLTAGE_SOURCE }
    public record Branch(String id, Kind kind, String a, String b, double value, double initialVoltage) {
        public Branch {
            Objects.requireNonNull(kind);
            for (String text : List.of(id, a, b))
                if (text.isBlank() || text.length() > 128) throw new IllegalArgumentException("Invalid branch identifier");
            if (a.equals(b)) throw new IllegalArgumentException("Collapsed branch terminals");
            if (!Double.isFinite(value) || !Double.isFinite(initialVoltage)) throw new IllegalArgumentException("Nonfinite branch state");
            if (kind != Kind.VOLTAGE_SOURCE && (value <= 0 || !Double.isFinite(1/value)))
                throw new IllegalArgumentException("Passive component value must be positive");
        }
    }
    public record Reading(double voltage, double current, double energy, boolean faulted) { }
    private final List<Island> islands = new ArrayList<>();
    private final Map<String, Island> owner = new HashMap<>();
    private boolean closed;

    public CircuitNetwork(Collection<Branch> branches, double dt) {
        if (!Double.isFinite(dt) || dt <= 0) throw new IllegalArgumentException("Invalid timestep");
        if (branches.size() > MAX_BRANCHES) throw new IllegalArgumentException("Network branch limit exceeded");
        Map<String, Branch> unique = new TreeMap<>();
        Union union = new Union();
        for (Branch branch : branches) {
            if (unique.put(branch.id(), branch) != null) throw new IllegalArgumentException("Duplicate branch ID");
            union.join(branch.a(), branch.b());
        }
        Map<String,List<Branch>> groups = new TreeMap<>();
        for (Branch branch : unique.values()) groups.computeIfAbsent(union.find(branch.a()), k -> new ArrayList<>()).add(branch);
        try {
            for (List<Branch> group : groups.values()) {
                Island island = new Island(group, dt);
                islands.add(island);
                for (Branch branch : group) owner.put(branch.id(), island);
            }
        } catch (RuntimeException exception) { close(); throw exception; }
    }
    public void step() { requireOpen(); for (Island island : islands) island.step(); }
    public Reading reading(String id) {
        requireOpen(); Island island = owner.get(id);
        if (island == null) throw new IllegalArgumentException("Unknown branch: " + id);
        Reading value = island.readings.get(id);
        return new Reading(value.voltage(), value.current(), value.energy(), island.faulted);
    }
    public void setSourceVoltage(String id, double volts) {
        requireOpen(); Island island = owner.get(id);
        if (island == null || !(island.parts.get(id) instanceof VoltageSource source)) throw new IllegalArgumentException("Unknown source");
        source.setU(volts);
    }
    public int islandCount() { requireOpen(); return islands.size(); }
    private void requireOpen() { if (closed) throw new IllegalStateException("Network is closed"); }
    @Override public void close() { if (!closed) { for (Island island : islands) island.system.breakSystem(); closed = true; } }

    private static final class Island {
        final SubSystem system;
        final Map<String,Bipole> parts = new LinkedHashMap<>();
        Map<String,Reading> readings = new LinkedHashMap<>();
        boolean faulted;
        Island(List<Branch> branches, double dt) {
            SortedSet<String> terminals = new TreeSet<>();
            int extraStates = 0;
            for (Branch branch : branches) { terminals.add(branch.a()); terminals.add(branch.b()); if (branch.kind()==Kind.VOLTAGE_SOURCE) extraStates++; }
            if (terminals.size()-1+extraStates > MAX_STATES_PER_ISLAND) throw new IllegalArgumentException("Connected circuit exceeds 128 MNA states");
            String reference = terminals.first();
            system = new SubSystem(null, dt);
            Map<String,VoltageState> nodes = new HashMap<>();
            for (String terminal : terminals) if (!terminal.equals(reference)) { VoltageState node = new VoltageState(); nodes.put(terminal,node); system.addState(node); }
            try {
                for (Branch branch : branches) {
                    VoltageState a = nodes.get(branch.a()), b = nodes.get(branch.b());
                    Bipole component;
                    switch (branch.kind()) {
                        case RESISTOR -> component = new Resistor(a,b).setR(branch.value());
                        case VOLTAGE_SOURCE -> component = new VoltageSource(branch.id(),a,b).setU(branch.value());
                        case CAPACITOR -> {
                            Capacitor capacitor = new Capacitor(a,b); capacitor.setC(branch.value());
                            capacitor.setInitialVoltage(branch.initialVoltage()); component = capacitor;
                        }
                        default -> throw new IllegalStateException("Unexpected kind");
                    }
                    system.addComponent(component); parts.put(branch.id(),component);
                    double volts = branch.kind()==Kind.CAPACITOR ? branch.initialVoltage() : 0;
                    double energy = branch.kind()==Kind.CAPACITOR ? .5*branch.value()*volts*volts : 0;
                    if (!Double.isFinite(energy)) throw new IllegalArgumentException("Unrepresentable initial capacitor energy");
                    readings.put(branch.id(),new Reading(volts,0,energy,false));
                }
            } catch (RuntimeException failure) { system.breakSystem(); throw failure; }
        }
        void step() {
            if (faulted) return;
            try {
                system.step();
                Map<String,Reading> next = new LinkedHashMap<>();
                for (var entry : parts.entrySet()) {
                    Bipole component = entry.getValue();
                    double voltage = component.getBipoleU(), current = component.getCurrent();
                    double energy = component instanceof Capacitor capacitor ? capacitor.getE() : 0;
                    if (!Double.isFinite(voltage) || !Double.isFinite(current) || !Double.isFinite(energy)) throw new ArithmeticException("Nonfinite device measurement");
                    next.put(entry.getKey(),new Reading(voltage,current,energy,false));
                }
                readings = next;
            } catch (RuntimeException failure) { faulted = true; system.breakSystem(); }
        }
    }
    /** Deterministic path-compressed union/find; bounded callers prevent deep adversarial graphs. */
    static final class Union {
        private final Map<String,String> parent = new HashMap<>();
        String find(String key) {
            parent.putIfAbsent(key,key);
            String root=key;
            while (!root.equals(parent.get(root))) root=parent.get(root);
            while (!key.equals(root)) { String next=parent.get(key);parent.put(key,root);key=next; }
            return root;
        }
        void join(String a,String b) { a=find(a);b=find(b);if(!a.equals(b))parent.put(a.compareTo(b)<0?b:a,a.compareTo(b)<0?a:b); }
    }
}
