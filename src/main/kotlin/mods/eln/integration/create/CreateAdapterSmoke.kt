package mods.eln.integration.create

import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity
import mods.eln.Eln
import mods.eln.mechanical.ShaftElement
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction as ShaftDirection
import mods.eln.node.NodeManager
import mods.eln.node.transparent.TransparentNode
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.tick.ServerTickEvent

/** Real Create network + ELN shaft topology, with a second JVM launch checking persistence. */
class CreateAdapterSmoke {
    private var ticks = 0
    private val verify = System.getProperty("eln.createSmoke") == "verify"
    private val base = BlockPos(640, 65, 640)
    @SubscribeEvent fun tick(event: ServerTickEvent.Post) {
        ticks++
        val world = event.server.overworld()
        fun adapter(i: Int) = world.getBlockEntity(base.offset(0, 0, i * 8)) as CreateAdapterEntity
        try {
            if (ticks == 10) {
                for (x in 39..41) for (z in 39..41) world.setChunkForced(x, z, true)
                if (!verify) for (i in 0..1) {
                    val p = base.offset(0, 0, i * 8)
                    for (x in -1..3) { world.setBlockAndUpdate(p.offset(x, -1, 0), Blocks.STONE.defaultBlockState()); world.removeBlock(p.offset(x, 0, 0), false) }
                    val motor = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "creative_motor"))
                    world.setBlockAndUpdate(p.west(), motor.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST))
                    world.setBlockAndUpdate(p, (if (i == 0) CreateIntegration.basic else CreateIntegration.industrial).get().defaultBlockState().setValue(CreateAdapterBlock.FACING, Direction.EAST))
                    val player = FakePlayerFactory.getMinecraft(world)
                    player.yRot = 0f; player.yHeadRot = 0f
                    for ((dx, name) in listOf(1 to "Joint", 2 to "Flywheel", 3 to "Generator")) {
                        val stack = Eln.findItemStack(name, 1)
                        player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy())
                        check(Eln.transparentNodeItem.placeBlockAt(stack, player, world, p.offset(dx, 0, 0), Direction.UP)) { "Cannot place $name" }
                    }
                    for ((dx, name) in listOf(3 to "High Voltage Cable", 4 to "Creative Power Resistor", 5 to "Ground Cable")) {
                        val target = p.offset(dx, 0, -1)
                        world.removeBlock(target, false)
                        world.setBlockAndUpdate(target.below(), Blocks.STONE.defaultBlockState())
                        player.yRot = if (dx == 4) 90f else 0f
                        player.yHeadRot = player.yRot
                        val stack = Eln.findItemStack(name, 1)
                        player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy())
                        Eln.sixNodeItem.onItemUse(stack, player, world, target, InteractionHand.MAIN_HAND, Direction.UP, 0.5f, 1f, 0.5f)
                        if (dx == 4) {
                            val node = NodeManager.instance!!.getNodeFromCoordonate(Coordinate(target.x, target.y, target.z, world)) as mods.eln.node.six.SixNode
                            val resistor = node.getElement(ShaftDirection.YN) as mods.eln.item.IConfigurable
                            val config = net.minecraft.nbt.CompoundTag(); config.putDouble("resistance", 1000.0)
                            resistor.readConfigTool(config, player)
                        }
                    }
                }
            }
            if (ticks == 20) for (i in 0..1) {
                if (verify) check(adapter(i).autoRetry && adapter(i).ratio == 8) { "Adapter settings did not survive restart" }
                val motor = world.getBlockEntity(base.offset(-1, 0, i * 8)) as CreativeMotorBlockEntity
                motor.generatedSpeed.setValue(if (i == 0) 256 else -256)
            }
            if (ticks == 160) for (i in 0..1) {
                val a = adapter(i)
                check(a.hasNetwork() && a.outputSpeed > 10 && a.fault == 0) { "Adapter $i did not drive the shaft: ${a.outputSpeed}, fault ${a.fault}" }
                val node = NodeManager.instance!!.getNodeFromCoordonate(Coordinate(base.x + 1, base.y, base.z + i * 8, world)) as TransparentNode
                val joint = node.element as ShaftElement
                check(a.getShaft(ShaftDirection.XP) === joint.getShaft(ShaftDirection.XN)) { "Adapter not in ELN shaft network" }
                check(!a.command(1)) { "Changed gear while engaged" }
                val generatorNode = NodeManager.instance!!.getNodeFromCoordonate(Coordinate(base.x + 3, base.y, base.z + i * 8, world)) as TransparentNode
                val generator = generatorNode.element as mods.eln.mechanical.GeneratorElement
                check(kotlin.math.abs(generator.electricalPowerSource.power) > 0.01) { "Generator has no electrical load" }
                a.command(0); check(a.command(1)); a.command(1); a.command(1); a.command(1); a.command(0)
                Eln.logger.info("CREATE SMOKE PASS tier={} speed={} restart={}", i, a.outputSpeed, verify)
            }
            if (ticks == 180) for (i in 0..1) {
                // Force actual network overstress, then ensure the adapter latches a fault.
                val a = adapter(i)
                a.orCreateNetwork.updateCapacityFor(world.getBlockEntity(base.offset(-1, 0, i * 8)) as CreativeMotorBlockEntity, 0f)
                a.getShaft(ShaftDirection.XP)!!.energy = 0.0
            }
            if (ticks == 185) for (i in 0..1) {
                val a = adapter(i)
                check(a.fault == 1 && a.deliveredPower == 0.0) { "Overstress did not trip adapter" }
                if (!a.autoRetry) a.command(3)
                val motor = world.getBlockEntity(base.offset(-1, 0, i * 8)) as CreativeMotorBlockEntity
                a.orCreateNetwork.updateCapacityFor(motor, motor.calculateAddedStressCapacity())
                world.setBlockAndUpdate(base.offset(0, 1, i * 8), Blocks.REDSTONE_BLOCK.defaultBlockState())
            }
            if (ticks == 190) for (i in 0..1) {
                check(adapter(i).fault == 0) { "Redstone rising edge did not reset the adapter" }
                world.removeBlock(base.offset(0, 1, i * 8), false)
            }
            if (ticks == 210) for (i in 0..1) {
                val a = adapter(i)
                a.orCreateNetwork.updateCapacityFor(world.getBlockEntity(base.offset(-1, 0, i * 8)) as CreativeMotorBlockEntity, 0f)
                a.getShaft(ShaftDirection.XP)!!.energy = 0.0
            }
            if (ticks == 220) for (i in 0..1) {
                val a = adapter(i)
                check(a.fault == 1)
                val motor = world.getBlockEntity(base.offset(-1, 0, i * 8)) as CreativeMotorBlockEntity
                a.orCreateNetwork.updateCapacityFor(motor, motor.calculateAddedStressCapacity())
            }
            if (ticks == 350) {
                for (i in 0..1) check(adapter(i).outputSpeed > 0 && adapter(i).fault == 0)
                Eln.logger.info("CREATE SMOKE PASS overload/redstone reset/automatic retry and both tiers; restart={}", verify)
                event.server.halt(false)
            }
        } catch (t: Throwable) {
            Eln.logger.error("CREATE SMOKE FAILED", t)
            val thread = event.server.runningThread
            Thread { thread.join(); System.exit(1) }.start()
            event.server.halt(false)
        }
    }
    companion object { fun register() { NeoForge.EVENT_BUS.register(CreateAdapterSmoke()) } }
}
