package mods.eln.sixnode.electricaldatalogger

import mods.eln.Eln
import mods.eln.misc.tagCompound
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Server-only fixture setup; printing itself must arrive from real clients over the network. */
object MultiplayerMonitorFixture {
    fun prepare(e: ElectricalDataLoggerElement) {
        e.pause = true
        e.logs.readFromNBT(MonitorPrintChecks.sampleTag(), "")
        e.inventory.setItem(0, ItemStack(Items.PAPER))
        e.needPublish()
    }

    fun matches(e: ElectricalDataLoggerElement): Boolean = e.pause && e.logs.size() == 3 &&
        e.logs.maxValue == 12f && e.logs.minValue == -12f && e.logs.samplingPeriod == 2f

    fun isPrint(stack: ItemStack): Boolean = !stack.isEmpty && Eln.instance.dataLogsPrintDescriptor.checkSameItemStack(stack)

    fun verifyPrint(stack: ItemStack) {
        check(isPrint(stack) && stack.count == 1)
        val chart = PrintedLogData(stack.tagCompound)
        check(chart.size == 3 && chart.value(0) == 12.0 && chart.value(2) == -12.0 && chart.duration == 4.0)
    }
}
