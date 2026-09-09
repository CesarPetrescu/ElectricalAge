package mods.eln.sim.mna;

import mods.eln.sim.mna.component.*;
import mods.eln.sim.mna.process.TransformerInterSystemProcess;
import mods.eln.sim.mna.state.VoltageState;

/** Runs against the real MNA implementation; also called by the FML/JUnit wrapper. */
public final class HvMnaRegression {
    private static int assertions;
    private static void close(double expected, double actual, double tolerance, String message) {
        assertions++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > tolerance)
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }
    private static void truth(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static final class Port {
        final SubSystem system = new SubSystem(null, 0.05);
        final VoltageState node = new VoltageState();
        final VoltageSource branch = new VoltageSource("converter", node, null);
        Port() { system.addState(node); system.addComponent(branch); }
        void resistance(double r) { system.addComponent(new Resistor(node, null).setResistance(r)); }
        VoltageSource supply(double volts, double resistance) {
            VoltageState external = new VoltageState();
            system.addState(external);
            VoltageSource supply = new VoltageSource("supply", external, null).setVoltage(volts);
            system.addComponent(supply);
            system.addComponent(new Resistor(external, node).setResistance(resistance));
            return supply;
        }
    }

    public static int runAll() {
        assertions = 0;
        probeDoesNotChangeCommandOrState();
        disabledSourceIsNotAShort();
        probeDoesNotAdvanceCapacitor();
        singularProbeIsExplicit();
        cleanupAndReconnect();
        invalidRatiosRejected();
        transformerSweeps();
        transformerSupplyRemoval();
        transformerMissingSubsystem();
        for (int i=0; i<1000; i++) randomizedTransformer(i);
        System.out.println("HV MNA regression: " + assertions + " assertions passed (1000 seeded networks)");
        return assertions;
    }
    public static void main(String[] args) { runAll(); }

    private static void probeDoesNotChangeCommandOrState() {
        Port p = new Port(); p.supply(800, 4);
        p.node.state = 713;
        p.branch.setVoltage(725);
        p.branch.setEnabled(false);
        double currentState = p.branch.getCurrentState().state;
        SubSystem.Thevenin th = p.system.getTh(p.node, p.branch);
        truth(th.valid, "probe is valid");
        close(800, th.voltage, 1e-8, "probe voltage");
        close(4, th.resistance, 1e-8, "probe resistance");
        close(725, p.branch.getVoltage(), 0, "command restored");
        close(713, p.node.state, 0, "committed node unchanged");
        close(currentState, p.branch.getCurrentState().state, 0, "committed current unchanged");
        truth(!p.branch.isEnabled(), "enabled state restored");
    }

    private static void disabledSourceIsNotAShort() {
        Port p = new Port(); p.resistance(1000);
        Capacitor cap = new Capacitor(p.node, null); cap.setCoulombs(0.001);
        p.system.addComponent(cap); p.node.state = 800;
        p.branch.setVoltage(0); p.branch.setEnabled(false);
        double initial = cap.getEnergy();
        p.system.step();
        close(800 / 1.05, p.node.state, 1e-8, "capacitor discharges through resistor, not source");
        close(0, p.branch.getCurrent(), 0, "disabled current");
        close(0, p.branch.getPower(), 0, "disabled power");
        truth(cap.getEnergy() > 0 && cap.getEnergy() < initial, "capacitor energy not erased or replenished");
        p.branch.setVoltage(800); p.branch.setEnabled(true); p.system.step();
        close(800, p.node.state, 1e-8, "source reenabled");
    }

    private static void probeDoesNotAdvanceCapacitor() {
        Port p = new Port(); p.resistance(1000);
        Capacitor c = new Capacitor(p.node,null); c.setCoulombs(0.005); p.system.addComponent(c);
        p.node.state=3200; p.branch.setEnabled(false);
        double energy=c.getEnergy();
        for (int i=0; i<30; i++) truth(p.system.getTh(p.node,p.branch).valid, "capacitor probe valid");
        close(energy,c.getEnergy(),0,"probes preserve capacitor energy");
        close(0,c.getCurrent(),0,"probes do not commit capacitor current");
        p.system.step();
        close(3200/1.01,p.node.state,1e-7,"single physical timestep after probes");
    }

    private static void singularProbeIsExplicit() {
        SubSystem s = new SubSystem(null,0.05);
        VoltageState orphan = new VoltageState(); s.addState(orphan);
        truth(Double.isNaN(s.solveChecked(orphan)), "singular checked solve is NaN, not zero");
        close(0,s.solve(orphan),0,"legacy solve retains zero fallback");
        Port p = new Port(); p.node.state=Double.NaN;
        truth(!p.system.getTh(p.node,p.branch).valid,"nonfinite probe rejected");
    }

    private static void cleanupAndReconnect() {
        Port p = new Port(); p.resistance(100); p.branch.setVoltage(50);
        for (int i=0;i<30;i++) {
            p.branch.setEnabled(i%2==0); p.system.step();
            truth(p.system.states.size()==2,"no auxiliary state leak on enable/disable");
        }
        p.system.removeComponent(p.branch);
        truth(p.system.states.size()==1,"current state removed on detach");
        p.system.addComponent(p.branch); p.branch.setEnabled(true); p.system.step();
        truth(p.system.states.size()==2,"reattach adds exactly one current state");
        close(50,p.node.state,1e-9,"reattach works");
    }

    private static void invalidRatiosRejected() {
        Port a=new Port(),b=new Port();
        TransformerInterSystemProcess p=new TransformerInterSystemProcess(a.node,b.node,a.branch,b.branch);
        for(double n:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}) {
            boolean rejected=false;
            try {p.setRatio(n);} catch(IllegalArgumentException e) {rejected=true;}
            truth(rejected,"invalid ratio rejected");
        }
    }

    private static void transformerSweeps() {
        for(double ratio:new double[]{1.0/256,1.0/64,1.0/16,1,16,64,256}) {
            Port a=new Port(),b=new Port(); a.supply(50,0.05);
            double resistance=Math.pow(50*ratio,2)/100.0; b.resistance(resistance);
            TransformerInterSystemProcess p=new TransformerInterSystemProcess(a.node,b.node,a.branch,b.branch);
            p.setRatio(ratio); p.rootSystemPreStepProcess(); a.system.step(); b.system.step();
            double expected=50*resistance/(resistance+ratio*ratio*0.05);
            close(expected,a.node.state,1e-6,"primary loaded voltage");
            close(a.node.state*ratio,b.node.state,1e-6,"ratio under load");
            close(-a.branch.getPower(),b.branch.getPower(),1e-5,"input equals output power");
            truth(b.branch.getPower()>0,"forward transfer");
            p.setEnabled(false); a.system.step(); b.system.step();
            close(0,a.branch.getCurrent(),0,"disabled primary");
            close(0,b.branch.getCurrent(),0,"disabled secondary");
        }
    }

    private static void transformerSupplyRemoval() {
        Port a=new Port(),b=new Port(); VoltageSource supply=a.supply(800,0.1); b.resistance(6400);
        TransformerInterSystemProcess p=new TransformerInterSystemProcess(a.node,b.node,a.branch,b.branch);
        p.rootSystemPreStepProcess(); a.system.step(); b.system.step();
        truth(b.branch.getPower()>90,"powered before removal");
        supply.setEnabled(false);
        for(int i=0;i<5;i++) {p.rootSystemPreStepProcess();a.system.step();b.system.step();}
        close(0,b.branch.getPower(),1e-8,"no sustained output after input removal");
    }

    private static void transformerMissingSubsystem() {
        VoltageState a=new VoltageState(),b=new VoltageState();
        VoltageSource x=new VoltageSource("a",a,null),y=new VoltageSource("b",b,null);
        TransformerInterSystemProcess p=new TransformerInterSystemProcess(a,b,x,y);
        p.rootSystemPreStepProcess();
        truth(!x.isEnabled()&&!y.isEnabled(),"missing subsystem fails open without NPE");
    }

    private static void randomizedTransformer(int seed) {
        java.util.Random random=new java.util.Random(seed);
        double ratio=Math.pow(256,2*random.nextDouble()-1);
        double e1=10+random.nextDouble()*1000, e2=random.nextDouble()*1000;
        double r1=.05+random.nextDouble()*2,r2=1+random.nextDouble()*100;
        Port a=new Port(),b=new Port();a.supply(e1,r1);b.supply(e2,r2);
        TransformerInterSystemProcess p=new TransformerInterSystemProcess(a.node,b.node,a.branch,b.branch);
        p.setRatio(ratio);p.rootSystemPreStepProcess();a.system.step();b.system.step();
        double pin=-a.branch.getPower(),pout=b.branch.getPower();
        close(a.node.state*ratio,b.node.state,1e-7*Math.max(1,Math.abs(b.node.state)),"random voltage ratio");
        close(pin,pout,1e-6*Math.max(1,Math.abs(pin)),"random reversible power balance");
    }
}
