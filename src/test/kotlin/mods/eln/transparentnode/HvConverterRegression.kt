package mods.eln.transparentnode

import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.*
import mods.eln.sim.mna.state.State
import mods.eln.sim.mna.state.VoltageState
import kotlin.math.abs
import kotlin.math.max

/** Actual OneWayDcDcProcess + RootSystem, not a parallel algebra-only implementation. */
object HvConverterRegression {
    private var assertions = 0
    private fun checkThat(ok: Boolean, text: String) { assertions++; check(ok) { text } }
    private fun close(expected: Double, actual: Double, tolerance: Double = 1e-6, label: String) {
        checkThat(actual.isFinite() && abs(expected - actual) <= tolerance, "$label: expected=$expected actual=$actual")
    }

    class Device(val root: RootSystem, override val mode: OneWayDcDcMode,
                 var requestedRatio: Double, override val isolated: Boolean = false,
                 primaryOhms: Double = 0.0, secondaryOhms: Double = 0.0) : OneWayDcDcAccess {
        override val primaryLoad = VoltageState()
        override val secondaryLoad = VoltageState()
        override val primaryReferenceLoad = VoltageState()
        override val secondaryReferenceLoad = VoltageState()
        override val primaryConversionLoad = if (primaryOhms > 0) VoltageState() else primaryLoad
        override val secondaryConversionLoad = if (secondaryOhms > 0) VoltageState() else secondaryLoad
        val primaryWinding = if (primaryOhms > 0) Resistor(primaryLoad, primaryConversionLoad).setResistance(primaryOhms) else null
        val secondaryWinding = if (secondaryOhms > 0) Resistor(secondaryConversionLoad, secondaryLoad).setResistance(secondaryOhms) else null
        override val inputSink = VoltageSource("input",primaryConversionLoad,if (isolated) primaryReferenceLoad else null)
        override val outputSource = VoltageSource("output",secondaryConversionLoad,if (isolated) secondaryReferenceLoad else null)
        override var requestedOutputVoltage: Double? = null
        override var populated = true
        override var protectedMode = true
        override var primaryMeltCurrent = 10.0
        override var secondaryMeltCurrent = 10.0
        override var activeRatio = 1.0
        override var movedPower = 0.0
        override var converterStatus = OneWayDcDcStatus.UNCONFIGURED
        override fun computeRatio() = requestedRatio
        val process = OneWayDcDcProcess(this)
        init {
            root.addState(primaryLoad);root.addState(secondaryLoad)
            if (primaryConversionLoad !== primaryLoad) root.addState(primaryConversionLoad)
            if (secondaryConversionLoad !== secondaryLoad) root.addState(secondaryConversionLoad)
            primaryWinding?.let { root.addComponent(it) }
            secondaryWinding?.let { root.addComponent(it) }
            if(isolated) {root.addState(primaryReferenceLoad);root.addState(secondaryReferenceLoad)}
            root.addComponent(inputSink);root.addComponent(outputSource);root.addProcess(process)
        }
        fun supply(volts: Double, resistance: Double=0.1): VoltageSource {
            val n=VoltageState();root.addState(n)
            val s=VoltageSource("external",n,if(isolated) primaryReferenceLoad else null).setVoltage(volts)
            root.addComponent(s);root.addComponent(Resistor(n,primaryLoad).setResistance(resistance));return s
        }
        fun load(ohms: Double): Resistor {
            val r=Resistor(secondaryLoad,if(isolated) secondaryReferenceLoad else null).setResistance(ohms)
            root.addComponent(r);return r
        }
        fun reference(state: State,volts: Double) { root.addComponent(VoltageSource("reference",state,null).setVoltage(volts)) }
        fun balanced(label: String) {
            close(-inputSink.power,outputSource.power,1e-6*max(1.0,abs(inputSink.power)),label)
        }
    }

