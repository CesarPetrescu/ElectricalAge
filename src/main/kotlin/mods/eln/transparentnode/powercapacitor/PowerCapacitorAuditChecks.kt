package mods.eln.transparentnode.powercapacitor

import mods.eln.Eln
import mods.eln.devtest.ContractReport
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.Utils
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.VoltageState
import mods.eln.sim.nbt.NbtResistor
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import kotlin.math.abs

/** Opt-in native diagnostics, called only by the smoke harness. Never a normal gameplay hook. */
object PowerCapacitorAuditChecks {
    @JvmStatic fun run(world: ServerLevel, restart: Boolean, report: ContractReport) {
        report.test("eln:power_capacitor", "signed-native-meter-current-and-voltage") {
            // Use the registered element and its actual multimeter method on an isolated solver,
            // not the production global simulator (which must not be stepped twice by tests).
            val descriptor = Eln.transparentNodeItem.subItemList.values.filterIsInstance<PowerCapacitorDescriptor>().single()
            val node = TransparentNode().apply { coordinate = Coordinate(654, 65, 640, world) }
            val element = PowerCapacitorElement(node, descriptor).apply { front = Direction.XN }
            node.element = element
            val sourcePin = VoltageState()
            element.capacitor.setCoulombs(.001)
            element.dischargeResistor.highImpedance()
            val source = VoltageSource("audit-native", sourcePin, null).setVoltage(10.0)
            val ground = VoltageSource("audit-ground", element.negativeLoad, null).setVoltage(0.0)
            val resistor = Resistor(sourcePin, element.positiveLoad).setResistance(100.0)
            val root = RootSystem(.01, 1)
            element.electricalLoadList.forEach(root::addState)
            element.electricalComponentList.forEach(root::addComponent)
            root.addState(sourcePin); root.addComponent(source); root.addComponent(ground); root.addComponent(resistor)
            root.step()
            check(abs(element.capacitor.voltage - 10.0 / 11) < 1e-7)
            for (volts in listOf(10.0, 0.0)) {
                source.voltage = volts
                val old = element.capacitor.voltage
                root.step()
                val expected = .001 * (element.capacitor.voltage - old) / .01
                check(abs(expected) > .001)
                check(abs(element.capacitor.current - expected) < 1e-9)
                check(abs(resistor.current - element.dischargeResistor.current - expected) < 1e-9)
                check(element.multiMeterString(element.front) == Utils.plotAmpere("I", expected))
                check(if (volts > 0) expected > 0 else expected < 0)
            }
        }
        report.test("eln:resistor", "saved-boundary-recovery-and-live-solve") {
            val pos = BlockPos(652, 65, 640)
            world.setChunkForced(pos.x shr 4, pos.z shr 4, true)
            if (!restart) {
                check(world.isEmptyBlock(pos)) { "Audit persistence cell already occupied" }
                world.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3)
                world.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3)
            }
            val chest = world.getBlockEntity(pos) as? ChestBlockEntity ?: error("Missing persisted audit chest")
            val key = "elnElectricalAudit"
            if (!restart) {
                val saved = NbtResistor("audit", null, null).apply { resistance = 470.0 }
                val valid = CompoundTag().also { saved.writeToNBT(it, "fixture") }
                chest.persistentData.put(key, CompoundTag().apply {
                    put("valid", valid)
                    put("missing", CompoundTag())
                    put("zero", CompoundTag().apply { putDouble("fixtureR", 0.0) })
                    put("wrongType", CompoundTag().apply { putString("fixtureR", "470") })
                })
                chest.setChanged()
            }
            check(chest.persistentData.contains(key)) { "Saved boundary fixtures did not survive restart" }
            val fixtures = chest.persistentData.getCompound(key)
            for ((name, expected) in listOf("valid" to 470.0, "missing" to 10.0, "zero" to 10.0, "wrongType" to 10.0)) {
                check(fixtures.contains(name)) { "Missing $name fixture" }
                val pin = VoltageState()
                val r = NbtResistor("audit", pin, null).apply { resistance = 10.0 }
                r.readFromNBT(fixtures.getCompound(name), "fixture")
                check(r.resistance == expected && r.resistanceInverse.isFinite())
                val root = RootSystem(.01, 1)
                root.addState(pin); root.addComponent(r)
                root.addComponent(VoltageSource("audit-recovered", pin, null).setVoltage(10.0))
                root.step()
                check(abs(r.current - 10.0 / expected) < 1e-9)
                r.setResistance(0.0); root.step()
                check(abs(r.current - 10.0 / expected) < 1e-9)
                r.setResistance(20.0); root.step()
                check(abs(r.current - .5) < 1e-9)
            }
        }
    }
}
