package mods.eln.sixnode.electricaldatalogger

import mods.eln.i18n.I18N.tr
import mods.eln.misc.tagCompound
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import kotlin.math.*

/** Native GUI pixels, independent of world lighting, legacy GL transforms and GUI scale. */
class PrintedLogScreen(val chart: PrintedLogData) : Screen(Component.literal(tr("Monitor printout"))) {
    var renderedFrames = 0
        private set
    var plotLeft = 0; private set
    var plotTop = 0; private set
    var plotWidth = 0; private set
    var plotHeight = 0; private set
    private var left = 0
    private var top = 0
    private var panelWidth = 0
    private var panelHeight = 0

    override fun init() {
        panelWidth = min(520, width - 16)
        panelHeight = min(330, height - 16)
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2
        plotLeft = left + 12; plotTop = top + 53
        plotWidth = (panelWidth - 108).coerceAtLeast(40)
        plotHeight = (panelHeight - 112).coerceAtLeast(20)
        addRenderableWidget(Button.builder(Component.literal(tr("Done"))) { onClose() }
            .bounds(left + panelWidth / 2 - 40, top + panelHeight - 26, 80, 20).build())
    }

    override fun isPauseScreen() = false
    override fun renderBackground(g: GuiGraphics, x: Int, y: Int, partial: Float) {}

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, partial: Float) {
        g.fill(0, 0, width, height, 0xA010161C.toInt())
        g.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, 0xFF6D6757.toInt())
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xFFF3EAD2.toInt())
        g.drawString(font, title, left + 12, top + 12, 0xFF292B2E.toInt(), false)
        val summary = tr("%1$ samples | %2$ per sample", chart.size, PrintedLogData.timeLabel(chart.period.toDouble()))
        g.drawString(font, font.plainSubstrByWidth(summary, panelWidth - 24), left + 12, top + 29, 0xFF55534D.toInt(), false)
        g.fill(plotLeft, plotTop, plotLeft + plotWidth + 1, plotTop + plotHeight + 1, 0xFFFFFBEE.toInt())
        for (n in 0..4) {
            val y = plotTop + plotHeight * n / 4
            g.hLine(plotLeft, plotLeft + plotWidth, y, 0xFFD5CEBC.toInt())
            val x = plotLeft + plotWidth * n / 4
            g.vLine(x, plotTop, plotTop + plotHeight, 0xFFD5CEBC.toInt())
        }
        for (n in 0..2) {
            g.drawString(font, font.plainSubstrByWidth(chart.label(1f - n / 2f), 78),
                plotLeft + plotWidth + 5, plotTop + plotHeight * n / 2 - 4, 0xFF343B40.toInt(), false)
        }
        val range = chart.maximum.toDouble() - chart.minimum
        val zero = if (range == 0.0) Double.NaN else chart.maximum / range
        if (chart.zeroLine && zero > 0 && zero < 1)
            g.hLine(plotLeft, plotLeft + plotWidth, plotTop + (zero * plotHeight).roundToInt(), 0xFF889999.toInt())
        fun px(i: Int) = plotLeft + plotWidth - (i.toDouble() / (chart.size - 1).coerceAtLeast(1) * plotWidth).roundToInt()
        fun py(i: Int) = plotTop + ((1 - chart.fraction(i)) * plotHeight).roundToInt()
        g.enableScissor(plotLeft, plotTop, plotLeft + plotWidth + 1, plotTop + plotHeight + 1)
        try {
            for (i in 0 until chart.size) {
                val x = px(i); val y = py(i)
                val next = (i + 1).coerceAtMost(chart.size - 1)
                val dx = px(next) - x; val dy = py(next) - y
                val steps = max(abs(dx), abs(dy)).coerceAtLeast(1)
                for (s in 0..steps) {
                    val sx = x + (dx * s.toDouble() / steps).roundToInt()
                    val sy = y + (dy * s.toDouble() / steps).roundToInt()
                    g.fill(sx, sy, sx + 1, sy + 1, 0xFFB13C32.toInt())
                }
            }
        } finally { g.disableScissor() }
        if (chart.size == 0) {
            val empty = tr("No recorded samples")
            g.drawString(font, empty, plotLeft + (plotWidth - font.width(empty)) / 2, plotTop + plotHeight / 2, 0xFF343B40.toInt(), false)
        }
        g.drawString(font, PrintedLogData.timeLabel(chart.duration), plotLeft, plotTop + plotHeight + 7, 0xFF343B40.toInt(), false)
        val latest = tr("Latest")
        g.drawString(font, latest, plotLeft + plotWidth - font.width(latest), plotTop + plotHeight + 7, 0xFF343B40.toInt(), false)
        super.render(g, mouseX, mouseY, partial)
        if (chart.size > 0 && mouseX in plotLeft..plotLeft + plotWidth && mouseY in plotTop..plotTop + plotHeight) {
            val i = ((plotLeft + plotWidth - mouseX).toDouble() / plotWidth * (chart.size - 1)).roundToInt().coerceIn(0, chart.size - 1)
            g.renderTooltip(font, Component.literal(tr("%1$ ago: %2$", PrintedLogData.timeLabel(chart.age(i)), chart.label(chart.fraction(i).toFloat()))), mouseX, mouseY)
        }
        renderedFrames++
    }

    companion object {
        @JvmStatic fun open(stack: ItemStack) {
            Minecraft.getInstance().setScreen(PrintedLogScreen(PrintedLogData(stack.tagCompound)))
        }
    }
}
