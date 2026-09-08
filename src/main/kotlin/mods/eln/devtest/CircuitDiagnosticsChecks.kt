package mods.eln.devtest

import com.mojang.authlib.GameProfile
import mods.eln.Eln
import mods.eln.misc.*
import mods.eln.node.*
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.transparentnode.battery.BatteryDescriptor
import mods.eln.transparentnode.battery.BatteryElement
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.neoforged.neoforge.common.util.FakePlayer
import java.util.UUID
import kotlin.math.abs

object CircuitDiagnosticsChecks {
    @JvmStatic fun run(world: ServerLevel, restart: Boolean): Int {
        val report = ContractReport(if (restart) "circuit-diagnostics-restart" else "circuit-diagnostics")
        report.write(false)
        mods.eln.transparentnode.powercapacitor.PowerCapacitorAuditChecks.run(world, restart, report)
        val messages = mutableListOf<String>()
        val player = object : FakePlayer(world, GameProfile(UUID.fromString("6190c104-6513-46be-bd64-b880ea8b736b"), "ELNMeterTest")) {
            override fun sendSystemMessage(message: Component) { messages.add(message.string) }
        }
        val node = TransparentNode().apply { coordinate = Coordinate(650, 65, 640, world) }
        val manager = NodeManager.instance!!
        check(manager.getNodeFromCoordonate(node.coordinate) == null) { "Diagnostic test cell already occupied" }
        val descriptor = Eln.transparentNodeItem.subItemList.values.filterIsInstance<BatteryDescriptor>().first()
        val battery = BatteryElement(node, descriptor).apply { front = Direction.XN; voltageSource.voltage = 12.0 }
        node.element = battery
        world.setChunkForced(650 shr 4, 640 shr 4, true)
        manager.addNode(node)
        val root = RootSystem(.01, 1)
        battery.electricalLoadList.forEach(root::addState)
        battery.electricalComponentList.forEach(root::addComponent)
        root.step()
        try {
            report.test("eln:multimeter", "surface-terminals-and-ambiguous-center-on-six-mounts") {
                val d = Eln.sixNodeItem.getDescriptor(Eln.findItemStack("Creative Power Resistor", 1))!!
                for (side in Direction.entries) {
                    for (rotation in LRDU.entries) {
                        val six = mods.eln.node.six.SixNode().apply { coordinate = node.coordinate }
                        val resistor = mods.eln.sixnode.CreativePowerResistorElement(six, side, d).apply { front = rotation }
                        six.sideElementList[side.int] = resistor
                        check(CircuitDiagnostics.terminal(six, side, .5f, .5f, .5f) == null)
                        // The resistor's two poles are perpendicular to its front, not always Left/Right.
                        for (port in listOf(rotation.left(), rotation.right())) {
                            val face = side.applyLRDU(port).toFacing()
                            val selected = CircuitDiagnostics.terminal(six, side, .5f + .4f * face.stepX, .5f + .4f * face.stepY, .5f + .4f * face.stepZ)
                            check(selected?.port == port) { "Wrong port for mounting $side / rotation $rotation / $port: $selected" }
                            check(selected.load === resistor.getElectricalLoad(port, NodeBase.maskElectricalPower))
                        }
                        check(CircuitDiagnostics.load(six, side, rotation) == null)
                    }
                }
            }
            report.test("eln:multimeter", "real-battery-terminal-mapping-and-floating-voltage") {
                check(CircuitDiagnostics.load(node, battery.front.left(), LRDU.Down) === battery.positiveLoad)
                check(CircuitDiagnostics.load(node, battery.front.right(), LRDU.Down) === battery.negativeLoad)
                check(CircuitDiagnostics.load(node, battery.front, LRDU.Down) == null)
                check(abs(battery.positiveLoad.voltage - 6) < .001)
                check(abs(battery.negativeLoad.voltage + 6) < .001)
            }
            report.test("eln:multimeter", "probe-interaction-reads-current-values-not-selection-snapshot") {
                val meter = Eln.multiMeterElement.newItemStack()
                player.setItemInHand(InteractionHand.MAIN_HAND, meter); player.isShiftKeyDown = true
                check(CircuitDiagnostics.activate(node, player, battery.front.right(), .5f, 0f, .5f))
                check(meter.tagCompound!!.contains(CircuitDiagnostics.PROBE_KEY))
                battery.voltageSource.voltage = 24.0; root.step()
                messages.clear()
                check(CircuitDiagnostics.activate(node, player, battery.front.left(), .5f, 0f, .5f))
                check(messages.any { it.contains(Utils.plotVolt("", 24.0)) }) { messages.joinToString() }
                check(abs(CircuitDiagnostics.difference(battery.negativeLoad.voltage, battery.positiveLoad.voltage) - 24) < .001)
                Eln.multiMeterElement.onItemRightClick(meter, world, player)
                check(!meter.tagCompound!!.contains(CircuitDiagnostics.PROBE_KEY))
            }
            report.test("eln:multimeter", "missing-terminal-clears-stale-probe") {
                val meter = Eln.multiMeterElement.newItemStack()
                player.setItemInHand(InteractionHand.MAIN_HAND, meter)
                CircuitDiagnostics.activate(node, player, battery.front.right(), .5f, 0f, .5f)
                battery.front = Direction.XP
                // The old face still exposes a terminal after rotation: use a different descriptor identity instead.
                val tag = meter.tagCompound!!
                tag.getCompound(CircuitDiagnostics.PROBE_KEY).putString("identity", "removed-fixture")
                meter.tagCompound = tag
                CircuitDiagnostics.activate(node, player, battery.front.left(), .5f, 0f, .5f)
                check(!meter.tagCompound!!.contains(CircuitDiagnostics.PROBE_KEY))
            }
            report.test("eln:multimeter", "normal-click-remains-available") {
                player.isShiftKeyDown = false
                check(!CircuitDiagnostics.activate(node, player, battery.front, .5f, .5f, .5f))
                check(battery.multiMeterString(battery.front).contains(Utils.plotVolt("", 24.0)))
            }
            report.test("eln:guide", "12V-12ohm-load-and-open-return") {
                battery.voltageSource.voltage = 12.0
                val load = Resistor(battery.positiveLoad, battery.negativeLoad).apply { resistance = 12.0 }
                root.addComponent(load); root.step()
                check(abs(load.current - 1.0) < .001)
                check(abs(load.power - 12.0) < .001)
                root.removeComponent(load); load.breakConnection(); root.step()
                check(abs(battery.positiveLoad.voltage - battery.negativeLoad.voltage - 12) < .001)
                // Source voltage persists even with the useful load path removed.
                check(abs(battery.voltageSource.current) < 1e-5) { "Open-return source current ${battery.voltageSource.current} A" }
            }
        } finally {
            manager.removeNode(node)
        }
        report.write(true)
        return report.failures
    }
}
