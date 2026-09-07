package mods.eln.integration.create

import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity
import mods.eln.Eln
import mods.eln.mechanical.ShaftElement
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction as ElnDirection
import mods.eln.node.NodeManager
import mods.eln.node.transparent.TransparentNode
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.util.FakePlayerFactory

/** Actual placements, Create networks and ELN hubs on all six faces, not just state assertions. */
internal object CreateAdapterPortSmoke {
    fun position(index: Int) = BlockPos(652, 69, 638 + index * 5)
    fun place(world: ServerLevel) {
        val player = FakePlayerFactory.getMinecraft(world)
        for ((i, facing) in Direction.values().withIndex()) {
            val pos = position(i)
            world.removeBlock(pos, false)
            for (side in Direction.values()) world.removeBlock(pos.relative(side), false)
            val reverse = i % 2 == 1
            val clicked = if (reverse) facing.opposite else facing
            val support = pos.relative(clicked.opposite)
            world.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState())
            player.isShiftKeyDown = reverse
            val stack = ItemStack((if (reverse) CreateIntegration.industrial else CreateIntegration.basic).get())
            player.setItemInHand(InteractionHand.MAIN_HAND, stack)
            val context = BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, BlockHitResult(Vec3.atCenterOf(support), clicked, support, false))
            check((stack.item as BlockItem).place(context).consumesAction()) { "Placement failed: $facing" }
            check(world.getBlockState(pos).getValue(CreateAdapterBlock.FACING) == facing) { "Incorrect placement orientation: $facing" }
            player.isShiftKeyDown = false
            world.removeBlock(support, false)
            val motor = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "creative_motor"))
            world.setBlockAndUpdate(pos.relative(facing.opposite), motor.defaultBlockState().setValue(BlockStateProperties.FACING, facing))
            for (side in Direction.values().filter { it != facing.opposite }) {
                val hub = Eln.findItemStack("Joint hub", 1)
                player.setItemInHand(InteractionHand.MAIN_HAND, hub.copy())
                check(Eln.transparentNodeItem.placeBlockAt(hub, player, world, pos.relative(side), Direction.UP))
            }
        }
    }
    fun start(world: ServerLevel) {
        for ((i, facing) in Direction.values().withIndex()) {
            (world.getBlockEntity(position(i).relative(facing.opposite)) as CreativeMotorBlockEntity).generatedSpeed.setValue(64)
        }
    }
    fun verify(world: ServerLevel) {
        val player = FakePlayerFactory.getMinecraft(world)
        for ((i, facing) in Direction.values().withIndex()) {
            val pos = position(i)
            val adapter = world.getBlockEntity(pos) as CreateAdapterEntity
            val block = adapter.blockState.block as CreateAdapterBlock
            check(adapter.outputSpeed > 10 && adapter.fault == 0) { "No output on $facing: ${adapter.outputSpeed}" }
            for (side in Direction.values()) {
                check(block.hasShaftTowards(world, pos, adapter.blockState, side) == (side == facing.opposite))
                check(block.canConnectRedstone(adapter.blockState, world, pos, side) == (side.axis != facing.axis))
                if (side == facing.opposite) continue
                val p = pos.relative(side)
                val hub = (NodeManager.instance!!.getNodeFromCoordonate(Coordinate(p.x, p.y, p.z, world)) as TransparentNode).element as ShaftElement
                val joined = adapter.getShaft(ElnDirection.fromFacing(facing)) === hub.getShaft(ElnDirection.fromFacing(side.opposite))
                check(joined == (side == facing)) { "Wrong ELN port connected: facing=$facing side=$side" }
                if (side != facing) check(hub.getShaft(ElnDirection.fromFacing(side.opposite))!!.rads == 0.0)
            }
            val control = Direction.values().first { it.axis != facing.axis }
            val target = pos.relative(control)
            world.removeBlock(target, false)
            world.setBlockAndUpdate(target, Blocks.REDSTONE_BLOCK.defaultBlockState())
            check(adapter.controlPowered()) { "No side redstone input on $facing" }
            world.removeBlock(target, false)
            val cable = Eln.findItemStack("Signal Cable", 1)
            player.setItemInHand(InteractionHand.MAIN_HAND, cable)
            Eln.sixNodeItem.onItemUse(cable, player, world, target, InteractionHand.MAIN_HAND, control, .5f, .5f, .5f)
            val node = NodeManager.instance!!.getNodeFromCoordonate(Coordinate(target.x, target.y, target.z, world)) as mods.eln.node.six.SixNode
            val wire = node.getElement(ElnDirection.fromFacing(control.opposite)) as mods.eln.sixnode.electricalcable.ElectricalCableElement
            wire.electricalLoad.voltage = Eln.SVU
            check(adapter.controlPowered()) { "No mounted signal input on $facing" }
            wire.electricalLoad.voltage = 0.0
            check(!adapter.controlPowered())
            world.removeBlock(target, false)
            // Restore the isolated hub so restart verification uses the same topology.
            val hub = Eln.findItemStack("Joint hub", 1)
            player.setItemInHand(InteractionHand.MAIN_HAND, hub.copy())
            check(Eln.transparentNodeItem.placeBlockAt(hub, player, world, target, Direction.UP))
            Eln.logger.info("CREATE PORT PASS {}: placement, rotation, output-only topology and side controls", facing)
        }
    }
}
