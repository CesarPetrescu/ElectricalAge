package mods.eln.sim.mna

import mods.eln.sim.mna.component.*
import mods.eln.sim.mna.state.VoltageState
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.*

class MatrixInverseUpdateTest {
    private class Circuit(val isolated: Boolean) {
        val system=SubSystem(null,.01)
        val nodes=List(28) { VoltageState().also(system::addState) }
        val ground=if(isolated) nodes.last() else null
        val source=SwitchableVoltageSource("reference").apply { connectTo(nodes[0],ground);voltage=3200.0;enabled=true }
        val load=Resistor(nodes[25],ground).setResistance(10000.0)
        val links=(1..25).map { Resistor(nodes[it-1],nodes[it]).setResistance(.01+it*.00031) }
        init {
            system.addComponent(source);system.addComponent(load);links.forEach(system::addComponent)
            system.addComponent(Resistor(nodes[26],nodes[10]).setResistance(.03))
            system.addComponent(Resistor(nodes[27],nodes[11]).setResistance(.04))
        }
    }
    @Test fun repeatedRankUpdatesMatchFreshFactorizationsAtBothPolaritiesAndReferences() {
        val random=Random(1009)
        for(isolated in listOf(false,true)) {
            val rig=Circuit(isolated);rig.system.step()
            repeat(100) { i ->
                val r=if(i%7==0)1e12 else 1.0+random.nextDouble()*10000
                val v=if(i%2==0)3200.0 else -3200.0
                val on=i%5!=0
                rig.load.resistance=r;rig.source.voltage=v;rig.source.enabled=on;rig.system.step()
                val fresh=Circuit(isolated)
                fresh.load.resistance=r;fresh.source.voltage=v;fresh.source.enabled=on;fresh.system.step()
                for(n in rig.nodes.indices) assertEquals(fresh.nodes[n].voltage,rig.nodes[n].voltage,1e-7)
                assertEquals(fresh.load.power,rig.load.power,1e-6+abs(fresh.load.power)*1e-10)
                assertEquals(fresh.source.current,rig.source.current,1e-7)
            }
            assertTrue(rig.system.updatedFactorizations>10,"The optimized path must actually execute")
            assertTrue(rig.system.fullFactorizations>=3,"Periodic full refactorization must execute")
        }
    }
    @Test fun contradictorySourceAfterCachedInverseStillFailsClosed() {
        val rig=Circuit(false);rig.system.step()
        repeat(8) { rig.load.resistance=100.0+it;rig.system.step() }
        assertTrue(rig.system.updatedFactorizations>0)
        rig.system.addComponent(VoltageSource("conflict",rig.nodes[0],null).setVoltage(1.0))
        rig.system.stepCalc()
        assertFalse(rig.system.hasValidStepSolution())
    }
}