    @JvmStatic fun runAll(): Int {
        assertions=0
        for(mode in listOf(OneWayDcDcMode.FIXED,OneWayDcDcMode.BOOST,OneWayDcDcMode.BOOST_BUCK)) {
            for(ratio in listOf(1.0,4.0,16.0,64.0,256.0)) {
                val root=RootSystem(0.05,4)
                val d=Device(root,mode,ratio);d.supply(50.0);d.load((50*ratio)*(50*ratio)/100)
                repeat(5) {root.step();d.balanced("$mode $ratio power")}
                checkThat(d.outputSource.power>98.0,"$mode $ratio delivers power")
                checkThat(d.converterStatus in setOf(OneWayDcDcStatus.RUNNING, OneWayDcDcStatus.LIMITED),
                    "$mode $ratio must operate (at maximum gain sag may limit): ${d.converterStatus}")
                if(mode==OneWayDcDcMode.FIXED) close(d.primaryLoad.voltage*ratio,d.secondaryLoad.voltage,label="fixed ratio under load")
            }
        }
        for(ratio in listOf(1.0/256,1.0/64,1.0/16,0.25,1.0)) {
            val root=RootSystem(0.05,2); val d=Device(root,OneWayDcDcMode.BUCK,ratio)
            d.secondaryMeltCurrent=100.0;d.supply(800.0);d.load((800*ratio)*(800*ratio)/100)
            root.step();d.balanced("buck $ratio power");checkThat(d.outputSource.power>99,"buck $ratio output=${d.outputSource.power} status=${d.converterStatus} vin=${d.inputSink.voltage} vout=${d.outputSource.voltage}")
        }
        protectedCurrentLimit()
        prechargedOutput()
        removedInput()
        fixedReverseBlocking()
        isolatedReferences()
        invalidNetwork()
        sourceFreeLoop()
        explicitWindingLosses()
        poweredChain()
        seededNetworks()
        println("HV runtime converter regression: $assertions assertions passed")
        return assertions
    }

    private fun explicitWindingLosses() {
        val root = RootSystem(0.05, 4)
        val d = Device(root, OneWayDcDcMode.BOOST, 16.0, primaryOhms = .2, secondaryOhms = 5.0)
        d.requestedOutputVoltage = 800.0
        d.supply(50.0, .01)
        val load = d.load(6400.0)
        repeat(5) { root.step() }
        d.balanced("explicit winding: internal conversion power")
        close(800.0, d.outputSource.voltage, label = "internal voltage target")
        checkThat(d.secondaryLoad.voltage < 800.0 && d.secondaryLoad.voltage > 790.0,
            "external terminal includes secondary copper drop")
        val terminalInputPower = d.primaryLoad.voltage * -d.inputSink.current
        close(d.primaryWinding!!.power + d.secondaryWinding!!.power,
            terminalInputPower - load.power, 1e-6, "measured terminal loss equals BOTH winding resistors")
        d.primaryWinding.setResistance(.8)
        repeat(5) { root.step() }
        d.balanced("temperature/length resistance update")
        close(d.primaryWinding.power + d.secondaryWinding.power,
            d.primaryLoad.voltage * -d.inputSink.current - load.power, 1e-6,
            "changed winding resistance remains energy consistent")
    }

    private fun poweredChain() {
        val root = RootSystem(0.05, 4)
        val up = Device(root, OneWayDcDcMode.FIXED, 64.0)
        val down = Device(root, OneWayDcDcMode.FIXED, 1.0 / 16.0)
        val source = up.supply(50.0, .05)
        val transmission = Resistor(up.secondaryLoad, down.primaryLoad).setResistance(10.0)
        root.addComponent(transmission)
        val load = down.load(400.0)
        repeat(10) { root.step() }
        up.balanced("powered chain step-up")
        down.balanced("powered chain step-down")
        checkThat(load.power > 90.0, "powered 50 -> 3200 -> 200 chain delivers useful power")
        close(up.outputSource.power, -down.inputSink.power + transmission.power,
            1e-5, "transmission loss appears in actual resistor")
        source.isEnabled = false
        repeat(30) { root.step() }
        close(0.0, load.power, 1e-6, "chain stops after supply removal")
    }

    private fun seededNetworks() {
        val random = java.util.Random(0xE1A20260909L)
        repeat(250) { index ->
            val input = 20.0 + random.nextDouble() * 780.0
            val ratio = kotlin.math.exp(-3.0 + random.nextDouble() * 6.0)
            val watts = 1.0 + random.nextDouble() * 50.0
            val root = RootSystem(.05, 2)
            val d = Device(root, OneWayDcDcMode.BOOST_BUCK, ratio)
            d.primaryMeltCurrent = 100.0
            d.secondaryMeltCurrent = 100.0
            d.supply(input, .001 + random.nextDouble() * .05)
            d.load(input * input * ratio * ratio / watts)
            repeat(3) { root.step() }
            d.balanced("seeded regulated network $index")
            checkThat(d.outputSource.power > 0.0, "seeded network $index powers its load")
            checkThat(-d.inputSink.current in 0.0..100.00001 && d.outputSource.current in 0.0..100.00001,
                "seeded network $index observes actual limits")
        }
    }

    private fun protectedCurrentLimit() {
        val root=RootSystem(0.05,4);val d=Device(root,OneWayDcDcMode.BOOST_BUCK,16.0)
        d.primaryMeltCurrent=2.0;d.secondaryMeltCurrent=0.08;d.supply(50.0);d.load(6400.0)
        repeat(10){root.step();d.balanced("limited power")}
        checkThat(-d.inputSink.current<=2.00001,"actual input current limit")
        checkThat(d.outputSource.current<=0.080001,"actual output current limit")
        checkThat(d.outputSource.power>0,"limiting is not just permanent shutdown")
        checkThat(d.converterStatus==OneWayDcDcStatus.LIMITED,"limit diagnostics")
    }

