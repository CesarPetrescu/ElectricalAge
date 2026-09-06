package mods.eln.sim.mna;

import mods.eln.misc.Profiler;
import mods.eln.sim.support.SimLog;
import mods.eln.sim.mna.component.Component;
import mods.eln.sim.mna.component.Delay;
import mods.eln.sim.mna.component.Resistor;
import mods.eln.sim.mna.component.VoltageSource;
import mods.eln.sim.mna.misc.IDestructor;
import mods.eln.sim.mna.misc.ISubSystemProcessFlush;
import mods.eln.sim.mna.misc.ISubSystemProcessI;
import mods.eln.sim.mna.misc.Matrix;
import mods.eln.sim.mna.state.State;
import mods.eln.sim.mna.state.VoltageState;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class SubSystem {
    public ArrayList<Component> component = new ArrayList<Component>();
    public List<State> states = new ArrayList<State>();
    public LinkedList<IDestructor> breakDestructor = new LinkedList<IDestructor>();
    public ArrayList<SubSystem> interSystemConnectivity = new ArrayList<SubSystem>();
    ArrayList<ISubSystemProcessI> processI = new ArrayList<ISubSystemProcessI>();
    State[] statesTab;

    RootSystem root;

    double dt;
    boolean matrixValid = false;

    int stateCount;
    Matrix A;
    //Matrix I;
    boolean singularMatrix;

    double[][] AInvdata;
    double[] Idata;

    double[] XtempData;

    boolean breaked = false;
    private boolean calculated;

    ArrayList<ISubSystemProcessFlush> processF = new ArrayList<ISubSystemProcessFlush>();

    public RootSystem getRoot() {
        return root;
    }

    public SubSystem(RootSystem root, double dt) {
        if (!Double.isFinite(dt) || dt <= 0) throw new IllegalArgumentException("Timestep must be finite and positive");
        this.dt = dt;
        this.root = root;
    }

    public void invalidate() {
        matrixValid = false;
        calculated = false;
    }

    public void addComponent(Component c) {
        requireActive();
        if (c == null || component.contains(c) || c.getSubSystem() != null)
            throw new IllegalArgumentException("Component already owned or null");
        component.add(c);
        try { c.addedTo(this); }
        catch (RuntimeException failure) {
            component.remove(c);
            c.quitSubSystem();
            invalidate();
            throw failure;
        }
        invalidate();
    }

    public void addState(State s) {
        requireActive();
        if (s == null || states.contains(s) || s.getSubSystem() != null)
            throw new IllegalArgumentException("State already owned or null");
        states.add(s);
        s.addedTo(this);
        invalidate();
    }

    public void removeComponent(Component c) {
        if (!component.remove(c)) return;
        c.quitSubSystem();
        invalidate();
    }

    public void removeState(State s) {
        if (!states.remove(s)) return;
        s.quitSubSystem();
        invalidate();
    }

	/*public void removeAll() {
		for (Component c : component) {
			c.disconnectFromSubSystem();
		}
		for (State s : states) {
			s.disconnectFromSubSystem();
		}	
		invalidate();
	}*/

    public void removeProcess(ISubSystemProcessI p) {
        processI.remove(p);
        invalidate();
    }

    public void addComponent(Iterable<Component> i) {
        for (Component c : i) {
            addComponent(c);
        }
    }

    public void addState(Iterable<State> i) {
        for (State s : i) {
            addState(s);
        }
    }

    public void addProcess(ISubSystemProcessI p) {
        requireActive();
        if (p == null) throw new IllegalArgumentException("Null subsystem process");
        if (!processI.contains(p)) { processI.add(p); calculated = false; }
    }

    //double[][] getDataRef()

    public void generateMatrix() {
        requireActive();
        // A failed explicit rebuild must not leave a previous calculation publishable.
        invalidate();
        stateCount = states.size();

        // Profiler p = new Profiler();
        // p.add("Inversse with " + stateCount + " state : ");

        A = new Matrix(stateCount, stateCount);
        //Adata = ((Array2DRowRealMatrix) A).getDataRef();
        // X = MatrixUtils.createRealMatrix(stateCount, 1); Xdata =
        // ((Array2DRowRealMatrix)X).getDataRef();
        //I = MatrixUtils.createRealMatrix(stateCount, 1);
        //Idata = ((Array2DRowRealMatrix) I).getDataRef();
        Idata = new double[stateCount];
        XtempData = new double[stateCount];
        {
            int idx = 0;
            for (State s : states) {
                s.setId(idx++);
            }
        }

        for (Component c : component) {
            c.applyTo(this);
        }

        //	org.apache.commons.math3.linear.

        try {
            Matrix inverse = A.getInverse();
            AInvdata = inverse.getData();
            singularMatrix = false;
        } catch (ArithmeticException | IllegalArgumentException failure) {
            singularMatrix = true;
            AInvdata = null;
            throw new ArithmeticException("MNA matrix cannot be solved: " + failure.getMessage());
        }

        statesTab = new State[stateCount];
        statesTab = states.toArray(statesTab);

        matrixValid = true;

        // p.stop();
        // SimLog.println(p);
    }

    private void requireActive() {
        if (breaked) throw new IllegalStateException("Subsystem has been disposed");
    }

    private int stateIndex(State state) {
        if (state.getSubSystem() != this || state.getId() < 0 || state.getId() >= stateCount
                || state.getId() >= states.size() || states.get(state.getId()) != state)
            throw new IllegalArgumentException("State does not belong to this matrix");
        return state.getId();
    }

    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Nonfinite MNA value");
        return value;
    }

    public void addToA(State a, State b, double value) {
        finite(value);
        if (a == null || b == null) return;
        A.addToEntry(stateIndex(a), stateIndex(b), value);
    }

    public void addToI(State state, double value) {
        finite(value);
        if (state == null) return;
        int index = stateIndex(state);
        Idata[index] = finite(Idata[index] + value);
    }

	/*
	 * public void pushX(){
	 * 
	 * }
	 */
	/*
	 * public void popX(){
	 * 
	 * }
	 */

    public void step() {
        stepCalc();
        stepFlush();
    }

    public void stepCalc() {
        requireActive();
        calculated = false;
        if (!matrixValid) generateMatrix();
        java.util.Arrays.fill(Idata, 0);
        for (ISubSystemProcessI process : processI) process.simProcessI(this);
        for (int row = 0; row < stateCount; row++) {
            double sum = 0;
            for (int column = 0; column < stateCount; column++)
                sum += AInvdata[row][column] * Idata[column];
            XtempData[row] = finite(sum);
        }
        calculated = true;
    }

    public double solve(State pin) {
        requireActive();
        if (pin == null) return 0;
        if (!matrixValid) generateMatrix();
        int row = stateIndex(pin);
        java.util.Arrays.fill(Idata, 0);
        for (ISubSystemProcessI process : processI) process.simProcessI(this);
        double sum = 0;
        for (int column = 0; column < stateCount; column++)
            sum += AInvdata[row][column] * Idata[column];
        return finite(sum);
    }

    /** No state is published until every equation has a finite solution. */
    public void stepFlush() {
        requireActive();
        if (!calculated) throw new IllegalStateException("No successful calculation to publish");
        for (int index = 0; index < stateCount; index++) finite(XtempData[index]);
        for (int index = 0; index < stateCount; index++) statesTab[index].state = XtempData[index];
        calculated = false;
        for (ISubSystemProcessFlush process : processF) process.simProcessFlush();
    }

    public static void main(String[] args) {
//		SubSystem s = new SubSystem(null, 0.1);
//		VoltageState n1, n2;
//		VoltageSource u1;
//		Resistor r1, r2;
//
//		s.addState(n1 = new VoltageState());
//		s.addState(n2 = new VoltageState());
//
//		//s.addComponent((u1 = new VoltageSource()).setU(1).connectTo(n1, null));
//
//		s.addComponent((r1 = new Resistor()).setR(10).connectTo(n1, n2));
//		s.addComponent((r2 = new Resistor()).setR(20).connectTo(n2, null));
//
//		s.step();
//		s.step();

        SubSystem s = new SubSystem(null, 0.1);
        VoltageState n1, n2, n3, n4, n5;
        VoltageSource u1;
        Resistor r1, r2, r3;
        Delay d1, d2;

        s.addState(n1 = new VoltageState());
        s.addState(n2 = new VoltageState());
        s.addState(n3 = new VoltageState());
        //	s.addState(n4 = new VoltageState());
        //	s.addState(n5 = new VoltageState());

        s.addComponent((u1 = new VoltageSource("")).setU(1).connectTo(n1, null));

        s.addComponent((r1 = new Resistor()).setR(10).connectTo(n1, n2));
        s.addComponent((d1 = new Delay()).set(1).connectTo(n2, n3));
        s.addComponent((r2 = new Resistor()).setR(10).connectTo(n3, null));
        //s.addComponent((d2 = new Delay()).set(10).connectTo(n4, n5));
        //s.addComponent((r2 = new Resistor()).setR(10).connectTo(n5, null));

        for (int idx = 0; idx < 100; idx++) {
            s.step();
        }

        System.out.println("END");

        s.step();
        s.step();
        s.step();
    }

    public boolean containe(State state) {
        return states.contains(state);
    }

    public void setX(State s, double value) {
        s.state = value;
    }

    public double getX(State s) {
        return s.state;
    }

    public double getXSafe(State bPin) {
        return bPin == null ? 0 : getX(bPin);
    }

    public boolean breakSystem() {
        if (breaked) return false;
        while (!breakDestructor.isEmpty()) {
            breakDestructor.pop().destruct();
        }

        for (Component c : new ArrayList<>(component)) {
            c.quitSubSystem();
        }
        for (State s : states) {
            s.quitSubSystem();
        }

        if (root != null) {
            for (Component c : component) {
                c.returnToRootSystem(root);
            }
            for (State s : states) {
                s.returnToRootSystem(root);
            }
        }
        if (root != null) root.systems.remove(this);

        invalidate();

        breaked = true;
        component.clear(); states.clear(); processI.clear(); processF.clear();
        interSystemConnectivity.clear(); statesTab = null;
        A = null; AInvdata = null; Idata = null; XtempData = null;
        return true;
    }

    public void addProcess(ISubSystemProcessFlush p) {
        requireActive();
        if (p == null) throw new IllegalArgumentException("Null subsystem process");
        if (!processF.contains(p)) { processF.add(p); calculated = false; }
    }

    public void removeProcess(ISubSystemProcessFlush p) {
        processF.remove(p);
    }

    public double getDt() {
        return dt;
    }

    static public class Th {
        public double R, U;

        public boolean isHighImpedance() {
            return R > 1e8;
        }
    }

    public Th getTh(State d, VoltageSource voltageSource) {
        Th th = new Th();
        double originalU = d.state;

        double aU = 10;
        voltageSource.setU(aU);
        double aI = solve(voltageSource.getCurrentState());

        double bU = 5;
        voltageSource.setU(bU);
        double bI = solve(voltageSource.getCurrentState());

        double Rth = (aU - bU) / (bI - aI);
        double Uth;
        //if(Double.isInfinite(d.Rth)) d.Rth = Double.MAX_VALUE;
        if (Rth > 10000000000000000000.0 || Rth < 0) {
            Uth = 0;
            Rth = 10000000000000000000.0;
        } else {
            Uth = aU + Rth * aI;
        }
        voltageSource.setU(originalU);

        th.R = Rth;
        th.U = Uth;
        return th;
    }
}
