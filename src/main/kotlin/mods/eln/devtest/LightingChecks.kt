package mods.eln.devtest

import mods.eln.Eln
import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.item.lampitem.LampDescriptor
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.node.NodeBase
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.TransparentNode
import mods.eln.sixnode.lampsocket.LampSocketElement
import mods.eln.transparentnode.floodlight.FloodlightElement
import mods.eln.transparentnode.floodlight.FloodlightOptics
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Run on each supported mounting face, before the generic placement contract removes the fixture. */
object LightingChecks {
    fun prepareGallery(world: net.minecraft.server.level.ServerLevel, player: ServerPlayer, entry: BlockContracts.Entry) {
        val pos = net.minecraft.core.BlockPos(entry.x, entry.y, entry.z)
        val node = mods.eln.node.NodeManager.instance?.getNodeFromCoordonate(mods.eln.misc.Coordinate(entry.x, entry.y, entry.z, world))
        val side = Direction.fromInt(entry.side)!!
        val lamp = (node as? SixNode)?.getElement(side) as? LampSocketElement ?: return
        lamp.inventory.setItem(0, Eln.findItemStack("120V Halogen Light Bulb", 1))
        lamp.inventory.setItem(1, Eln.findItemStack("Low Voltage Cable", 1))
        lamp.poweredByLampSupply = false
        lamp.inventoryChange(lamp.inventory)
        for (port in LRDU.entries) {
            if (!lamp.descriptor.renderSideCables && port != lamp.front && port != lamp.front.inverse()) continue
            val neighbour = pos.relative(side.applyLRDU(port).toFacing())
            world.setBlockAndUpdate(neighbour.relative(side.toFacing()), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState())
            val cable = Eln.findItemStack("Low Voltage Cable", 1)
            player.setItemInHand(InteractionHand.MAIN_HAND, cable)
            if (world.getBlockState(neighbour).block === Eln.sixNodeBlock) world.removeBlock(neighbour, false)
            check(Eln.sixNodeItem.placeBlockAt(cable, player, world, neighbour, side.inverse.toFacing(), .5f, .5f, .5f)) {
                "Gallery cable placement failed at $neighbour"
            }
        }
        lamp.needPublish()
    }

    fun check(node: NodeBase?, player: ServerPlayer, side: Direction) {
        val lamp = (node as? SixNode)?.getElement(side) as? LampSocketElement
        val flood = (node as? TransparentNode)?.element as? FloodlightElement
        if (lamp == null && flood == null) return
        val bulb = Eln.findItemStack("120V Halogen Light Bulb", 2)
        val descriptor = GenericItemUsingDamageDescriptor.getDescriptor(bulb) as LampDescriptor
        descriptor.setLifeInTag(bulb, 7.5)
        player.setItemInHand(InteractionHand.MAIN_HAND, bulb)
        check(if (lamp != null) lamp.onBlockActivated(player, side, .5f, .5f, .5f)
            else flood!!.onBlockActivated(player, side, .5f, .5f, .5f)) { "Direct halogen insertion was rejected" }
        val inventory = lamp?.inventory ?: flood!!.inventory
        check(inventory.getItem(0).count == 1) { "Expected one installed bulb" }
        check(descriptor.getLifeInTag(inventory.getItem(0)) == 7.5) { "Insertion reset bulb lifetime" }
        val menu = lamp?.newContainer(side, player) ?: flood!!.newContainer(side, player)
        check(menu.getSlot(0).mayPlace(descriptor.newItemStack())) { "GUI rejects accepted halogen bulb" }
        if (flood != null) {
            check(flood.rotationAxis.toStandardDirection() == side.inverse) { "Mounting axis differs from clicked face" }
            check(flood.electricalLoadList.count { it === flood.electricalLoad } == 1) { "Duplicate MNA load" }
            repeat(4) {
                for (face in Direction.entries) for (lrdu in LRDU.entries) {
                    val expected = FloodlightOptics.isMountingPlanePort(flood.rotationAxis, face, lrdu) &&
                        (flood.motorized || face.toHybridNodeDirection() == flood.blockFacing.back())
                    check((flood.getElectricalLoad(face, lrdu) != null) == expected) { "Wrong floodlight port: $face/$lrdu" }
                }
                flood.blockFacing = flood.blockFacing.left(flood.rotationAxis)
            }
            if (!flood.motorized) {
                for ((event, value) in listOf(0 to 123.0, 1 to 90.0, 2 to 35.0)) {
                    val bytes = ByteArrayOutputStream()
                    DataOutputStream(bytes).use { it.writeByte(event); it.writeDouble(value) }
                    flood.networkUnserialize(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))
                }
                check(flood.swivelAngle == 123.0 && flood.headAngle == 90.0 && flood.beamWidth == 35.0) {
                    "Aim controls did not reach server state"
                }
            }
            // Exercise the actual light-placement process at the old polar singularity.
            // Supply the load directly here; full source/cable delivery is covered by the main lamp circuit.
            // Disconnect first so the live solver cannot overwrite this synthetic voltage mid-check.
            flood.disconnect()
            try {
                flood.electricalLoad.state = 120.0
                flood.swivelAngle = 0.0
                flood.headAngle = 90.0
                flood.beamWidth = 20.0
                flood.swivelControl.state = 0.0
                flood.headControl.state = Eln.SVU * 0.5
                flood.beamControl.state = Eln.SVU * 20.0 / 45.0
                mods.eln.transparentnode.floodlight.FloodlightProcess(flood).process(0.05)
                val coordinate = flood.node!!.coordinate
                val lit = coordinate.pos.relative(flood.rotationAxis.toStandardDirection().toFacing(), 2)
                check(coordinate.world().getBlockState(lit).block === Eln.lightBlock) {
                    "Powered floodlight did not illuminate $lit: voltage=${flood.electricalLoad.voltage}, powered=${flood.powered}, " +
                        "aim=${flood.swivelAngle}/${flood.headAngle}/${flood.beamWidth}, " +
                        "ray=${FloodlightOptics.direction(flood.swivelAngle, flood.headAngle, flood.rotationAxis, flood.blockFacing)}, " +
                        "path=" + (1..3).map { coordinate.world().getBlockState(coordinate.pos.relative(flood.rotationAxis.toStandardDirection().toFacing(), it)) }
                }
            } finally {
                flood.electricalLoad.state = 0.0
                flood.connect()
            }
        }
    }
}