    private fun prechargedOutput() {
        val root=RootSystem(0.05,4);val d=Device(root,OneWayDcDcMode.BOOST,4.0)
        d.supply(50.0);d.load(10000.0)
        val cap=Capacitor(d.secondaryLoad,null);cap.setCoulombs(0.01);root.addComponent(cap)
        d.secondaryLoad.voltage=800.0
        val initial=cap.energy
        root.step()
        close(0.0,d.outputSource.current,0.0,"no reverse transfer")
        checkThat(cap.energy in 0.0..initial && cap.energy>initial*0.9,"output not shorted or erased")
        checkThat(d.converterStatus==OneWayDcDcStatus.OUTPUT_HIGH,"precharged reason")
    }

    private fun removedInput() {
        val root=RootSystem(0.05,4);val d=Device(root,OneWayDcDcMode.BOOST,16.0)
        val source=d.supply(50.0);d.load(6400.0)
        repeat(4){root.step()};checkThat(d.outputSource.power>90,"powered before disconnect")
        source.isEnabled=false
        repeat(30){root.step();close(0.0,d.outputSource.power,1e-7,"no phantom power after supply removal")}
        source.isEnabled=true
        repeat(5){root.step()};checkThat(d.outputSource.power>90,"recovers after reconnect")
    }

    private fun fixedReverseBlocking() {
        val root=RootSystem(0.05,4);val d=Device(root,OneWayDcDcMode.FIXED,4.0)
        d.supply(50.0);d.load(10000.0)
        val n=VoltageState();root.addState(n)
        root.addComponent(VoltageSource("outputSupply",n,null).setVoltage(800.0))
        root.addComponent(Resistor(n,d.secondaryLoad).setResistance(0.1))
        root.step();close(0.0,d.inputSink.current,0.0,"fixed one-way blocks reverse input current")
        close(0.0,d.outputSource.current,0.0,"fixed one-way blocks reverse output current")
    }

    private fun isolatedReferences() {
        val root=RootSystem(0.05,4);val d=Device(root,OneWayDcDcMode.ISOLATION,1.0,true)
        d.reference(d.primaryReferenceLoad,1500.0);d.reference(d.secondaryReferenceLoad,-2000.0)
        d.supply(50.0);d.load(100.0)
        repeat(3){root.step()}
        close(1500.0,d.primaryReferenceLoad.voltage,label="primary reference independent")
        close(-2000.0,d.secondaryReferenceLoad.voltage,label="secondary reference independent")
        close(d.primaryLoad.voltage-1500,d.secondaryLoad.voltage+2000,label="isolated ratio is differential")
        d.balanced("isolated power uses differential voltages")
    }

    private fun invalidNetwork() {
        val root=RootSystem(0.05,4);val d=Device(root,OneWayDcDcMode.BOOST,Double.NaN)
        d.supply(50.0);d.load(6400.0);root.step()
        close(0.0,d.outputSource.power,0.0,"invalid ratio fails open")
        checkThat(d.converterStatus==OneWayDcDcStatus.UNCONFIGURED,"invalid ratio diagnostic")
    }

    private fun sourceFreeLoop() {
        val root=RootSystem(0.05,2)
        val a=Device(root,OneWayDcDcMode.FIXED,4.0)
        val b=Device(root,OneWayDcDcMode.FIXED,0.25)
        a.protectedMode=false;b.protectedMode=false
        a.load(10000.0);b.load(10000.0)
        root.addComponent(Resistor(a.secondaryLoad,b.primaryLoad).setResistance(.1))
        root.addComponent(Resistor(b.secondaryLoad,a.primaryLoad).setResistance(.1))
        a.inputSink.setVoltage(50.0);a.outputSource.setVoltage(200.0)
        b.inputSink.setVoltage(200.0);b.outputSource.setVoltage(50.0)
        a.primaryLoad.voltage=50.0;a.secondaryLoad.voltage=200.0
        b.primaryLoad.voltage=200.0;b.secondaryLoad.voltage=50.0
        // This electrically unrelated circuit must not be tripped along with the failed loop.
        val separate=Device(root,OneWayDcDcMode.BOOST,16.0)
        separate.supply(50.0);separate.load(6400.0)
        repeat(5) {root.step()}
        close(0.0,a.outputSource.power,1e-7,"source-free loop A fails open")
        close(0.0,b.outputSource.power,1e-7,"source-free loop B fails open")
        checkThat(separate.outputSource.power>90,"unrelated powered network stays online")
    }
}

fun main() { HvConverterRegression.runAll() }
