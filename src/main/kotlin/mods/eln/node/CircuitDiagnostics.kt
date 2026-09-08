package mods.eln.node

import mods.eln.Eln
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.ElectricalLoad
import mods.eln.sixnode.electricalcable.UtilityCableElement
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import kotlin.math.abs

/** Read-only probes. Re-resolve BOTH terminals on the server; never cache old voltage values. */
object CircuitDiagnostics {
    const val PROBE_KEY = "elnMeterProbe"
    data class Terminal(val port: LRDU, val load: ElectricalLoad)

    fun difference(reference: Double, measured: Double): Double {
        require(reference.isFinite() && measured.isFinite())
        return measured - reference
    }

    fun clear(stack: ItemStack) {
        val tag = stack.tagCompound ?: return
        tag.remove(PROBE_KEY)
        stack.tagCompound = tag
    }

    fun load(node: NodeBase, side: Direction, port: LRDU): ElectricalLoad? {
        val element = (node as? SixNode)?.getElement(side)
        // A multi-core probe needs explicit core selection. Never silently measure the first core.
        if (element is UtilityCableElement && !element.descriptor.actsAsSingleConductor) return null
        val mask = if (node is SixNode) element?.getConnectionMask(port) ?: 0 else node.getSideConnectionMask(side, port)
        if (mask and (NodeBase.maskElectricalPower or NodeBase.maskElectricalGate) == 0) return null
        return if (node is SixNode) element?.getElectricalLoad(port, mask) else node.getElectricalLoad(side, port, mask)
    }

    fun terminal(node: NodeBase, side: Direction, x: Float, y: Float, z: Float): Terminal? {
        fun distance(terminal: Terminal): Double {
            val direction = side.applyLRDU(terminal.port).toFacing()
            val tx = .5 + .4 * direction.stepX; val ty = .5 + .4 * direction.stepY; val tz = .5 + .4 * direction.stepZ
            return (x - tx) * (x - tx) + (y - ty) * (y - ty) + (z - tz) * (z - tz)
        }
        val candidates = LRDU.entries.mapNotNull { port -> load(node, side, port)?.let { Terminal(port, it) } }.sortedBy(::distance)
        val first = candidates.firstOrNull() ?: return null
        // Clicking midway between different terminals must not silently choose one of them.
        if (candidates.any { it.load !== first.load && distance(it) - distance(first) < .04 }) return null
        return first
    }

    private fun identity(node: NodeBase, side: Direction) = when (node) {
        is SixNode -> node.getElement(side)?.sixNodeElementDescriptor?.name ?: ""
        is TransparentNode -> node.element?.descriptor?.name ?: ""
        else -> node.javaClass.name
    }

    fun activate(node: NodeBase, player: Player, side: Direction, x: Float, y: Float, z: Float): Boolean {
        val stack = player.mainHandItem
        if (!player.isShiftKeyDown || !(Eln.multiMeterElement.checkSameItemStack(stack) || Eln.allMeterElement.checkSameItemStack(stack))) return false
        val terminal = terminal(node, side, x, y, z)
        if (terminal == null) {
            Utils.sendMessage(player, tr("No unambiguous electrical terminal here. Aim at a connector; use a single-core breakout for multicore cables."))
            return true
        }
        val tag = stack.tagCompound ?: CompoundTag()
        val world = player.level()
        val dimension = world.dimension().location().toString()
        if (!tag.contains(PROBE_KEY)) {
            val probe = CompoundTag()
            probe.putLong("pos", node.coordinate.pos.asLong())
            probe.putString("dimension", dimension)
            probe.putInt("side", side.int); probe.putInt("port", terminal.port.toInt())
            probe.putString("identity", identity(node, side))
            tag.put(PROBE_KEY, probe); stack.tagCompound = tag
            Utils.sendMessage(player, tr("Probe A selected at %1$, face %2$, port %3$. Sneak-click B to measure B minus A.", node.coordinate.pos.toShortString(), side.toFacing().getName(), terminal.port.name))
            return true
        }
        val probe = tag.getCompound(PROBE_KEY)
        val pos = BlockPos.of(probe.getLong("pos"))
        val firstSide = Direction.entries.getOrNull(probe.getInt("side"))
        val firstPort = LRDU.entries.getOrNull(probe.getInt("port"))
        if (probe.getString("dimension") != dimension || pos.distSqr(node.coordinate.pos) > 64.0 * 64 || !world.hasChunkAt(pos)) {
            clear(stack)
            Utils.sendMessage(player, tr("Probe A is unloaded, in another dimension, or over 64 blocks away. Select it again."))
            return true
        }
        val firstNode = NodeManager.instance?.getNodeFromCoordonate(Coordinate(pos.x, pos.y, pos.z, world))
        val first = if (firstNode != null && firstSide != null && firstPort != null && identity(firstNode, firstSide) == probe.getString("identity")) load(firstNode, firstSide, firstPort) else null
        if (first == null) {
            clear(stack)
            Utils.sendMessage(player, tr("Probe A no longer has the selected terminal. Select it again."))
            return true
        }
        val second = terminal.load
        if (!first.voltage.isFinite() || !second.voltage.isFinite()) {
            Utils.sendMessage(player, tr("The circuit has no finite solver reading yet.")); return true
        }
        Utils.sendMessage(player, tr("B minus A: %1$ | A to ground: %2$ | B to ground: %3$", Utils.plotVolt("", difference(first.voltage, second.voltage)), Utils.plotVolt("", first.voltage), Utils.plotVolt("", second.voltage)))
        if (first === second) Utils.sendMessage(player, tr("Both probes touch the same electrical terminal."))
        if (first.subSystem !== second.subSystem || first.subSystem == null)
            Utils.sendMessage(player, tr("Separate or unsolved electrical sections: check their ground references before interpreting this difference."))
        Utils.sendMessage(player, tr("Terminal B current: %1$ | Cable/contact loss at B: %2$", Utils.plotAmpere("", second.current), Utils.plotPower("", second.serialPower)))
        if (abs(second.current) < 1e-6)
            Utils.sendMessage(player, tr("Almost no current: the load may be off, disconnected, or idle. This reading alone cannot prove a broken return wire."))
        Utils.sendMessage(player, tr("A remains selected. Sneak-click another B to compare; sneak-use in air to clear."))
        return true
    }
}
