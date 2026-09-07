package mods.eln.sixnode.electricaldatalogger

import mods.eln.Eln
import mods.eln.devtest.ContractReport
import mods.eln.misc.*
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.common.util.FakePlayerFactory
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/** Real monitor print packet, inventory transaction and persistent in-world print fixture. */
object MonitorPrintChecks {
    private val fixture = BlockPos(640, 65, 640)
    fun sampleTag() = CompoundTag().apply {
        putByteArray("log", byteArrayOf(127, 0, -128))
        putFloat("samplingPeriod", 2f); putFloat("maxValue", 12f); putFloat("minValue", -12f)
        putByte("unitType", DataLogs.voltageType); putBoolean("showZeroLine", true)
    }
    @JvmStatic fun run(world: ServerLevel, restart: Boolean): Int {
        val report = ContractReport(if (restart) "monitor-print-restart" else "monitor-print")
        report.write(false)
        val player = FakePlayerFactory.getMinecraft(world)
        val descriptors = Eln.sixNodeItem.subItemList.values.filterIsInstance<ElectricalDataLoggerDescriptor>()
        report.test("eln:data_logger_print", "monitor-discovery") { check(descriptors.isNotEmpty()) }
        fun print(e: ElectricalDataLoggerElement) {
            e.networkUnserialize(DataInputStream(ByteArrayInputStream(byteArrayOf(ElectricalDataLoggerElement.printId))), player)
            e.slowProcess.process(.01)
        }
        for (d in descriptors) {
            val id = BuiltInRegistries.ITEM.getKey(d.parentItem).toString()
            report.test(id, "print-consumes-one-paper-and-preserves-snapshot") {
                val node = SixNode().apply { coordinate = Coordinate(fixture.x, fixture.y, fixture.z, world) }
                val e = ElectricalDataLoggerElement(node, Direction.YN, d)
                e.pause = true; e.timeToNextSample = 0.0; e.sampleStackNbr = 0
                e.logs.readFromNBT(sampleTag(), "")
                e.inventory.setItem(0, ItemStack(Items.PAPER, 3))
                print(e)
                check(e.inventory.getItem(0).count == 2)
                val output = e.inventory.getItem(1)
                check(Eln.instance.dataLogsPrintDescriptor.checkSameItemStack(output) && output.count == 1)
                val saved = output.tagCompound!!.copy()
                check(saved.getByteArray("log").contentEquals(sampleTag().getByteArray("log")))
                print(e) // occupied output must neither consume nor overwrite
                check(e.inventory.getItem(0).count == 2 && e.inventory.getItem(1).tagCompound == saved)
                e.logs.reset()
                check(output.tagCompound == saved)
                e.inventory.setItem(1, ItemStack.EMPTY)
                e.inventory.setItem(0, ItemStack(Items.STONE))
                print(e)
                check(e.inventory.getItem(1).isEmpty && e.inventory.getItem(0).`is`(Items.STONE))
                e.inventory.setItem(0, ItemStack.EMPTY); print(e)
                check(e.inventory.getItem(1).isEmpty)
                val restored = ItemStack.parseOptional(world.registryAccess(), output.save(world.registryAccess()) as CompoundTag)
                check(restored.tagCompound == saved)
            }
        }
        report.test("eln:data_logger_print", "saved-world-monitor-print") {
            world.setChunkForced(fixture.x shr 4, fixture.z shr 4, true)
            if (!restart) {
                val descriptor = descriptors.first { it.onFloor }
                world.setBlockAndUpdate(fixture.below(), Blocks.STONE.defaultBlockState())
                player.isShiftKeyDown = false
                val stack = descriptor.newItemStack()
                player.setItemInHand(InteractionHand.MAIN_HAND, stack)
                check(Eln.sixNodeItem.placeBlockAt(stack, player, world, fixture, net.minecraft.core.Direction.UP, .5f, .5f, .5f))
            }
            val node = NodeManager.instance!!.getNodeFromCoordonate(Coordinate(fixture.x, fixture.y, fixture.z, world)) as SixNode
            val e = node.getElement(Direction.YN) as ElectricalDataLoggerElement
            if (!restart) {
                e.pause = true; e.logs.readFromNBT(sampleTag(), "")
                e.inventory.setItem(0, ItemStack(Items.PAPER, 2)); print(e)
            }
            check(e.pause)
            check(e.inventory.getItem(0).count == 1)
            val output = e.inventory.getItem(1)
            check(Eln.instance.dataLogsPrintDescriptor.checkSameItemStack(output))
            val chart = PrintedLogData(output.tagCompound)
            check(chart.size == 3 && chart.value(0) == 12.0 && chart.value(2) == -12.0 && chart.duration == 4.0)
        }
        report.write(true)
        return report.failures
    }
}
