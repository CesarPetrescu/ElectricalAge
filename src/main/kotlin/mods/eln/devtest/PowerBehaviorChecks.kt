package mods.eln.devtest

import mods.eln.Eln
import mods.eln.mechanical.*
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.node.NodeBase
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.ElectricalConnection
import mods.eln.sixnode.electricalcable.*
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.State
import mods.eln.sim.mna.state.VoltageState
import mods.eln.transparentnode.battery.BatteryDescriptor
import mods.eln.transparentnode.battery.BatteryElement
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import kotlin.math.abs
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

/** Real registered machines and MNA circuits; invoked only by the GitHub/dev smoke server. */
object PowerBehaviorChecks {
    private const val DT = 0.01
    private fun id(e: TransparentNodeElement) = BuiltInRegistries.ITEM.getKey(e.descriptor!!.parentItem).toString()
    private fun node(world: ServerLevel) = TransparentNode().apply { coordinate = Coordinate(512, 65, 512, world) }
    private fun root(e: TransparentNodeElement): RootSystem = RootSystem(DT, 1).apply {
        e.electricalLoadList.forEach(::addState)
        e.electricalComponentList.forEach(::addComponent)
    }
    private fun resistor(root: RootSystem, a: State?, b: State?, resistance: Double) = Resistor(a, b).also {
        it.resistance = resistance; root.addComponent(it)
    }
    private fun reference(root: RootSystem, negative: State?) { if (negative != null) resistor(root, negative, null, 1e9) }
    private fun battery(world: ServerLevel, d: BatteryDescriptor) = BatteryElement(node(world), d).apply {
        d.applyTo(batteryProcess); d.applyTo(thermalLoad); d.applyTo(dischargeResistor)
        positiveLoad.serialResistance = d.electricalRs; negativeLoad.serialResistance = d.electricalRs
        batteryProcess.charge = .5
        voltageSource.voltage = batteryProcess.u
    }

    /** Actual source circuit -> 26 AWG -> return. Keeps thermal integration coupled to solved I²R. */
    private class FaultWire(val root: RootSystem, supply: ElectricalLoad, returnPin: State?) {
        val thermal=WireThermalLoad("fault",WireThermalPhysics(UtilityCableMaterial.COPPER,.1288))
        val wire=ElectricalLoad()
        val end=ElectricalLoad().apply { serialResistance=0.0 }
        val feed=ElectricalConnection(supply,wire)
        var interrupted=false
        var inputJoules=0.0
        var coolingJoules=0.0
        init {
            thermal.updateProperties(20.0,20.0,false)
            wire.serialResistance=WirePhysics.resistance(UtilityCableMaterial.COPPER,.1288)/2
            root.addState(wire);root.addState(end)
            root.addComponent(feed);root.addComponent(ElectricalConnection(wire,end))
            root.addComponent(Resistor(end,returnPin).apply { resistance=.000001 })
        }
        fun step() {
            val heating=wire.serialPower*DT
            inputJoules+=heating
            thermal.updateProperties(20.0,20.0,false)
            val cooling=thermal.temperatureCelsius/thermal.Rp*DT
            coolingJoules+=cooling
            thermal.integrateEnergy(heating-cooling)
            wire.serialResistance=WirePhysics.resistance(UtilityCableMaterial.COPPER,.1288,celsius=thermal.absoluteCelsius)/2
            if(thermal.failed && !interrupted) { root.removeComponent(feed);feed.breakConnection();interrupted=true }
        }
        fun checkBalance() {
            check(abs(inputJoules-thermal.storedJoules-coolingJoules)<maxOf(.001,inputJoules*1e-7))
            if(interrupted)check(abs(wire.current)<1e-6) { "Fault wire retained ghost current" }
        }
    }

    /** Runs immediately after real placement (all supported directions), before the simulator steps. */
    fun checkPlaced(node: NodeBase?) {
        val b = (node as? TransparentNode)?.element as? BatteryElement ?: return
        check(b.batteryProcess.life > 0.0 && b.batteryProcess.charge.isFinite())
        check(b.voltageSource.voltage == b.batteryProcess.u && b.voltageSource.voltage > 0.0) {
            "Charged battery must expose its saved voltage before the first electrical step"
        }
        check(b.dischargeResistor.resistance == b.descriptor.electricalRp) { "Self discharge configuration was overwritten" }
    }

