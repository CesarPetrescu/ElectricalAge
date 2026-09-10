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
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
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
        val cables = UtilityCableDescriptor.allDescriptors().filter {
            !it.melted && it.parentItemDamage in (38 shl 6)..((38 shl 6) + 19)
        }
        check(cables.size == 10 && cables.all(GridCablePolicy::accepts))
        val player = FakePlayerFactory.getMinecraft(world)
        val oldMode = player.gameMode.gameModeForPlayer
        val oldHand = player.mainHandItem.copy()
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
                        val spool = cable.newItemStack().also { cable.setRemainingLengthMeters(it, 32.0) }
                        player.setItemInHand(InteractionHand.MAIN_HAND, spool)
                        val start = if (reverse) second else first
                        val end = if (reverse) first else second
                        val startSide = if (reverse) sideB else sideA
                        val endSide = if (reverse) sideA else sideB
                        check(start.onBlockActivated(player, startSide, .5f, .5f, .5f))
                        check(first.gridLinkList.isEmpty() && second.gridLinkList.isEmpty())
                        check(cable.getRemainingLengthMeters(player.mainHandItem) == 32.0)
                        check(end.onBlockActivated(player, endSide, .5f, .5f, .5f))
                        val link = first.gridLinkList.single()
                        check(second.gridLinkList.single() === link && link.connected)
                        check(cable.getRemainingLengthMeters(player.mainHandItem) == 26.0)
                        check(Eln.sixNodeItem.getDescriptor(link.cable) === cable)
                        check(cable.getRemainingLengthMeters(link.cable) == 6.0)
                        check(checkNotNull(link.spanThermal).meters == 6.0)
                        val tag = CompoundTag(); link.writeToNBT(tag, "")
                        val restored = GridLink(tag, "")
                        check(Eln.sixNodeItem.getDescriptor(restored.cable) === cable)
                        check(cable.getRemainingLengthMeters(restored.cable) == 6.0)
                        val refund = link.onBreakElement()
                        check(cable.getRemainingLengthMeters(refund) == 6.0)
                        check(!link.connected && first.gridLinkList.isEmpty() && second.gridLinkList.isEmpty())
                    }
                    // A former poleEligible flag cannot bypass the new minimum voltage or damage rule.
                    val low = UtilityCableDescriptor.allDescriptors().first {
                        it.poleEligible && !it.melted && it.insulated && it.insulationVoltageRating == 600.0
                    }
                    for (rejected in listOf(low, checkNotNull(cables.first().meltedDescriptor))) {
                        val spool = rejected.newItemStack().also { rejected.setRemainingLengthMeters(it, 32.0) }
                        player.setItemInHand(InteractionHand.MAIN_HAND, spool)
                        first.onBlockActivated(player, sideA, .5f, .5f, .5f)
                        second.onBlockActivated(player, sideB, .5f, .5f, .5f)
                        check(first.gridLinkList.isEmpty() && second.gridLinkList.isEmpty())
                        check(rejected.getRemainingLengthMeters(player.mainHandItem) == 32.0)
                    }
                } finally {
                    a?.gridLinkList?.toList()?.forEach { it.onBreakElement() }
                    b?.gridLinkList?.toList()?.forEach { it.onBreakElement() }
                    world.removeBlock(origin, false)
                    world.removeBlock(partner, false)
                }
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, oldHand)
            player.setGameMode(oldMode)
            world.setChunkForced(origin.x shr 4, origin.z shr 4, false)
        }
    }
}
