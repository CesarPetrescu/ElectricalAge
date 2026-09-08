package mods.eln.transparentnode.evaporative

import mods.eln.gui.GuiHelper
import mods.eln.i18n.I18N.tr
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import java.util.Locale
import kotlin.math.roundToInt

/** ELN's grey instrument panel, retaining the native server-validated menu protocol. */
class EvaporativeCoolerScreen(menu: EvaporativeCoolerMenu, inventory: Inventory) :
    AbstractContainerScreen<EvaporativeCoolerMenu>(menu, inventory, Component.literal(tr("Evaporative Heat Sink"))) {
    private val modeButtons = mutableListOf<Button>()
    private var redstoneButton: Button? = null
    private var details = false
    init { imageWidth = 248; imageHeight = 228 }

    override fun init() {
        super.init()
        modeButtons.clear()
        redstoneButton = null
        fun button(x: Int, y: Int, w: Int, id: Int, text: String, tip: String): Button =
            addRenderableWidget(Button.builder(Component.literal(text)) {
                minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId, id)
            }.bounds(leftPos + x, topPos + y, w, 20).tooltip(Tooltip.create(Component.literal(tip))).build())
        fun pageButton(x: Int, w: Int, label: String) =
            addRenderableWidget(Button.builder(Component.literal(label)) {
                details = !details
                rebuildWidgets()
            }.bounds(leftPos + x, topPos + 196, w, 20).build())
        if (details) {
            pageButton(156, 80, tr("Back"))
            return
        }
        val modes = listOf(tr("Off"), tr("Dry"), tr("Auto"), tr("Wet"))
        val tips = listOf(tr("Disable the motor and pump. Passive cooling remains."),
            tr("Run the fan without using water."),
            tr("Use the fan above target +2 C and water above target +5 C."),
            tr("Request wet cooling continuously. Safety interlocks still apply."))
        modes.forEachIndexed { i, text -> modeButtons.add(button(12 + i * 57, 112, 53, i, text, tips[i])) }
        button(120, 140, 26, 10, "-5", tr("Lower target by 5 C."))
        button(150, 140, 26, 11, "-1", tr("Lower target by 1 C."))
        button(180, 140, 26, 12, "+1", tr("Raise target by 1 C."))
        button(210, 140, 26, 13, "+5", tr("Raise target by 5 C."))
        button(150, 168, 40, 20, "-10", tr("Lower maximum fan speed by 10 percent."))
        button(196, 168, 40, 21, "+10", tr("Raise maximum fan speed by 10 percent."))
        redstoneButton = button(12, 196, 136, 30, tr("Redstone: ignored"),
            tr("Cycle ignored, require signal, and require no signal."))
        pageButton(156, 80, tr("Details"))
        updateControls()
    }

    override fun containerTick() { super.containerTick(); updateControls() }
    private fun updateControls() {
        modeButtons.forEachIndexed { i, b -> b.active = menu.values.get(0) != i }
        redstoneButton?.message = Component.literal(when (menu.values.get(3)) {
            1 -> tr("Redstone: high")
            2 -> tr("Redstone: low")
            else -> tr("Redstone: ignored")
        })
    }

    override fun renderBg(g: GuiGraphics, partial: Float, mouseX: Int, mouseY: Int) {
        val x = leftPos; val y = topPos
        // The same frame/corners as GuiContainerEln, not a separate dashboard skin.
        GuiHelper.drawPanel(g, x, y, imageWidth, imageHeight)
        if (!details) {
            // Match GuiVerticalProgressBar's inset edge. Only water uses a colour.
            g.fill(x + 12, y + 30, x + 29, y + 88, 0xFF404040.toInt())
            g.fill(x + 13, y + 31, x + 28, y + 87, 0xFF606060.toInt())
            g.fill(x + 14, y + 32, x + 27, y + 86, 0xFF808080.toInt())
            val fill = (54.0 * menu.values.get(9).coerceIn(0, 4000) / 4000).roundToInt()
            g.fill(x + 14, y + 86 - fill, x + 27, y + 86, 0xFF36578B.toInt())
            for (i in 1..3) g.fill(x + 12, y + 86 - i*13, x + 17, y + 87 - i*13, 0xFF404040.toInt())
            val selected = menu.values.get(0)
            if (selected in 0..3) g.fill(x + 16 + selected*57, y + 133, x + 61 + selected*57, y + 134, 0xFF555555.toInt())
        }
    }

    private fun decimal(v: Double) = String.format(Locale.ROOT, "%.1f", v)
    private fun power(v: Int) = if (kotlin.math.abs(v.toLong()) >= 1000) decimal(v / 1000.0) + " kW" else "$v W"
    private fun fitted(s: String, max: Int): String =
        if (font.width(s) <= max) s else font.plainSubstrByWidth(s, (max - font.width("...")).coerceAtLeast(0)) + "..."

    override fun renderLabels(g: GuiGraphics, mouseX: Int, mouseY: Int) {
        val d = menu.values
        fun text(s: String, x: Int, y: Int, max: Int = 224, color: Int = 0x404040) =
            g.drawString(font, fitted(s, max), x, y, color, false)
        text(title.string, 12, 11, 187)
        text("240 V", 207, 11, 29)
        if (details) {
            fun row(label: String, value: String, y: Int) {
                text(label, 12, y, 144)
                val shown = fitted(value, 74)
                text(shown, 236 - font.width(shown), y, 74, 0x202020)
            }
            row(tr("Intake temperature"), decimal(d.get(6)/10.0) + " C", 32)
            row(tr("Relative humidity"), decimal(d.get(7)/10.0) + "%", 46)
            row(tr("Wet bulb (estimate)"), decimal(d.get(8)/10.0) + " C", 60)
            row(tr("Water use"), decimal(d.get(12)/1000.0) + " mB/s", 74)
            row(tr("Sensible cooling"), power(d.get(16)), 88)
            row(tr("Evaporative cooling"), power(d.get(15)), 102)
            row(tr("Motor heat / input"), power(d.get(10)), 116)
            row(tr("Supply voltage"), decimal(d.get(14)/10.0) + " V", 130)
            row(tr("Actual fan speed"), "${d.get(13)}%", 144)
            row(tr("Outdoor airflow"), if (d.get(17)==1) tr("Verified") else tr("Not verified"), 158)
            row(tr("Wet operation"), if (d.get(18)==1) tr("Enabled") else tr("Inhibited"), 172)
            text(tr("Hover for connections."), 12, 202, 140)
            return
        }
        text(tr("Temperature: %1$ C", decimal(d.get(5)/10.0)), 38, 32, 198, 0x202020)
        text(tr("Net cooling: %1$", power(d.get(11))), 38, 47, 198)
        text(tr("Input: %1$", power(d.get(10))), 38, 62, 198)
        text(tr("Water: %1$ / 4.0 B", decimal(d.get(9)/1000.0)), 38, 77, 198)
        val status = d.get(4)
        text(EvaporativeStatus.text(status), 12, 96, 224, if (status >= 4) 0x813A24 else 0x404040)
        text(tr("Target: %1$ C", d.get(1)), 12, 146, 102)
        text(tr("Fan limit: %1$%", d.get(2)), 12, 174, 130)
    }

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(g, mouseX, mouseY, partialTick)
        val x = mouseX-leftPos; val y = mouseY-topPos
        val d = menu.values
        val tip = when {
            !details && x in 12..236 && y in 93..107 -> EvaporativeStatus.text(d.get(4))
            !details && x in 12..29 && y in 30..88 -> tr("%1$ / 4000 mB. Fill or drain with a main-hand bucket.", d.get(9))
            !details && x in 38..236 && y in 44..58 -> tr("Sensible: %1$ W; evaporation: %2$ W; motor heat: %3$ W. Negative net means warming.", d.get(16), d.get(15), d.get(10))
            details && x in 12..152 && y in 196..216 -> tr("Heat: copper sides | Power: front/rear | Water: top/bottom")
            details && x in 12..236 && y in 56..71 -> tr("Wet-bulb value is an estimate, not a guaranteed surface temperature.")
            details && x in 12..236 && y in 155..169 -> tr("Wet mode needs an unobstructed path to sky within 8 blocks.")
            else -> null
        }
        if (tip != null) g.renderTooltip(font, font.split(Component.literal(tip), 220), mouseX, mouseY)
    }
}
