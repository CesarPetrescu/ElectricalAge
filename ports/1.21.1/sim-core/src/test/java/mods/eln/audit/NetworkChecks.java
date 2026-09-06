package mods.eln.audit;

import java.util.*;
import mods.eln.sim.network.*;
import mods.eln.sim.network.GridTopology.*;
import mods.eln.sim.network.CircuitNetwork.*;
import static mods.eln.audit.HardeningChecks.*;

/** Analytical, topology-edit, polarity and fault-isolation checks against the actual MNA core. */
public final class NetworkChecks {
    private static Device device(int x,int z,GridTopology.Kind kind) {
        return new Device(new Cell(x,1,z),kind,Side.EAST,true,0);
    }
    public static List<Device> ring(boolean capacitor) {
        List<Device> grid=new ArrayList<>();
        grid.add(device(1,0,GridTopology.Kind.SOURCE));
        grid.add(device(1,2,capacitor?GridTopology.Kind.CAPACITOR:GridTopology.Kind.LOAD));
        for(int x:new int[]{0,2})for(int z=0;z<3;z++)grid.add(device(x,z,GridTopology.Kind.WIRE));
        return grid;
    }
    private static Branch branch(String id,CircuitNetwork.Kind kind,String a,String b,double value,double seed) {
        return new Branch(id,kind,a,b,value,seed);
    }
    public static Map<String,Runnable> cases() {
        Map<String,Runnable> tests=new LinkedHashMap<>();
        tests.put("closed-ring-analytical-current",()->{try(CircuitNetwork n=GridTopology.compile(ring(false))){n.step();Reading r=n.reading("1,1,2/body");check(!r.faulted());near(10/11.6,r.current());near(100/11.6,r.voltage());near(r.current(),n.reading("1,1,0/body").current());}});
        tests.put("open-return-has-no-hidden-ground",()->{List<Device>d=ring(false);d.removeIf(x->x.cell().equals(new Cell(0,1,1)));try(CircuitNetwork n=GridTopology.compile(d)){n.step();near(0,n.reading("1,1,2/body").current());near(0,n.reading("1,1,0/body").current());}});
        tests.put("wrong-axis-does-not-connect",()->{List<Device>d=ring(false);d.set(1,new Device(d.get(1).cell(),GridTopology.Kind.LOAD,Side.UP,true,0));try(CircuitNetwork n=GridTopology.compile(d)){n.step();near(0,n.reading("1,1,2/body").current());near(0,n.reading("1,1,0/body").current());}});
        tests.put("load-polarity-is-differential",()->{List<Device>d=ring(false);d.set(1,new Device(d.get(1).cell(),GridTopology.Kind.LOAD,Side.WEST,true,0));try(CircuitNetwork n=GridTopology.compile(d)){n.step();near(-100/11.6,n.reading("1,1,2/body").voltage());near(-10/11.6,n.reading("1,1,2/body").current());}});
        tests.put("capacitor-ring-charge-recurrence",()->{try(CircuitNetwork n=GridTopology.compile(ring(true))){for(int i=1;i<=400;i++){n.step();Reading r=n.reading("1,1,2/body");check(!r.faulted());near(10*(1-Math.pow(1/(1+.05/1.6),i)),r.voltage());near((10-r.voltage())/1.6,r.current());near(.5*r.voltage()*r.voltage(),r.energy());}}});
        tests.put("source-toggle-without-rebuild",()->{try(CircuitNetwork n=GridTopology.compile(ring(true))){double v=0;for(int i=0;i<300;i++){boolean on=i%11<5;n.setSourceVoltage("1,1,0/body",on?10:0);n.step();v=(v+(on?10:0)*.05/1.6)/(1+.05/1.6);near(v,n.reading("1,1,2/body").voltage());}}});
        tests.put("rebuild-preserves-capacitor-history",()->{double v=0;for(int i=0;i<100;i++){List<Device>d=ring(true);d.set(1,new Device(d.get(1).cell(),GridTopology.Kind.CAPACITOR,Side.EAST,true,v));try(CircuitNetwork n=GridTopology.compile(d)){n.step();v=n.reading("1,1,2/body").voltage();near(10*(1-Math.pow(1/(1+.05/1.6),i+1)),v);}}});
        tests.put("isolated-capacitor-preserves-differential-voltage",()->{try(CircuitNetwork n=GridTopology.compile(List.of(new Device(new Cell(-5,-1,-9),GridTopology.Kind.CAPACITOR,Side.DOWN,true,-3)))){for(int i=0;i<20;i++)n.step();near(-3,n.reading("-5,-1,-9/body").voltage());near(0,n.reading("-5,-1,-9/body").current());}});
        tests.put("parallel-capacitors-both-contribute",()->{try(CircuitNetwork n=new CircuitNetwork(List.of(branch("c1",CircuitNetwork.Kind.CAPACITOR,"a","b",1,10),branch("c2",CircuitNetwork.Kind.CAPACITOR,"a","b",1,10),branch("r",CircuitNetwork.Kind.RESISTOR,"a","b",10,0)),.1)){n.step();near(10/1.005,n.reading("c1").voltage());near(-n.reading("r").current(),n.reading("c1").current()+n.reading("c2").current());}});
        tests.put("fault-isolation-and-latching",()->{try(CircuitNetwork n=new CircuitNetwork(List.of(branch("bad1",CircuitNetwork.Kind.VOLTAGE_SOURCE,"x","y",10,0),branch("bad2",CircuitNetwork.Kind.VOLTAGE_SOURCE,"x","y",5,0),branch("good",CircuitNetwork.Kind.CAPACITOR,"g","h",1,3)),.05)){n.step();check(n.reading("bad1").faulted());check(!n.reading("good").faulted());near(3,n.reading("good").voltage());n.setSourceVoltage("bad2",10);n.step();check(n.reading("bad2").faulted());}});
        tests.put("grid-order-independent",()->{List<Device>d=ring(false);Collections.shuffle(d,new Random(41));try(CircuitNetwork a=GridTopology.compile(d);CircuitNetwork b=GridTopology.compile(ring(false))){a.step();b.step();near(a.reading("1,1,2/body").voltage(),b.reading("1,1,2/body").voltage());}});
        tests.put("close-idempotent-rejects-use",()->{CircuitNetwork n=GridTopology.compile(ring(false));n.close();n.close();rejects(n::step);rejects(()->n.reading("1,1,0/body"));});
        tests.put("unknown-reading-rejected",()->{try(CircuitNetwork n=GridTopology.compile(ring(false))){rejects(()->n.reading("unknown"));rejects(()->n.setSourceVoltage("1,1,2/body",4));}});
        tests.put("empty-netlist-is-safe",()->{try(CircuitNetwork n=GridTopology.compile(List.of())){n.step();check(n.islandCount()==0);}});
        tests.put("duplicate-position-rejected",()->rejects(()->GridTopology.compile(List.of(device(0,0,GridTopology.Kind.WIRE),device(0,0,GridTopology.Kind.LOAD)))));
        tests.put("duplicate-branch-rejected",()->{Branch b=branch("id",CircuitNetwork.Kind.RESISTOR,"a","b",1,0);rejects(()->new CircuitNetwork(List.of(b,b),.05));});
        tests.put("device-budget-enforced",()->{List<Device>d=new ArrayList<>();for(int i=0;i<=GridTopology.MAX_DEVICES;i++)d.add(device(i,0,GridTopology.Kind.WIRE));rejects(()->GridTopology.compile(d));});
        tests.put("matrix-budget-enforced",()->{List<Branch>b=new ArrayList<>();for(int i=0;i<130;i++)b.add(branch("r"+i,CircuitNetwork.Kind.RESISTOR,"n"+i,"n"+(i+1),1,0));rejects(()->new CircuitNetwork(b,.05));});
        tests.put("grid-state-bound-enforced",()->rejects(()->new Device(new Cell(0,0,0),GridTopology.Kind.CAPACITOR,Side.EAST,true,1001)));
        tests.put("nonfinite-source-rejected",()->{try(CircuitNetwork n=GridTopology.compile(ring(false))){rejects(()->n.setSourceVoltage("1,1,0/body",Double.NaN));}});
        return tests;
    }
    public static void main(String[] args) {
        int passed=0,failed=0;
        for(var e:cases().entrySet())try{e.getValue().run();passed++;System.out.println("PASS "+e.getKey());}catch(Throwable x){failed++;System.out.println("FAIL "+e.getKey());x.printStackTrace();}
        System.out.println("NETWORK passed="+passed+" failed="+failed);if(failed>0)System.exit(1);
    }
}
