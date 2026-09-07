package mods.eln.transparentnode.evaporative;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Same numerical cases run locally with javac and in the NeoForge JUnit suite. */
public final class EvaporationRegression {
    public record Case(String name, Runnable body) {}
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void near(double a, double b, double tolerance) { check(Math.abs(a-b) <= tolerance, a + " != " + b); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid input accepted");
    }
    private static EvaporationModel.Step step(double t, double rh, boolean wet, double water) {
        return EvaporationModel.step(t, 35, rh, 46, 8000, water, 135, 18000, .05, wet);
    }
    private static double[] simulate(double dt, boolean wet) {
        double temp = 70, water = 4000, latentJ = 0, electricJ = 0, sensibleJ = 0;
        var environment = EvaporationModel.environment(35, 30);
        int steps = (int)Math.round(600 / dt);
        for (int i = 0; i < steps; i++) {
            var s = EvaporationModel.step(temp, environment, 46, 8000, water, 135, 18000, dt, wet);
            temp += (1500 - s.netCoolingWatts()) * dt / 18000;
            water -= s.waterMb(); latentJ += s.evaporationWatts()*dt;
            electricJ += s.electricalHeatWatts()*dt; sensibleJ += s.sensibleWatts()*dt;
        }
        near((temp - 70) * 18000, 1500 * steps * dt + electricJ - sensibleJ - latentJ, 1e-5);
        return new double[]{temp, water};
    }
    public static List<Case> cases() {
        return List.of(
            new Case("ASHRAE saturation pressure references", () -> {
                near(EvaporationModel.saturationPressure(0), 611.15, 1);
                near(EvaporationModel.saturationPressure(20), 2338.8, 2);
                near(EvaporationModel.saturationPressure(35), 5627.8, 3);
            }),
            new Case("wet bulb bounded by dry bulb", () -> {
                for (double t : new double[]{5,20,35,50}) for (double rh : new double[]{1,20,50,80,100})
                    check(EvaporationModel.wetBulbEstimate(t,rh) <= t + 1e-8, "Wet bulb above ambient");
            }),
            new Case("saturated wet bulb equals ambient", () -> near(EvaporationModel.wetBulbEstimate(35,100),35,1e-8)),
            new Case("dry-air wet bulb reference", () -> near(EvaporationModel.wetBulbEstimate(35,20),19.0,1.0)),
            new Case("humidity penalizes evaporation", () -> check(step(40,20,true,4000).evaporationWatts() > step(40,80,true,4000).evaporationWatts(),"Humidity ignored")),
            new Case("hot surface still evaporates at 100 percent RH", () -> check(step(60,100,true,4000).evaporationWatts() > 0,"Saturation wrongly disables hot cooling")),
            new Case("saturated isothermal surface does not evaporate", () -> near(step(35,100,true,4000).evaporationWatts(),0,1e-8)),
            new Case("dry mode consumes no water", () -> near(step(60,20,false,4000).waterMb(),0,0)),
            new Case("empty tank has no evaporative cooling", () -> near(step(60,20,true,0).evaporationWatts(),0,0)),
            new Case("last water limits energy", () -> {
                var s = step(60,20,true,.00001);
                near(s.waterMb(),.00001,1e-12);
                near(s.evaporationWatts()*.05, s.waterMb()*.001*EvaporationModel.latentHeat(60),1e-12);
            }),
            new Case("latent rating is bounded", () -> check(step(90,20,true,4000).evaporationWatts() <= 8000,"Capacity exceeded")),
            new Case("below ambient dry surface warms", () -> check(step(20,20,false,4000).sensibleWatts() < 0,"One-way sensible exchange")),
            new Case("freezing and hot-water cutoffs", () -> {
                near(step(0,20,true,4000).waterMb(),0,0); near(step(96,20,true,4000).waterMb(),0,0);
            }),
            new Case("net energy includes motor heat", () -> {
                var s = step(60,20,true,4000);
                near(s.netCoolingWatts(), s.sensibleWatts()+s.evaporationWatts()-135,1e-10);
            }),
            new Case("no non-finite or invalid inputs", () -> {
                rejects(() -> step(Double.NaN,20,true,4000)); rejects(() -> step(60,-1,true,4000));
                rejects(() -> step(60,101,true,4000)); rejects(() -> step(60,20,true,-1));
                rejects(() -> EvaporationModel.step(50,35,20,46,8000,4000,135,18000,0,true));
            }),
            new Case("network conservation and timestep invariance", () -> {
                var a = simulate(.1,true); var b = simulate(.05,true); var c = simulate(.0025,true);
                near(a[0],c[0],.02); near(b[0],c[0],.01); near(a[1],c[1],1.0);
            }),
            new Case("wet cooling beats same-size dry sink", () -> check(simulate(.05,true)[0] + 10 < simulate(.05,false)[0],"No useful wet benefit")),
            new Case("sub-mB film accounting", () -> {
                var f = new FractionalWater(); var tank = new AtomicInteger(1);
                double consumed = 0;
                for (int i=0;i<1000;i++) consumed += f.consume(.00025,tank.get(), n -> {tank.addAndGet(-n);return n;});
                near(consumed,.25,1e-12); near(tank.get()+f.filmMb()+consumed,1,1e-12);
            }),
            new Case("film save restart equivalent", () -> {
                var f = new FractionalWater(); var tank = new AtomicInteger(4);
                f.consume(.123,tank.get(),n -> {tank.addAndGet(-n);return n;});
                var restored = new FractionalWater(); restored.restore(f.filmMb());
                near(restored.consume(.125,tank.get(),n -> n),f.consume(.125,tank.get(),n -> n),1e-12);
                near(restored.filmMb(),f.filmMb(),1e-12);
            }),
            new Case("last fractional film consumed once", () -> {
                var f = new FractionalWater(); f.restore(.25);
                near(f.consume(1,0,n->0),.25,0); near(f.consume(1,0,n->0),0,0);
            }),
            new Case("invalid film restore sanitized", () -> {
                var f = new FractionalWater(); f.restore(Double.NaN);near(f.filmMb(),0,0);
                f.restore(123);check(f.filmMb()<1,"Unbounded restored water");
            }),
            new Case("partial fluid drain cannot create free evaporation", () -> {
                var f=new FractionalWater(); near(f.consume(10,20,n->2),2,0);near(f.filmMb(),0,0);
            }),
            new Case("menu commands whitelisted and clamped", () -> {
                var c = new EvaporativeControls(); check(!c.command(999),"Unknown command accepted");
                for(int i=0;i<200;i++){c.command(10);c.command(20);} near(c.targetCelsius(),5,0);near(c.fanPercent(),10,0);
                for(int i=0;i<200;i++){c.command(13);c.command(21);} near(c.targetCelsius(),90,0);near(c.fanPercent(),100,0);
            }),
            new Case("auto control hysteresis", () -> {
                var c=new EvaporativeControls(); check(c.demand(43,false).speed()>0,"Fan not started");
                check(!c.demand(43,false).wet(),"Water started too early");check(c.demand(46,false).wet(),"Water not started");
                check(c.demand(44,false).wet(),"No wet hysteresis");check(!c.demand(42,false).wet(),"Water not stopped");
                near(c.demand(40,false).speed(),0,0);
            }),
            new Case("off and redstone interlocks", () -> {
                var c=new EvaporativeControls();c.command(3);c.command(30);
                near(c.demand(80,false).speed(),0,0);check(c.demand(80,true).wet(),"High signal not honored");
                c.command(30);near(c.demand(80,true).speed(),0,0);check(c.demand(80,false).wet(),"Low signal not honored");
                c.command(0);near(c.demand(80,false).speed(),0,0);
            }),
            new Case("control save values sanitized", () -> {
                var c=new EvaporativeControls();c.restore(500,-20,400,-5);
                near(c.mode(),3,0);near(c.targetCelsius(),5,0);near(c.fanPercent(),100,0);near(c.redstoneMode(),0,0);
            })
        );
    }
    public static void main(String[] args) {
        int failed=0;
        for(var test:cases()) try {test.body.run();System.out.println("PASS "+test.name);} catch(Throwable e){failed++;System.err.println("FAIL "+test.name+": "+e);}
        System.out.println("Evaporative regression: "+cases().size()+" cases, "+failed+" failed");
        if(failed>0)System.exit(1);
    }
}
