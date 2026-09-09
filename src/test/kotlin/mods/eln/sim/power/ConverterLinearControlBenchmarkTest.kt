package mods.eln.sim.power

import mods.eln.Eln
import mods.eln.metrics.MetricsSubsystem
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.VoltageState
import kotlin.test.Test
import kotlin.test.assertTrue

class ConverterLinearControlBenchmarkTest {
    /** The same linear topology also runs against the base revision in the paired Actions job.
     * It has no references to classes introduced in this PR, so source is copied unchanged.
     */
    @Test fun ordinaryLinearNetworkControlMeasurement() {
        Eln.debugEnabled=false;Eln.mqttEnabled=false;Eln.simMetricsEnabled=false;MetricsSubsystem.shutdown()
        val root=RootSystem(.01,1)
        val nodes=List(96){VoltageState().also(root::addState)}
        root.addComponent(VoltageSource("baseline",nodes[0],null).setVoltage(300.0))
        for(i in 1..nodes.lastIndex) {
            root.addComponent(Resistor(nodes[i-1],nodes[i]).setResistance(.01+i*.0001))
            root.addComponent(Resistor(nodes[i],null).setResistance(10000.0))
        }
        root.step();repeat(100){root.step()}
        val samples=DoubleArray(1000){val t=System.nanoTime();root.step();(System.nanoTime()-t)/1e6}.sorted()
        println("CONVERTER_LINEAR_CONTROL p50_ms=${samples[499]} p95_ms=${samples[949]} p99_ms=${samples[989]}")
        assertTrue(nodes.last().voltage>290.0)
    }
}
