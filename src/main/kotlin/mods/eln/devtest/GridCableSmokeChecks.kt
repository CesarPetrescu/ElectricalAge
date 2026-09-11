package mods.eln.devtest

import mods.eln.Eln
import mods.eln.gridnode.GridCablePolicy
import mods.eln.gridnode.GridDescriptor
import mods.eln.gridnode.GridElement
import mods.eln.gridnode.GridLink
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.node.NodeManager
import mods.eln.node.transparent.TransparentNode
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.common.util.FakePlayerFactory

/** Opt-in dedicated-server test: registered blocks, normal server click handler and survival spools.
 * It does not claim to exercise client mouse targeting or the native renderer.
 */
object GridCableSmokeChecks {
    fun run(world: ServerLevel) {
        val devices = Eln.transparentNodeItem.subItemList.values.filterIsInstance<GridDescriptor>().distinct()
        check(devices.map { it.name }.toSet() == setOf("Grid DC-DC Converter", "Utility Pole",
            "Utility Pole w/DC-DC Converter", "Transmission Tower", "Direct Utility Pole", "Grid Switch"))
        val registered = Eln.sixNodeItem.subItemList.values.filterIsInstance<ElectricalCableDescriptor>().distinct()
        // Select by circuit type/damage, independently of the policy under test.
        val cables = registered.filter { !it.signalWire && !(it is UtilityCableDescriptor && it.melted) }
        check(cables.isNotEmpty() && cables.all(GridCablePolicy::accepts))
        check(cables.any { it !is UtilityCableDescriptor && it.electricalNominalVoltage < 1000.0 })
        check(cables.filterIsInstance<UtilityCableDescriptor>().any { !it.insulated })
        check(cables.filterIsInstance<UtilityCableDescriptor>().any { it.insulated && it.insulationVoltageRating < 1000.0 })
        check(cables.filterIsInstance<UtilityCableDescriptor>().count {
            it.parentItemDamage in (38 shl 6)..((38 shl 6) + 19)
        } == 10)
        val player = FakePlayerFactory.getMinecraft(world)
        val oldMode = player.gameMode.gameModeForPlayer
        val oldItems = player.inventory.items.map { it.copy() }
        // Legacy cable consumption can search the whole inventory. Isolate and restore it.
        player.inventory.items.indices.forEach { player.inventory.items[it] = ItemStack.EMPTY }
        var connections = 0
        fun stack(cable: ElectricalCableDescriptor, meters: Int): ItemStack =
            if (cable is UtilityCableDescriptor) cable.newItemStack().also { cable.setRemainingLengthMeters(it, meters.toDouble()) }
            else cable.newItemStack(meters)
        fun meters(cable: ElectricalCableDescriptor, item: ItemStack): Double =
            if (cable is UtilityCableDescriptor) cable.getRemainingLengthMeters(item) else item.count.toDouble()
        val origin = BlockPos(264, 80, 264)
        val partner = origin.east(6)
        world.setChunkForced(origin.x shr 4, origin.z shr 4, true)
        player.setGameMode(GameType.SURVIVAL)
        fun node(p: BlockPos) = NodeManager.instance!!.getNodeFromCoordonate(Coordinate(p.x, p.y, p.z, world)) as? TransparentNode
        fun place(name: String, p: BlockPos): GridElement {
            check(Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack(name, 1), player, world, p, net.minecraft.core.Direction.UP))
            return checkNotNull(node(p)?.element as? GridElement)
        }
        try {
            for (dx in -4..10) for (dz in -4..4) {
                world.setBlockAndUpdate(origin.offset(dx, -1, dz), Blocks.STONE.defaultBlockState())
                for (dy in 0..10) world.removeBlock(origin.offset(dx, dy, dz), false)
            }
            for (descriptor in devices) {
                var a: GridElement? = null
                var b: GridElement? = null
                try {
                    val first = place(descriptor.name, origin).also { a = it }
                    val second = place("Direct Utility Pole", partner).also { b = it }
                    val sideA = Direction.values().first { first.getGridElectricalLoad(it) != null }
                    val sideB = Direction.values().first { second.getGridElectricalLoad(it) != null }
                    for (cable in cables) for (reverse in listOf(false, true)) {
                        player.setItemInHand(InteractionHand.MAIN_HAND, stack(cable, 32))
                        val start = if (reverse) second else first
                        val end = if (reverse) first else second
                        val startSide = if (reverse) sideB else sideA
                        val endSide = if (reverse) sideA else sideB
                        check(start.onBlockActivated(player, startSide, .5f, .5f, .5f))
                        check(first.gridLinkList.isEmpty() && second.gridLinkList.isEmpty())
                        check(meters(cable, player.mainHandItem) == 32.0)
                        check(end.onBlockActivated(player, endSide, .5f, .5f, .5f))
                        check(first.gridLinkList.size == 1) { "${descriptor.name}: ${cable.name}, reversed=$reverse" }
                        val link = first.gridLinkList.single()
                        check(second.gridLinkList.single() === link && link.connected)
                        check(meters(cable, player.mainHandItem) == 26.0)
                        check(Eln.sixNodeItem.getDescriptor(link.cable) === cable)
                        check(meters(cable, link.cable) == 6.0)
                        if (cable is UtilityCableDescriptor) check(checkNotNull(link.spanThermal).meters == 6.0)
                        val tag = CompoundTag(); link.writeToNBT(tag, "")
                        val restored = GridLink(tag, "")
                        check(Eln.sixNodeItem.getDescriptor(restored.cable) === cable)
                        check(meters(cable, restored.cable) == 6.0)
                        val refund = link.onBreakElement()
                        check(meters(cable, refund) == 6.0)
                        check(!link.connected && first.gridLinkList.isEmpty() && second.gridLinkList.isEmpty())
                        connections++
                    }
                    // Actual signal buses and damaged conductors remain incompatible, at any rating.
                    val rejectedCables = registered.filter { it.signalWire || (it is UtilityCableDescriptor && it.melted) }
                    check(rejectedCables.any { it.signalWire })
                    check(rejectedCables.any { it is UtilityCableDescriptor && it.melted })
                    for (rejected in rejectedCables) {
                        player.setItemInHand(InteractionHand.MAIN_HAND, stack(rejected, 32))
                        first.onBlockActivated(player, sideA, .5f, .5f, .5f)
                        second.onBlockActivated(player, sideB, .5f, .5f, .5f)
                        check(first.gridLinkList.isEmpty() && second.gridLinkList.isEmpty())
                        check(meters(rejected, player.mainHandItem) == 32.0)
                    }
                } finally {
                    a?.gridLinkList?.toList()?.forEach { it.onBreakElement() }
                    b?.gridLinkList?.toList()?.forEach { it.onBreakElement() }
                    world.removeBlock(origin, false)
                    world.removeBlock(partner, false)
                }
            }
            check(connections == devices.size * cables.size * 2)
            Eln.logger.info("GRID CABLE PASS: {} devices x {} intact power cables x 2 click orders = {} connections",
                devices.size, cables.size, connections)
        } finally {
            oldItems.forEachIndexed { index, item -> player.inventory.items[index] = item }
            player.inventory.setChanged()
            player.setGameMode(oldMode)
            world.setChunkForced(origin.x shr 4, origin.z shr 4, false)
        }
    }
}
