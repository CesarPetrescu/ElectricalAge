package mods.eln.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import mods.eln.misc.FunctionTable;
import mods.eln.sim.mna.SubSystem;
import mods.eln.sim.mna.RootSystem;
import mods.eln.sim.mna.component.Capacitor;
import mods.eln.sim.mna.component.CurrentSource;
import mods.eln.sim.mna.component.Inductor;
import mods.eln.sim.mna.component.Resistor;
import mods.eln.sim.mna.component.VoltageSource;
import mods.eln.sim.mna.misc.ISubSystemProcessI;
import mods.eln.sim.mna.misc.ISubSystemProcessFlush;
import mods.eln.sim.mna.misc.Matrix;
import mods.eln.sim.mna.state.VoltageState;

/** Inherited numerical regressions now exercise the extracted production core without any game stubs. */
public final class NumericalChecks {
    private NumericalChecks() { }
    public static void close(double expected,double actual,double tolerance) {
        if (!Double.isFinite(actual) || Math.abs(expected-actual)>tolerance) {
            throw new AssertionError("expected "+expected+", got "+actual+", tolerance "+tolerance);
        }
    }
    private static void fails(Runnable action) {
        try {action.run();} catch (RuntimeException expected) {return;}
        throw new AssertionError("Expected rejection, not an inverse containing invalid values");
    }
    private static Matrix matrix(double[][] data) {
        Matrix m=new Matrix(data.length,data.length==0?0:data[0].length);
        for(int r=0;r<data.length;r++)for(int c=0;c<data[r].length;c++)m.setEntry(r,c,data[r][c]);
        return m;
    }
    public static void inverseCheck(double[][] a,double tol) {
        Matrix source=matrix(a);Matrix inv=source.getInverse();
        for(int r=0;r<a.length;r++)for(int c=0;c<a.length;c++) {
            double product=0.;for(int k=0;k<a.length;k++)product+=a[r][k]*inv.getEntry(k,c);
            close(r==c?1.:0.,product,tol);
            close(a[r][c],source.getEntry(r,c),0.);
        }
    }
    public static Map<String,Runnable> cases() {
        Map<String,Runnable> checks=new LinkedHashMap<String,Runnable>();
        checks.put("matrix/identity",()->inverseCheck(new double[][]{{1,0},{0,1}},1e-12));
        checks.put("matrix/permutation",()->inverseCheck(new double[][]{{0,1},{1,0}},1e-12));
        checks.put("matrix/non-symmetric",()->inverseCheck(new double[][]{{3,7,-2},{4,-1,2},{0,3,9}},1e-11));
        checks.put("matrix/zero-rejected",()->fails(()->matrix(new double[][]{{0,0},{0,0}}).getInverse()));
        checks.put("matrix/singular-dependent-rejected",()->fails(()->matrix(new double[][]{{1,2},{2,4}}).getInverse()));
        checks.put("matrix/NaN-rejected",()->fails(()->matrix(new double[][]{{Double.NaN}}).getInverse()));
        checks.put("matrix/infinity-rejected",()->fails(()->matrix(new double[][]{{Double.POSITIVE_INFINITY}}).getInverse()));
        checks.put("matrix/non-square-rejected",()->fails(()->new Matrix(2,3).getInverse()));
        checks.put("matrix/defensive-data-copy",()->{
            Matrix m=matrix(new double[][]{{2}});double[][] copy=m.getData();copy[0][0]=99;close(2,m.getEntry(0,0),0);
        });
        for(final double scale:new double[]{1e-200,1e-20,1e-16,1e-8,1.,1e8,1e155,1e200}) {
            checks.put("matrix/uniform-scale-"+scale,()->inverseCheck(new double[][]{{2*scale,scale},{scale,3*scale}},1e-11));
        }
        checks.put("matrix/hilbert-6",()->{
            double[][] a=new double[6][6];for(int r=0;r<6;r++)for(int c=0;c<6;c++)a[r][c]=1./(r+c+1.);
            inverseCheck(a,1e-7);
        });
        checks.put("matrix/near-maximum-finite-diagonal",()->inverseCheck(new double[][]{{1e308,0},{0,-1e308}},1e-12));
        checks.put("matrix/tiny-finite-diagonal",()->inverseCheck(new double[][]{{1e-308,0},{0,1e-308}},1e-12));
        checks.put("matrix/unrepresentable-inverse-rejected",()->fails(()->matrix(new double[][]{{Double.MIN_VALUE}}).getInverse()));
        checks.put("matrix/column-scale-disparity",()->inverseCheck(new double[][]{{1e-100,0},{0,1e100}},1e-12));
        for(int idx=0;idx<300;idx++) {
            final int seed=idx;
            checks.put("matrix/seeded-diagonally-dominant-"+idx,()->{
                Random random=new Random(seed);int n=2+seed%11;double[][] a=new double[n][n];
                for(int r=0;r<n;r++) {double sum=0;for(int c=0;c<n;c++) {a[r][c]=random.nextDouble()*2-1;sum+=Math.abs(a[r][c]);}a[r][r]+=sum+1;}
                inverseCheck(a,2e-11);
            });
        }
        checks.put("mna/voltage-resistor",()->{
            SubSystem s=new SubSystem(null,.05);VoltageState n=new VoltageState();s.addState(n);
            VoltageSource v=new VoltageSource("source",n,null).setU(50);Resistor r=new Resistor(n,null).setR(10);
            s.addComponent(v);s.addComponent(r);s.step();close(50,n.getU(),1e-9);close(5,r.getCurrent(),1e-10);close(5,v.getCurrent(),1e-10);
        });
        checks.put("mna/divider-and-dirty-rebuild",()->{
            SubSystem s=new SubSystem(null,.05);VoltageState a=new VoltageState(),b=new VoltageState();s.addState(a);s.addState(b);
            VoltageSource v=new VoltageSource("source",a,null).setU(12);Resistor first=new Resistor(a,b).setR(10),second=new Resistor(b,null).setR(20);
            s.addComponent(v);s.addComponent(first);s.addComponent(second);s.step();close(8,b.getU(),1e-10);
            second.setR(10);s.step();close(6,b.getU(),1e-10);
        });
        checks.put("mna/RHS-addition",()->{
            SubSystem s=new SubSystem(null,.05);VoltageState n=new VoltageState();s.addState(n);s.addComponent(new Resistor(n,null).setR(10));
            s.addProcess((ISubSystemProcessI)(system->system.addToI(n,.1)));
            s.addProcess((ISubSystemProcessI)(system->system.addToI(n,.2)));
            s.step();close(3,n.getU(),1e-11);
        });
        checks.put("mna/RHS-cancellation",()->{
            SubSystem s=new SubSystem(null,.05);VoltageState n=new VoltageState();s.addState(n);s.addComponent(new Resistor(n,null).setR(10));
            s.addProcess((ISubSystemProcessI)(system->system.addToI(n,.1)));
            s.addProcess((ISubSystemProcessI)(system->system.addToI(n,-.1)));
            s.step();close(0,n.getU(),1e-11);
        });
        checks.put("mna/RHS-cleared-every-step",()->{
            SubSystem s=new SubSystem(null,.05);VoltageState n=new VoltageState();s.addState(n);s.addComponent(new Resistor(n,null).setR(10));
            ISubSystemProcessI p=system->system.addToI(n,.1);s.addProcess(p);
            for(int i=0;i<20;i++){s.step();close(1,n.getU(),1e-11);}
            s.removeProcess(p);s.step();close(0,n.getU(),1e-11);
        });
        checks.put("mna/parallel-capacitors-discharge",()->{
            SubSystem s=new SubSystem(null,.1);VoltageState n=new VoltageState();n.setU(10);s.addState(n);s.addComponent(new Resistor(n,null).setR(10));
            for(int i=0;i<2;i++){Capacitor c=new Capacitor(n,null);c.setC(1);s.addComponent(c);}
            s.step();close(10/(1+.1/20),n.getU(),1e-10);
        });
        checks.put("mna/RC-transient",()->{
            SubSystem s=new SubSystem(null,.05);VoltageState a=new VoltageState(),b=new VoltageState();s.addState(a);s.addState(b);
            s.addComponent(new VoltageSource("source",a,null).setU(10));s.addComponent(new Resistor(a,b).setR(2));
            Capacitor c=new Capacitor(b,null);c.setC(.5);s.addComponent(c);
            for(int i=1;i<=100;i++){s.step();close(10*(1-Math.pow(1/1.05,i)),b.getU(),1e-8);}
        });
        checks.put("mna/RL-transient",()->{
            SubSystem s=new SubSystem(null,.05);VoltageState a=new VoltageState(),b=new VoltageState();s.addState(a);s.addState(b);
            s.addComponent(new VoltageSource("source",a,null).setU(10));s.addComponent(new Resistor(a,b).setR(2));
            Inductor l=new Inductor("L",b,null);l.setL(1);s.addComponent(l);
            for(int i=1;i<=100;i++){s.step();close(5*(1-Math.pow(1/1.1,i)),l.getCurrent(),1e-8);}
        });
        checks.put("mna/flush-remove",()->{
            SubSystem s=new SubSystem(null,.05);final int[] n={0};ISubSystemProcessFlush p=()->n[0]++;
            s.addProcess(p);s.step();s.removeProcess(p);s.step();close(1,n[0],0);
        });
        checks.put("mna/rootless-break",()->{SubSystem s=new SubSystem(null,.05);s.addState(new VoltageState());s.breakSystem();});
        checks.put("mna/root-system-circuit",()->{
            RootSystem root=new RootSystem(.05,1);VoltageState n=new VoltageState();root.addState(n);
            VoltageSource v=new VoltageSource("source",n,null).setU(24);Resistor r=new Resistor(n,null).setR(12);
            root.addComponent(v);root.addComponent(r);root.step();close(24,n.getU(),1e-9);close(2,r.getCurrent(),1e-10);
        });
        checks.put("table/interpolate-and-extrapolate",()->{
            FunctionTable t=new FunctionTable(new double[]{0,2,4},2);close(1,t.getValue(.5),1e-12);close(-2,t.getValue(-1),1e-12);close(6,t.getValue(3),1e-12);
        });
        checks.put("table/duplicate-scaling",()->{
            FunctionTable original=new FunctionTable(new double[]{0,2,4},2);FunctionTable copy=original.duplicate(2,3);
            close(6,copy.getValue(2),1e-12);close(2,original.getValue(1),1e-12);
        });
        checks.put("mna/current-sources-add-and-remove",()->{
            SubSystem s=new SubSystem(null,.05);VoltageState n=new VoltageState();s.addState(n);s.addComponent(new Resistor(n,null).setR(10));
            CurrentSource a=new CurrentSource("a",n,null).setCurrent(.1),b=new CurrentSource("b",n,null).setCurrent(.2);
            s.addComponent(a);s.addComponent(b);s.step();close(3,n.getU(),1e-10);
            s.removeComponent(b);s.step();close(1,n.getU(),1e-10);
            a.setCurrent(-.5);s.step();close(-5,n.getU(),1e-10);
        });
        checks.put("mna/current-source-nonfinite-rejected",()->fails(()->new CurrentSource("a").setCurrent(Double.NaN)));
        checks.put("mna/current-source-detached-quit",()->new CurrentSource("a").quitSubSystem());
        for(int j=1;j<=8;j++) {
            final int count=j;
            checks.put("mna/parallel-capacitor-equivalence-"+j,()->{
                SubSystem s=new SubSystem(null,.1);VoltageState n=new VoltageState();s.addState(n);n.setU(10);
                s.addComponent(new Resistor(n,null).setR(10));double total=0;
                for(int i=0;i<count;i++){Capacitor c=new Capacitor(n,null);double value=.3+i*.2;c.setC(value);total+=value;s.addComponent(c);}
                double expected=10;
                for(int i=0;i<100;i++){expected/=1+.1/(10*total);s.step();close(expected,n.getU(),1e-8);}
            });
        }
        for(int j=0;j<100;j++) {
            final int seed=j;
            checks.put("mna/random-divider-"+j,()->{
                Random random=new Random(98765+seed);double r1=Math.pow(10,random.nextDouble()*8-4),r2=Math.pow(10,random.nextDouble()*8-4),v=random.nextDouble()*100;
                SubSystem s=new SubSystem(null,.05);VoltageState a=new VoltageState(),b=new VoltageState();s.addState(a);s.addState(b);
                s.addComponent(new VoltageSource("v",a,null).setU(v));s.addComponent(new Resistor(a,b).setR(r1));s.addComponent(new Resistor(b,null).setR(r2));s.step();
                close(v*r2/(r1+r2),b.getU(),1e-7);
            });
        }
        return checks;
    }
    public static void main(String[] args) {
        int passed=0,failed=0;
        for(Map.Entry<String,Runnable> c:cases().entrySet()) {
            try {c.getValue().run();passed++;System.out.println("PASS "+c.getKey());}
            catch(Throwable error){failed++;System.out.println("FAIL "+c.getKey()+": "+error);}
        }
        System.out.println("RESULT passed="+passed+" failed="+failed);
        if(failed!=0)System.exit(1);
    }
}
