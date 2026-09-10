package mods.eln.devtest

import mods.eln.Eln
import mods.eln.misc.Direction as Side
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.misc.MnaConst
import mods.eln.sixnode.CreativePowerResistorElement
import mods.eln.transparentnode.battery.BatteryDescriptor
import mods.eln.transparentnode.battery.BatteryElement
import net.minecraft.core.registries.BuiltInRegistries
import kotlin.math.abs
import kotlin.math.max

/** Loaded current is measured at the actual resistor; total cell current includes self discharge. */
internal class NativeBatteryCampaign(private val fixture: NativeCampaignFixtures) {
    fun prepare() = with(fixture) {
        val descriptors = Eln.transparentNodeItem.subItemList.values.filterIsInstance<BatteryDescriptor>().sortedBy { it.name }
        check(descriptors.size == 7) { "New battery requires a reviewed native case: ${descriptors.map { it.name }}" }
        for (descriptor in descriptors) {
            val position = cell()
            val battery = placeMachine(descriptor.newItemStack(1), position) as BatteryElement
            battery.front = Side.ZP
            battery.reconnect()
            wire(position.west()); wire(position.east())
            ground(position.east(2)); ground(position.west(3))
            val loadPosition = position.west(2)
            val resistance = descriptor.electricalU * descriptor.electricalU / (descriptor.electricalStdP * .1)
            val key = BuiltInRegistries.ITEM.getKey(descriptor.parentItem).path
            var before = 0.0
            var beforeLife = 1.0
            var integratedJ = 0.0
            var observedSteps = 0
            var observer: IProcess? = null
            fun voltage() = battery.positiveLoad.voltage - battery.negativeLoad.voltage
            fun externalCurrent(): Double {
                if (node(loadPosition) == null) return 0.0
                val element = six(loadPosition) as CreativePowerResistorElement
                return abs(element.electricalComponentList.filterIsInstance<Resistor>().single().current)
            }
            fun startObserver() {
                check(observer == null)
                before = battery.batteryProcess.energy
                beforeLife = battery.batteryProcess.life
                integratedJ = 0.0
                observedSteps = 0
                // Added after the battery process. This observes accepted MNA steps; it never stamps the circuit.
                observer = IProcess { dt ->
                    val power = voltage() * battery.batteryProcess.dischargeCurrent
                    check(power.isFinite() && power >= -1e-6)
                    integratedJ += power * dt
                    observedSteps++
                }.also { Eln.simulator.addElectricalProcess(it) }
            }
            fun stopObserver() {
                observer?.let { Eln.simulator.removeElectricalProcess(it) }
                observer = null
            }
            fun measure(): Map<String, Any> = mapOf(
                "charge" to battery.batteryProcess.charge,
                "energyJ" to battery.batteryProcess.energy,
                "voltageV" to voltage(),
                "totalCurrentA" to battery.batteryProcess.dischargeCurrent,
                "internalCurrentA" to battery.dischargeResistor.current,
                "expectedInternalCurrentA" to voltage() / descriptor.electricalRp,
                "externalCurrentA" to externalCurrent(),
                "selfDischargeOhms" to battery.dischargeResistor.resistance,
                "internalDissipationW" to battery.dischargeResistor.power,
                "temperatureC" to battery.thermalLoad.temperatureCelsius,
                "integratedCellOutputJ" to integratedJ,
                "electricalSamples" to observedSteps
            )
            fun verify(open: Boolean): Map<String, Any> {
                check(observedSteps > 0) { "No accepted electrical observations" }
                val internal = battery.dischargeResistor.current
                NativeCampaignOracles.near(battery.dischargeResistor.resistance, descriptor.electricalRp, 1e-6, "Internal resistor")
                NativeCampaignOracles.near(internal, voltage() / descriptor.electricalRp,
                    1e-8 + abs(internal) * 1e-6, "Self discharge")
                val external = externalCurrent()
                val referenceLeak = (abs(battery.positiveLoad.voltage) + abs(battery.negativeLoad.voltage)) / MnaConst.highImpedance
                NativeCampaignOracles.batteryCurrent(battery.batteryProcess.dischargeCurrent, external, internal, referenceLeak)
                if (open) check(node(loadPosition) == null && external == 0.0)
                else check(external > 0.0) { "The declared battery load draws no current" }
                val lost = before - battery.batteryProcess.energy
                check(lost.isFinite() && lost >= -1e-5 && integratedJ >= 0.0)
                // Battery.energy uses a 50-bin voltage integral. Bound its quadrature error and
                // measured lifetime loss explicitly, rather than pretending the meter is an exact integral.
                val agingBound = before * ((beforeLife - battery.batteryProcess.life).coerceAtLeast(0.0) / beforeLife)
                NativeCampaignOracles.near(lost, integratedJ, 1.0 + integratedJ * .05 + agingBound,
                    "Stored energy / accepted terminal energy (50-bin meter)")
                return measure() + mapOf("beforeJ" to before, "storedEnergyLostJ" to lost,
                    "agingAllowanceJ" to agingBound, "externalOpen" to open,
                    "energyMeterTolerance" to "1 J + 5% quadrature allowance + measured lifetime loss")
            }
            steps += NativeCampaignFixtures.Step("battery-$key-discharge", "${descriptor.name}: external load plus internal self discharge",
                position, listOf(id(battery)), 40,
                begin = { load(loadPosition, resistance); startObserver() }, sample = ::measure,
                verify = { verify(false) }, end = ::stopObserver)
            steps += NativeCampaignFixtures.Step("battery-$key-open", "${descriptor.name}: no external load; account for internal self discharge",
                position, listOf(id(battery)), 20,
                begin = { world.removeBlock(loadPosition, false); startObserver() }, sample = ::measure,
                verify = { verify(true) }, end = ::stopObserver)
        }
    }
}
