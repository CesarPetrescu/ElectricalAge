package mods.eln.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import mods.eln.sim.bench.RcCircuit;
import mods.eln.sim.mna.*;
import mods.eln.sim.mna.state.*;
import mods.eln.sim.mna.component.*;
import mods.eln.sim.mna.misc.ISubSystemProcessI;
import mods.eln.sim.mna.misc.ISubSystemProcessFlush;
import mods.eln.sim.persistence.StateData;

/** Dependency-free tests, also executed as individual real JUnit dynamic cases. */
public final class HardeningChecks {
    static void check(boolean condition) { if (!condition) throw new AssertionError("Contract failed"); }
    static void near(double expected, double actual) { if (!Double.isFinite(actual) || Math.abs(expected-actual)>1e-8) throw new AssertionError(expected+" != "+actual); }
    static void rejects(Runnable operation) { try { operation.run(); } catch (IllegalArgumentException | IllegalStateException | ArithmeticException expected) { return; } throw new AssertionError("Invalid operation succeeded"); }
    public static Map<String,Runnable> cases() {
        Map<String,Runnable> cases=new LinkedHashMap<>();
        cases.put("singular-preserves-state",()->{SubSystem s=new SubSystem(null,.05);VoltageState n=new VoltageState();n.setU(7);s.addState(n);rejects(s::step);near(7,n.getU());rejects(s::stepFlush);s.breakSystem();});
        cases.put("nonfinite-rhs-preserves-state",()->{SubSystem s=new SubSystem(null,.05);VoltageState n=new VoltageState();n.setU(7);s.addState(n);s.addComponent(new Resistor(n,null).setR(10));s.addProcess((ISubSystemProcessI)x->x.addToI(n,Double.NaN));rejects(s::step);near(7,n.getU());s.breakSystem();});
        cases.put("rhs-overflow-preserves-state",()->{SubSystem s=new SubSystem(null,.05);VoltageState n=new VoltageState();n.setU(7);s.addState(n);s.addComponent(new Resistor(n,null).setR(1));s.addProcess((ISubSystemProcessI)x->{x.addToI(n,1e308);x.addToI(n,1e308);});rejects(s::step);near(7,n.getU());s.breakSystem();});
        cases.put("solution-overflow-preserves-all-states",()->{SubSystem s=new SubSystem(null,.05);VoltageState a=new VoltageState(),b=new VoltageState();a.setU(2);b.setU(3);s.addState(a);s.addState(b);s.addComponent(new Resistor(a,null).setR(10));s.addComponent(new Resistor(b,null).setR(10));s.addProcess((ISubSystemProcessI)x->{x.addToI(a,1);x.addToI(b,1e308);});rejects(s::step);near(2,a.getU());near(3,b.getU());s.breakSystem();});
        cases.put("source-private-state-detached",()->{SubSystem s=new SubSystem(null,.05);VoltageState n=new VoltageState();s.addState(n);VoltageSource v=new VoltageSource("v",n,null);s.addComponent(v);s.removeComponent(v);check(v.getCurrentState().getSubSystem()==null);v.quitSubSystem();s.breakSystem();});
        cases.put("inductor-private-state-detached",()->{SubSystem s=new SubSystem(null,.05);VoltageState n=new VoltageState();s.addState(n);Inductor l=new Inductor("l",n,null);l.setL(1);s.addComponent(l);s.removeComponent(l);check(l.getCurrentState().getSubSystem()==null);l.quitSubSystem();s.breakSystem();});
        cases.put("capacitor-detached-quit",()->new Capacitor().quitSubSystem());
        cases.put("break-clears-ownership",()->{SubSystem s=new SubSystem(null,.05);VoltageState n=new VoltageState();s.addState(n);VoltageSource v=new VoltageSource("v",n,null);s.addComponent(v);s.breakSystem();check(v.getCurrentState().getSubSystem()==null && n.getSubSystem()==null && v.getSubSystem()==null);check(s.states.isEmpty() && s.component.isEmpty());check(!s.breakSystem());rejects(s::step);});
        cases.put("flush-without-calc-rejected",()->rejects(new SubSystem(null,.05)::stepFlush));
        cases.put("duplicate-state-rejected",()->{SubSystem s=new SubSystem(null,.05);VoltageState n=new VoltageState();s.addState(n);rejects(()->s.addState(n));check(s.states.size()==1);s.breakSystem();});
        cases.put("cross-system-state-rejected",()->{SubSystem s=new SubSystem(null,.05),t=new SubSystem(null,.05);VoltageState n=new VoltageState();s.addState(n);rejects(()->t.addState(n));check(t.states.isEmpty());s.breakSystem();t.breakSystem();});
        cases.put("duplicate-component-rejected",()->{SubSystem s=new SubSystem(null,.05);Resistor r=new Resistor(null,null);s.addComponent(r);rejects(()->s.addComponent(r));check(s.component.size()==1);s.breakSystem();});
        cases.put("duplicate-rhs-registration-idempotent",()->{SubSystem s=new SubSystem(null,.05);VoltageState n=new VoltageState();s.addState(n);s.addComponent(new Resistor(n,null).setR(1));ISubSystemProcessI p=x->x.addToI(n,2);s.addProcess(p);s.addProcess(p);s.step();near(2,n.getU());s.breakSystem();});
        cases.put("power-source-not-doubled",()->{SubSystem s=new SubSystem(new RootSystem(.05,1),.05);VoltageState n=new VoltageState();s.addState(n);PowerSource p=new PowerSource("p",n);p.setU(3);s.addComponent(p);s.addComponent(new Resistor(n,null).setR(10));s.step();near(3,n.getU());s.breakSystem();});
        cases.put("rejected-component-add-rolls-back",()->{SubSystem s=new SubSystem(null,.05);rejects(()->s.addComponent(new PowerSource("p",new VoltageState())));check(s.component.isEmpty() && s.states.isEmpty());s.breakSystem();});
        cases.put("capacitor-current-is-not-zero-stub",()->{SubSystem s=new SubSystem(null,.05);VoltageState n=new VoltageState();n.setU(10);s.addState(n);Capacitor c=new Capacitor(n,null);c.setC(1);s.addComponent(c);s.addComponent(new Resistor(n,null).setR(10));s.step();near(-n.getU()/10,c.getCurrent());s.breakSystem();});
        cases.put("capacitor-explicit-differential-history",()->{SubSystem s=new SubSystem(null,.05);VoltageState n=new VoltageState();s.addState(n);Capacitor c=new Capacitor(null,n);c.setC(1);c.setInitialVoltage(6);s.addComponent(c);s.step();near(-6,n.getU());near(0,c.getCurrent());s.breakSystem();});
        cases.put("bench-reset-clears-new-history",()->{try(RcCircuit c=new RcCircuit()){for(int i=0;i<100;i++)c.step();c.restore(new RcCircuit.Snapshot(0,true));c.step();near(10*.005/1.005,c.voltage());}});
        for(double value:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}) cases.put("timestep-"+value,()->rejects(()->new SubSystem(null,value)));
        for(double value:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,Double.MIN_VALUE}) cases.put("resistance-"+value,()->rejects(()->new Resistor().setR(value)));
        for(double value:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY}) {
            cases.put("capacitance-"+value,()->rejects(()->new Capacitor().setC(value)));
            cases.put("inductance-"+value,()->rejects(()->new Inductor("l").setL(value)));
        }
        cases.put("voltage-nan-rejected",()->rejects(()->new VoltageSource("v").setU(Double.NaN)));
        cases.put("source-state-read-is-atomic",()->{VoltageSource source=new VoltageSource("v").setU(3);source.getCurrentState().state=-.25;StateData bad=new Values(Map.of("vU",6.0,"vIstate",Double.NaN));rejects(()->source.readState(bad,""));near(3,source.getU());near(.25,source.getCurrent());});
        cases.put("inductor-nonfinite-restore-rejected",()->{Inductor inductor=new Inductor("l");inductor.getCurrentState().state=.5;rejects(()->inductor.readState(new Values(Map.of("lIstate",Double.POSITIVE_INFINITY)),""));near(.5,inductor.getCurrent());});
        cases.put("forged-state-id-rejected",()->{SubSystem s=new SubSystem(null,.05);VoltageState a=new VoltageState(),b=new VoltageState();s.addState(a);s.addState(b);s.addComponent(new Resistor(a,null).setR(1));s.addComponent(new Resistor(b,null).setR(1));s.generateMatrix();a.setId(b.getId());rejects(()->s.addToI(a,1));s.breakSystem();});
        cases.put("failed-regeneration-invalidates-pending-flush",()->{SubSystem s=new SubSystem(null,.05);VoltageState n=new VoltageState();n.setU(7);s.addState(n);s.addComponent(new Resistor(n,null).setR(1));s.stepCalc();s.component.clear();rejects(s::generateMatrix);rejects(s::stepFlush);near(7,n.getU());s.breakSystem();});
        cases.put("null-rhs-process-rejected",()->{SubSystem s=new SubSystem(null,.05);rejects(()->s.addProcess((ISubSystemProcessI)null));s.breakSystem();});
        cases.put("null-flush-process-rejected",()->{SubSystem s=new SubSystem(null,.05);rejects(()->s.addProcess((ISubSystemProcessFlush)null));s.breakSystem();});
        cases.put("closed-system-rejects-callback-registration",()->{SubSystem s=new SubSystem(null,.05);s.breakSystem();rejects(()->s.addProcess((ISubSystemProcessI)x->{}));rejects(()->s.addProcess((ISubSystemProcessFlush)()->{}));});
        return cases;
    }
    /** Persistence-interface fixture, not a Minecraft/NBT replacement. */
    private static final class Values implements StateData {
        private final Map<String,Double> numbers;
        Values(Map<String,Double> numbers){this.numbers=numbers;}
        public double getDouble(String key){return numbers.getOrDefault(key,0.0);}
        public void setDouble(String key,double value){throw new UnsupportedOperationException();}
        public boolean getBoolean(String key){return false;}
        public void setBoolean(String key,boolean value){throw new UnsupportedOperationException();}
    }
    public static void main(String[] args) {
        int passed=0,failed=0;
        for(var entry:cases().entrySet())try{entry.getValue().run();passed++;System.out.println("PASS "+entry.getKey());}catch(Throwable e){failed++;System.out.println("FAIL "+entry.getKey());e.printStackTrace();}
        System.out.println("HARDENING passed="+passed+" failed="+failed);if(failed>0)System.exit(1);
    }
}