    @JvmStatic fun run(world: ServerLevel, restart: Boolean): Int {
        val report = ContractReport(if (restart) "power-behavior-restart" else "power-behavior")
        report.write(false)
        val descriptors = Eln.transparentNodeItem.subItemList.values.filterNotNull()
        for (d in descriptors.filterIsInstance<GeneratorDescriptor>()) {
            val key = BuiltInRegistries.ITEM.getKey(d.parentItem).toString()
            report.test(key,"wire-short-pays-for-heat-and-depletes-finite-shaft-energy") {
                val g=GeneratorElement(node(world),d);g.shaft._mass=d.shaftMass;g.shaft.rads=d.nominalRads.toDouble()
                val r=root(g)
                val negative=if(d.bipolarTerminals)g.negativeLoad else null
                reference(r,negative)
                val fault=FaultWire(r,g.inputLoad,negative)
                val initial=g.shaft.energy
                // Match the live simulator: rebuild topology before invoking the regulator.
                r.addProcess(g.electricalProcess)
                r.generate()
                repeat(1200) {
                    r.step()
                    val before=g.shaft.energy
                    g.thermal.PcTemp=0.0;g.shaftProcess.process(DT)
                    check(abs((before-g.shaft.energy)/DT-g.electricalPowerSource.power-g.thermal.PcTemp)<.05)
                    check(abs(g.electricalPowerSource.current)<=d.regulatorCurrentLimit*1.001)
                    check(g.shaft.energy>=0 && g.shaft.energy<=initial+1e-6)
                    fault.step()
                }
                check(fault.inputJoules>0 && g.shaft.energy<initial)
                fault.checkBalance()
            }
            report.test(key, "small-load-and-stop-led-publication") {
                val g = GeneratorElement(node(world), d)
                g.electricalPowerSource.voltage = 10.0
                g.electricalPowerSource.currentState.state = -.1
                g.node!!.needPublish = false
                g.maybePublishE(g.electricalPowerSource.power)
                check(g.node!!.needPublish) { "Small load never published its LED state" }
                val bytes = ByteArrayOutputStream()
                g.networkSerialize(DataOutputStream(bytes))
                check(ByteBuffer.wrap(bytes.toByteArray()).getDouble(bytes.size() - 8) == 1.0)
                g.node!!.needPublish = false
                g.maybePublishE(0.0)
                check(g.node!!.needPublish) { "Stopped LED state not published" }
            }
            for (loadFraction in listOf(0.0, .1, .5, 1.0, 4.0)) report.test(key, "load-$loadFraction") {
                val g = GeneratorElement(node(world), d)
                g.shaft._mass = d.shaftMass
                val r = root(g)
                val negative = if (d.bipolarTerminals) g.negativeLoad else null
                reference(r, negative)
                val load = resistor(r, g.inputLoad, negative,
                    if (loadFraction == 0.0) 1e12 else d.nominalU.toDouble() * d.nominalU / (d.nominalP * loadFraction))
                r.generate()
                repeat(300) {
                    g.shaft.rads = d.nominalRads.toDouble() // external dynamometer supplies measured mechanical work
                    g.electricalProcess.process(DT); r.step()
                    check(abs(g.electricalPowerSource.current) <= d.regulatorCurrentLimit * 1.001)
                    val before = g.shaft.energy
                    g.thermal.PcTemp = 0.0; g.shaftProcess.process(DT)
                    val removed = (before - g.shaft.energy) / DT
                    check(abs(removed - g.electricalPowerSource.power - g.thermal.PcTemp) < .01) { "Generator energy balance" }
                }
                if (loadFraction in .1..1.0) {
                    check(load.power > d.nominalP * loadFraction * .75) { "No usable output: ${load.power} W" }
                }
                if (loadFraction == 0.0) check(abs(g.electricalPowerSource.current) < .001)
                g.shaft.rads = 0.0
                g.electricalProcess.process(DT); r.step()
                check(abs(load.power) < 1e-6) { "Stopped shaft still creates power" }
            }
            report.test(key, "battery-charge-and-backdrive") {
                val g = GeneratorElement(node(world), d); g.shaft._mass = d.shaftMass
                val b = battery(world, descriptors.filterIsInstance<BatteryDescriptor>().first { it.electricalU == 120.0 })
                val r = root(g)
                b.electricalLoadList.forEach(r::addState); b.electricalComponentList.forEach(r::addComponent)
                val negative = if (d.bipolarTerminals) g.negativeLoad else null
                resistor(r, g.inputLoad, b.positiveLoad, .5)
                resistor(r, negative, b.negativeLoad, .5)
                r.generate()
                val before = b.batteryProcess.Q
                repeat(400) {
                    g.shaft.rads = d.nominalRads * 140.0 / d.nominalU
                    g.electricalProcess.process(DT); r.step(); b.batteryProcess.process(DT); g.shaftProcess.process(DT)
                    check(abs(g.electricalPowerSource.current) <= d.regulatorCurrentLimit * 1.001)
                }
                check(b.batteryProcess.Q > before) { "Shaft generator did not charge battery" }
                val charged = b.batteryProcess.Q
                repeat(200) {
                    g.shaft.rads = 0.0
                    g.electricalProcess.process(DT); r.step(); b.batteryProcess.process(DT); g.shaftProcess.process(DT)
                    check(g.electricalPowerSource.current >= -d.regulatorCurrentLimit * 1.001)
                }
                check(b.batteryProcess.Q < charged && g.shaft.energy > 0.0) { "Battery cannot turn generator as inefficient motor" }
            }
        }
        for (d in descriptors.filterIsInstance<MotorDescriptor>()) {
            val key = BuiltInRegistries.ITEM.getKey(d.parentItem).toString()
            report.test(key, "small-load-and-stop-led-publication") {
                val m = MotorElement(node(world), d)
                m.powerSource.voltage = 10.0; m.powerSource.currentState.state = .1
                m.node!!.needPublish = false
                m.maybePublishP(-m.powerSource.power)
                check(m.node!!.needPublish) { "Small motor load never published its LED state" }
                m.node!!.needPublish = false
                m.powerSource.currentState.state = 0.0; m.maybePublishP(0.0)
                check(m.node!!.needPublish) { "Stopped motor LED state not published" }
            }
            report.test(key, "shaft-driven-motor-powers-load-and-stops") {
                val m = MotorElement(node(world), d); m.shaft._mass = d.shaftMass
                val r = root(m)
                val negative = if (d.bipolarTerminals) m.negativeLoad else null
                reference(r, negative)
                val load = resistor(r, m.wireLoad, negative, d.nominalU.toDouble() * d.nominalU / (d.nominalP * .1))
                r.generate()
                repeat(300) {
                    m.shaft.rads = d.nominalRads.toDouble()
                    m.electricalProcess.process(DT); r.step(); m.shaftProcess.process(DT)
                }
                check(load.power > d.nominalP * .075) { "Driven shaft motor produced no usable current: ${load.power} W" }
                val bytes = ByteArrayOutputStream()
                m.networkSerialize(DataOutputStream(bytes))
                check(ByteBuffer.wrap(bytes.toByteArray()).getDouble(bytes.size() - 16) < 0.0) { "Client was not told about reverse power" }
                m.shaft.rads = 0.0
                m.electricalProcess.process(DT); r.step()
                check(abs(load.power) < 1e-6) { "Stopped motor still exports power" }
            }
            for (offset in if (d.bipolarTerminals) listOf(0.0, 150.0, -150.0) else listOf(0.0)) {
                report.test(key, "motoring-and-regeneration-common-mode-$offset") {
                    val m = MotorElement(node(world), d); m.shaft._mass = d.shaftMass
                    val r = root(m)
                    val negative = if (d.bipolarTerminals) m.negativeLoad else null
                    if (negative != null) r.addComponent(VoltageSource("reference", negative, null).setVoltage(offset))
                    val bus = VoltageState(); r.addState(bus)
                    val supply = VoltageSource("supply", bus, negative).setVoltage(d.nominalU.toDouble())
                    r.addComponent(supply); resistor(r, bus, m.wireLoad, .1); r.generate()
                    repeat(1200) {
                        m.electricalProcess.process(DT); r.step(); m.shaftProcess.process(DT)
                        check(abs(m.powerSource.current) <= d.driveCurrentLimit * 1.001) { "Motor current limit bypassed" }
                    }
                    check(m.shaft.rads > d.nominalRads * .75) { "Motor did not start: ${m.shaft.rads} rad/s" }
                    supply.voltage = d.nominalU * .5
                    repeat(300) {
                        m.shaft.rads = d.nominalRads.toDouble()
                        m.electricalProcess.process(DT); r.step()
                        val before = m.shaft.energy
                        m.thermal.PcTemp = 0.0; m.shaftProcess.process(DT)
                        check(abs((before - m.shaft.energy) / DT - m.powerSource.power - m.thermal.PcTemp) < .01)
                    }
                    check(m.powerSource.power > 1.0 && supply.power < -1.0) { "Driven motor did not charge its supply" }
                    check(m.thermal.PcTemp > m.powerSource.power * 8.9) { "Reverse generation should remain inefficient" }
                }
            }
        }
        for (d in descriptors.filterIsInstance<BatteryDescriptor>()) {
            val key = BuiltInRegistries.ITEM.getKey(d.parentItem).toString()
            report.test(key,"wire-short-drains-charge-and-heats-real-conductor") {
                val b=battery(world,d);val r=root(b)
                val fault=FaultWire(r,b.positiveLoad,b.negativeLoad)
                val before=b.batteryProcess.Q
                r.generate()
                repeat(1200) { r.step();b.batteryProcess.process(DT);fault.step() }
                check(b.batteryProcess.Q<before && fault.inputJoules>0)
                check(b.batteryProcess.charge in 0.0..1.0)
                fault.checkBalance()
            }
            report.test(key, "discharge-recharge-full-empty-save") {
                val b = battery(world, d); val r = root(b)
                val load = resistor(r, b.positiveLoad, b.negativeLoad, d.electricalU * d.electricalU / d.electricalStdP)
                r.generate(); val before = b.batteryProcess.Q
                repeat(100) { r.step(); b.batteryProcess.process(DT) }
                check(b.batteryProcess.Q < before && load.power > 0.0)
                val saved = CompoundTag(); b.batteryProcess.writeToNBT(saved, "test")
                val restored = battery(world, d); restored.batteryProcess.readFromNBT(saved, "test")
                check(restored.batteryProcess.Q == b.batteryProcess.Q && restored.batteryProcess.life == b.batteryProcess.life)
                r.removeComponent(load)
                load.breakConnection()
                check(load !in b.positiveLoad.connectedComponents) { "Discharge load remained connected during charger test" }
                val bus = VoltageState(); r.addState(bus)
                r.addComponent(VoltageSource("charger", bus, b.negativeLoad).setVoltage(d.electricalU * 1.1))
                resistor(r, bus, b.positiveLoad, d.electricalU / d.electricalStdI)
                val depleted = b.batteryProcess.Q
                repeat(100) { r.step(); b.batteryProcess.process(DT) }
                if (d.isRechargable) check(b.batteryProcess.Q > depleted) {
                    "Battery failed to recharge: before=$depleted after=${b.batteryProcess.Q} current=${b.voltageSource.current}"
                }
                else check(b.batteryProcess.Q <= depleted) { "Single-use battery gained charge" }
                b.batteryProcess.charge = 1.0
                repeat(100) { r.step(); b.batteryProcess.process(DT) }
                check(b.batteryProcess.charge <= 1.0 && b.batteryProcess.energy <= b.batteryProcess.energyMax + .001)
                b.batteryProcess.charge = 0.0
                check(b.batteryProcess.u == 0.0 && b.batteryProcess.energy == 0.0)
            }
            report.test(key, "missing-item-fields") {
                val b = battery(world, d)
                b.readItemStackNBT(CompoundTag().apply { putDouble("charge", .25) })
                check(b.fromItemstackLife == 1.0 && b.fromItemstackCharge == .25)
                b.readItemStackNBT(CompoundTag().apply { putDouble("life", .5) })
                check(b.fromItemstackCharge == d.startCharge && b.fromItemstackLife == .5)
                b.readItemStackNBT(CompoundTag().apply { putDouble("charge", 0.0) })
                check(b.fromItemstackCharge == 0.0) { "An explicitly empty battery was refilled" }
            }
        }
        report.write(true)
        return report.failures
    }
}
