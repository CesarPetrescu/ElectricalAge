package mods.eln.audit;

import java.util.stream.Stream;
import mods.eln.sim.bench.RcCircuit;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;

class PortRegressionTest {
    @TestFactory Stream<DynamicTest> inheritedNumerics() {
        return NumericalChecks.cases().entrySet().stream().map(e -> DynamicTest.dynamicTest(e.getKey(), e.getValue()::run));
    }
    @TestFactory Stream<DynamicTest> hardening() {
        return HardeningChecks.cases().entrySet().stream().map(e -> DynamicTest.dynamicTest(e.getKey(), e.getValue()::run));
    }
    @TestFactory Stream<DynamicTest> network() {
        return NetworkChecks.cases().entrySet().stream().map(e -> DynamicTest.dynamicTest(e.getKey(), e.getValue()::run));
    }
    @Test void initialState() {
        try (RcCircuit c = new RcCircuit()) { assertEquals(0, c.voltage()); assertTrue(c.powered()); assertEquals(0,c.steps()); }
    }
    @Test void chargeMatchesDiscreteEquation() {
        try (RcCircuit c = new RcCircuit()) {
            for (int i=1;i<=1000;i++) { c.step(); assertEquals(10*(1-Math.pow(1/1.005,i)),c.voltage(),1e-8); }
        }
    }
    @Test void dischargeMatchesDiscreteEquation() {
        try (RcCircuit c = new RcCircuit()) {
            c.restore(new RcCircuit.Snapshot(10,false));
            for(int i=1;i<=1000;i++) { c.step(); assertEquals(10*Math.pow(1/1.005,i),c.voltage(),1e-8); }
        }
    }
    @Test void alternatingPowerMatchesIndependentRecurrence() {
        try (RcCircuit c = new RcCircuit()) {
            double expected=0;
            for(int i=0;i<1000;i++) { boolean on=i%7<3; c.setPowered(on); expected=(expected+(on?10:0)*.005)/1.005; c.step(); assertEquals(expected,c.voltage(),1e-8); }
        }
    }
    @Test void energyAndCurrentHaveCorrectUnits() {
        try (RcCircuit c = new RcCircuit()) { c.restore(new RcCircuit.Snapshot(4,true)); assertEquals(8,c.energy()); assertEquals(.6,c.current(),1e-12); c.setPowered(false); assertEquals(-.4,c.current(),1e-12); }
    }
    @Test void independentInstances() {
        try(RcCircuit a=new RcCircuit(); RcCircuit b=new RcCircuit()) { a.step(); assertTrue(a.voltage()>0); assertEquals(0,b.voltage()); }
    }
    @Test void snapshotRestoresContinuation() {
        try(RcCircuit a=new RcCircuit(); RcCircuit b=new RcCircuit()) {
            for(int i=0;i<137;i++)a.step(); b.restore(a.snapshot());
            for(int i=0;i<100;i++){a.step();b.step();assertEquals(a.voltage(),b.voltage(),1e-10);}
        }
    }
    @Test void invalidSnapshotsRejected() {
        for(double v:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-.1,10.1}) assertThrows(IllegalArgumentException.class,()->new RcCircuit.Snapshot(v,true));
    }
    @Test void closeIsIdempotentAndRejectsTicks() {
        RcCircuit c=new RcCircuit();c.step();c.close();c.close();assertThrows(IllegalStateException.class,c::step);
    }
    @Test void resetDoesNotKeepIntegratorHistory() {
        try(RcCircuit c=new RcCircuit()){for(int i=0;i<50;i++)c.step();c.restore(new RcCircuit.Snapshot(0,true));c.step();assertEquals(10*.005/1.005,c.voltage(),1e-10);assertEquals(1,c.steps());}
    }
}
