package mods.eln.sixnode.electricaldatalogger

import mods.eln.Eln
import mods.eln.devtest.ContractReport
import mods.eln.misc.tagCompound
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import java.nio.file.Files

/** GitHub-only opt-in client suite: item use, nonempty model, rendered pixels, scale and empty pages. */
class PrintedLogClientChecks {
    private val report = ContractReport("monitor-print-client")
    private var step = 0
    private var scale = 1
    private var oldScale = 0
    private var oldWidth = 0
    private var oldHeight = 0
    private var resizeTicks = 0
    private var oldStack = ItemStack.EMPTY
    private var finished = false
    private var page: PrintedLogScreen? = null
    private var print = ItemStack.EMPTY

    fun tick(mc: Minecraft): Boolean {
        if (finished) return true
        if (step == 0) {
            if (oldWidth == 0) {
                oldWidth = mc.window.width; oldHeight = mc.window.height
                mc.window.setWindowed(1280, 960)
                return false // let GLFW deliver the resize before measuring GUI pixels
            }
            if (mc.window.width < 1280 || mc.window.height < 960) {
                check(++resizeTicks < 100) { "Client window did not resize for three distinct GUI scales" }
                return false
            }
            oldScale = mc.options.guiScale().get()
            oldStack = mc.player!!.mainHandItem.copy()
            print = Eln.instance.dataLogsPrintDescriptor.newItemStack()
            print.tagCompound = MonitorPrintChecks.sampleTag()
            report.test("eln:data_logger_print", "visible-paper-item-model") {
                val model = mc.itemRenderer.getModel(print, mc.level, mc.player, 0)
                check(!model.isCustomRenderer)
                check(model.getQuads(null, null, RandomSource.create(0)).isNotEmpty())
                check(model.particleIcon.contents().name().toString() == "minecraft:item/paper")
            }
            mc.player!!.setItemInHand(InteractionHand.MAIN_HAND, print)
            mc.options.guiScale().set(scale); mc.resizeDisplay()
            report.test("eln:data_logger_print", "right-click-opens-saved-chart") {
                print.item.use(mc.level!!, mc.player!!, InteractionHand.MAIN_HAND)
                check(mc.screen is PrintedLogScreen)
            }
            page = mc.screen as? PrintedLogScreen
            check(page != null)
            step = 1
            return false
        }
        val current = page!!
        if (current.renderedFrames < 3) return false
        val name = if (step == 1) "scale-$scale" else "empty"
        report.test("eln:data_logger_print", "rendered-chart-$name") {
            if (step == 1) check(mc.window.guiScale == scale.toDouble()) { "Requested scale $scale was clamped to ${mc.window.guiScale}" }
            check(current.plotLeft >= 0 && current.plotTop >= 0)
            check(current.plotLeft + current.plotWidth < current.width)
            check(current.plotTop + current.plotHeight < current.height)
            Screenshot.takeScreenshot(mc.mainRenderTarget).use { image ->
                var ink = 0
                for (x in current.plotLeft..current.plotLeft + current.plotWidth) {
                    for (y in current.plotTop..current.plotTop + current.plotHeight) {
                        val rgba = image.getPixelRGBA(((x + .5) * mc.window.guiScale).toInt(), ((y + .5) * mc.window.guiScale).toInt()) and 0xFFFFFF
                        if (rgba == 0x323CB1) ink++
                    }
                }
                if (step == 1) check(ink > 40) { "Chart trace invisible: $ink red pixels" }
                else check(ink == 0) { "Empty chart displays stale trace" }
                val target = mc.gameDirectory.toPath().resolve("screenshots/smoke-monitor-print-$name.png")
                Files.createDirectories(target.parent); image.writeToFile(target)
            }
        }
        if (step == 1 && ++scale <= 3) {
            mc.options.guiScale().set(scale); mc.resizeDisplay()
            PrintedLogScreen.open(print); page = mc.screen as PrintedLogScreen
        } else if (step == 1) {
            step = 2
            val empty = Eln.instance.dataLogsPrintDescriptor.newItemStack()
            PrintedLogScreen.open(empty); page = mc.screen as PrintedLogScreen
        } else {
            mc.setScreen(null)
            mc.player!!.setItemInHand(InteractionHand.MAIN_HAND, oldStack)
            mc.window.setWindowed(oldWidth, oldHeight)
            mc.options.guiScale().set(oldScale); mc.resizeDisplay()
            report.write(true); finished = true
            check(report.failures == 0) { "Printed chart contracts failed: ${report.failures}" }
        }
        report.write(finished)
        return finished
    }
}
