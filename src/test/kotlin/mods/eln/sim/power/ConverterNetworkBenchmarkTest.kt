package mods.eln.sim.power

import mods.eln.Eln
import mods.eln.metrics.MetricsSubsystem
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.VoltageState
import kotlin.test.Test
import kotlin.test.assertTrue

/** Separate from correctness tests; prints measured times, sizes and heap, never canned values.
 * The converter fixture and acceptance checks are identical to the production-controller regressions.
 */
class ConverterNetworkBenchmarkTest {
    @Test fun measuresGrowingParallelNetworksAndLoadChanges() {
        Eln.debugEnabled=false; Eln.mqttEnabled=false; Eln.simMetricsEnabled=false; MetricsSubsystem.shutdown()
        for(count in listOf(1,4,16,64)) {
            val rig=ConverterBusFixture(count,true)
            // A branching feeder adds states that cannot all be line-folded away.
            repeat(count) { i ->
                val a=rig.pin(); val b=rig.pin()
                rig.resistor(rig.bus,a,.01+i*.0001);rig.resistor(a,b,.02)
                rig.resistor(b,rig.loadPin,.03);rig.resistor(a,rig.loadPin,.04)
            }
            rig.load.resistance=10000.0/count
            val start=System.nanoTime();rig.step(1);val startup=(System.nanoTime()-start)/1e6
            rig.step(30)
            val samples=DoubleArray(250) { val t=System.nanoTime();rig.step(1);(System.nanoTime()-t)/1e6 }.sorted()
            val overloadStart=System.nanoTime();rig.load.resistance=40.0/count;rig.step(1)
            val overload=(System.nanoTime()-overloadStart)/1e6
            val limited=rig.bus.voltage
            val recoverStart=System.nanoTime();rig.load.resistance=10000.0/count;rig.step(1)
            val recovery=(System.nanoTime()-recoverStart)/1e6
            val reconnectStart=System.nanoTime();rig.detachLoad();rig.step(1);rig.attachLoad(10000.0/count);rig.step(1)
            val reconnect=(System.nanoTime()-reconnectStart)/1e6
            val maxMatrix=rig.root.systems.maxOf { it.captureDebugSnapshot().conductanceMatrix.size }
            val heap=(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1048576
            println("CONVERTER_NETWORK_BENCH count=$count maxMatrix=$maxMatrix startup_ms=$startup p50_ms=${samples[124]} p95_ms=${samples[237]} p99_ms=${samples[247]} overload_ms=$overload recovery_ms=$recovery reconnect_ms=$reconnect heapUsedMiB=$heap limitedV=$limited")
            assertTrue(limited in 500.0..2000.0,"Overload was not an accepted current-limited state")
            assertTrue(samples[237]<50.0,"This circuit alone exceeds a Minecraft tick: count=$count p95=${samples[237]} ms")
            assertTrue(overload<2000.0 && recovery<2000.0 && reconnect<2000.0,"Unacceptable multi-second topology/control stall for $count converters")
        }
    }

}
