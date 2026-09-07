package mods.eln.devtest

import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.node.six.SixNode
import mods.eln.sim.*
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.CurrentSource
import mods.eln.sim.mna.component.Resistor
import mods.eln.sixnode.electricalcable.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

/** Every registered utility descriptor, including damaged variants, with real MNA and heat sampling.
 * World placement/interruption is checked separately by WireThermalSmokeTest.
 */
object WireThermalChecks {
    @JvmStatic fun run(world: ServerLevel, restart: Boolean): Int {
        val suite = if (restart) "wire-thermal-restart" else "wire-thermal"
        val report = ContractReport(suite)
        report.write(false)
        val traces = StringBuilder("descriptor,seconds,temperature_C,current_per_core_A,heating_W,stored_J,cooling_J,phase_J\n")
        for (d in UtilityCableDescriptor.allDescriptors()) {
            val id = "utility:${d.parentItemDamage}:${d.sizeLabel}:${d.conductorCount}:${d.material}:${d.insulated}:${d.melted}"
            report.test(id, "conserved-heating-cooling-and-fusion") {
                val cable = UtilityCableElement(SixNode().apply { coordinate = Coordinate(770,65,768,world) }, Direction.YN,d)
                cable.initialize()
                val root = RootSystem(.01,1)
                val loads = cable.electricalLoadList.filterIsInstance<ElectricalLoad>()
                val current = 50.0 * d.conductorAreaMm2 / .1288
                for (load in loads) {
                    val a=ElectricalLoad().apply { serialResistance=0.0 }
                    val b=ElectricalLoad().apply { serialResistance=0.0 }
                    listOf(a,load,b).forEach(root::addState)
                    root.addComponent(CurrentSource("thermal",a,null).setCurrent(current))
                    root.addComponent(Resistor(b,null).apply { resistance=.001 })
                    root.addComponent(ElectricalConnection(a,load));root.addComponent(ElectricalConnection(load,b))
                }
                root.generate()
                val thermal=cable.thermalLoad
                val start=thermal.storedJoules
                var cooling=0.0;var ticks=0
                while (!thermal.failed && ticks<200) {
                    loads.forEach { it.serialResistance=d.resistanceOhms(celsius=thermal.absoluteCelsius)/2 }
                    repeat(5) { root.step();cable.heating.sample.process(.01) }
                    thermal.PcTemp=0.0
                    cable.thermalSlowProcessList.forEach { it.process(.05) }
                    val removed=thermal.temperatureCelsius/thermal.Rp*.05
                    cooling+=removed
                    thermal.integrateEnergy(thermal.PcTemp*.05-removed)
                    check(abs(cable.heating.lastWatts-loads.sumOf { it.serialPower }) < maxOf(.0001,cable.heating.lastWatts*1e-8))
                    if (ticks%5==0) traces.append("$id,${ticks*.05},${thermal.absoluteCelsius},$current,${cable.heating.lastWatts},${thermal.storedJoules},$cooling,${thermal.phaseJoules}\n")
                    if (restart && ticks==10) {
                        val before=thermal.storedJoules;val nbt=CompoundTag();thermal.writeToNBT(nbt,"")
                        thermal.temperatureCelsius=0.0;thermal.readFromNBT(nbt,"")
                        // Existing saves store temperature as float. Account for that bounded quantization.
                        cooling += before-thermal.storedJoules
                        check(abs(before-thermal.storedJoules)<maxOf(.1,abs(before)*1e-6))
                    }
                    ticks++
                }
                check(thermal.failed) { "Severe overload plateaued at ${thermal.absoluteCelsius} C" }
                val accounted=thermal.storedJoules-start+cooling
                check(abs(cable.heating.deliveredJoules-accounted)<maxOf(1e-6,abs(accounted)*1e-8)) { "Heat disappeared" }
                check(cable.heating.pendingJoules==0.0)
            }
            report.test(id,"power-off-cools-without-repairing-descriptor") {
                val thermal=WireThermalLoad("test",WireThermalPhysics(d.material,d.totalConductorAreaMm2))
                thermal.temperatureCelsius=200.0
                val start=thermal.storedJoules;var removed=0.0
                repeat(400) {
                    thermal.updateProperties(20.0,20.0,d.insulated)
                    val energy=thermal.temperatureCelsius/thermal.Rp*.05
                    removed+=energy;thermal.integrateEnergy(-energy)
                }
                check(thermal.absoluteCelsius in 20.0..<220.0)
                check(abs(start-thermal.storedJoules-removed)<maxOf(1e-6,abs(start)*1e-8))
            }
            if (d.poleEligible) report.test(id,"span-length-heat-disconnect-and-restored-resistance") {
                val a=ElectricalLoad().apply { serialResistance=.01 }
                val b=ElectricalLoad().apply { serialResistance=.01 }
                val connection=mods.eln.gridnode.WireSpanConnection(a,b,d.resistanceOhms(32.0))
                val root=RootSystem(.01,1)
                root.addState(a);root.addState(b);root.addComponent(connection)
                root.addComponent(mods.eln.sim.mna.component.VoltageSource("span",a,null).setVoltage(10.0))
                root.addComponent(Resistor(b,null).apply { resistance=10.0 })
                val span=mods.eln.gridnode.WireSpanThermal(d,32.0)
                val existing=mods.eln.Eln.simulator.electricalProcessList.toSet()
                var joules=0.0
                try {
                    span.connect(connection,{20.0},{ error("Unexpected span failure") })
                    val sampler=(mods.eln.Eln.simulator.electricalProcessList.toSet()-existing).single()
                    root.generate()
                    repeat(5) { root.step();joules+=connection.current*connection.current*connection.spanOhms*.01;sampler.process(.01) }
                } finally { span.disconnect() }
                check(mods.eln.Eln.simulator.electricalProcessList.toSet()==existing) { "Leaked span heating process" }
                check(joules>0 && abs(span.load.storedJoules-joules)<1e-7) { "Disconnect discarded pending span heat" }
                check(abs(span.load.physics.massKg-WirePhysics.massKg(d.material,d.conductorAreaMm2,32.0))<1e-10)
                span.insulationDamaged=true
                val nbt=CompoundTag();span.write(nbt)
                val restored=mods.eln.gridnode.WireSpanThermal(d,32.0);restored.read(nbt)
                try {
                    restored.connect(connection,{30.0},{ error("Unexpected restored span failure") })
                    check(restored.insulationDamaged)
                    check(abs(restored.load.storedJoules-joules)<.01)
                    check(abs(connection.resistance-d.resistanceOhms(32.0,restored.load.absoluteCelsius)-.02)<1e-8)
                } finally { restored.disconnect() }
                root.removeComponent(connection);connection.breakConnection()
                check(connection !in a.connectedComponents && connection !in b.connectedComponents)
                check(mods.eln.Eln.simulator.electricalProcessList.toSet()==existing)
            }
        }
        val path=Path.of("../../build/smoke-artifacts/$suite.csv")
        Files.createDirectories(path.parent);Files.writeString(path,traces)
        report.write(true)
        return report.failures
    }
}
