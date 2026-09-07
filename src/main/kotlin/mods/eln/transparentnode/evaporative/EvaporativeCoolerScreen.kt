package mods.eln.transparentnode.evaporative

import mods.eln.i18n.I18N.tr
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import java.util.Locale
import kotlin.math.roundToInt

/** Native Minecraft menu. All actions go through the vanilla server-validated menu protocol. */
class EvaporativeCoolerScreen(menu: EvaporativeCoolerMenu, inventory: Inventory) :
    AbstractContainerScreen<EvaporativeCoolerMenu>(menu, inventory, Component.literal(tr("EC-240 Evaporative Heat Sink"))) {
    private val modeButtons = mutableListOf<Button>()
    private lateinit var redstoneButton: Button
    init { imageWidth = 320; imageHeight = 238 }
    override fun init() {
        super.init()
        modeButtons.clear()
        fun button(x: Int, y: Int, w: Int, id: Int, text: String, tip: String) =
            addRenderableWidget(Button.builder(Component.literal(text)) {
                minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId, id)
            }.bounds(leftPos + x, topPos + y, w, 18).tooltip(Tooltip.create(Component.literal(tip))).build())
        val modes = listOf(tr("Off"), tr("Dry"), tr("Auto"), tr("Wet"))
        val tips = listOf(tr("Disable the motor and pump. Passive cooling remains."),
            tr("Run the fan without using water."),
            tr("Use the fan above target +2 C and water above target +5 C."),
            tr("Request wet cooling continuously. Safety interlocks still apply."))
        modes.forEachIndexed { i, text -> modeButtons.add(button(166 + i * 36, 64, 33, i, text, tips[i])) }
        button(166, 106, 32, 10, "-5", tr("Lower target by 5 C."))
        button(203, 106, 32, 11, "-1", tr("Lower target by 1 C."))
        button(240, 106, 32, 12, "+1", tr("Raise target by 1 C."))
        button(277, 106, 32, 13, "+5", tr("Raise target by 5 C."))
        button(166, 146, 32, 20, "-10", tr("Lower maximum fan speed by 10 percent."))
        button(277, 146, 32, 21, "+10", tr("Raise maximum fan speed by 10 percent."))
        redstoneButton = button(166, 187, 143, 30, tr("Redstone: ignored"),
            tr("Cycle ignored, require signal, and require no signal."))
        updateControls()
    }
    override fun containerTick() { super.containerTick(); updateControls() }
    private fun updateControls() {
        modeButtons.forEachIndexed { i, b -> b.active = menu.values.get(0) != i }
        redstoneButton.message = Component.literal(when (menu.values.get(3)) {
            1 -> tr("Redstone: high")
            2 -> tr("Redstone: low")
            else -> tr("Redstone: ignored")
        })
    }
    override fun renderBg(g: GuiGraphics, partial: Float, mouseX: Int, mouseY: Int) {
        val x = leftPos; val y = topPos
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF101C26.toInt())
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + 3, 0xFF54C7C1.toInt())
        g.fill(x + 8, y + 49, x + 155, y + 211, 0xFF1A2A36.toInt())
        g.fill(x + 160, y + 49, x + 312, y + 211, 0xFF1A2A36.toInt())
        g.fill(x + 8, y + 30, x + 312, y + 44, 0xFF233846.toInt())
        // Water level gauge with discrete quarter marks. Independent of text, readable at a glance.
        g.fill(x + 12, y + 68, x + 28, y + 151, 0xFF0B141D.toInt())
        val fill = (80.0 * menu.values.get(9).coerceIn(0, 4000) / 4000).roundToInt()
        g.fill(x + 14, y + 148 - fill, x + 26, y + 148, 0xFF489DE0.toInt())
        for (i in 0..4) g.fill(x + 11, y + 148 - i*20, x + 17, y + 149 - i*20, 0xFFB7D4E6.toInt())
        val selected = menu.values.get(0)
        if (selected in 0..3) g.fill(x + 165 + selected*36, y + 63, x + 200 + selected*36, y + 83, 0xFF54C7C1.toInt())
    }
    private fun decimal(v: Double) = String.format(Locale.ROOT, "%.1f", v)
    override fun renderLabels(g: GuiGraphics, mouseX: Int, mouseY: Int) {
        val d = menu.values
        fun text(s: String, x: Int, y: Int, color: Int = 0xD7E6EE) { g.drawString(font, s, x, y, color, false) }
        text(title.string, 12, 12, 0xFFFFFF)
        val status = d.get(4)
        text(EvaporativeStatus.text(status), 12, 33, if (status >= 4) 0xF2C078 else 0x80DBCB)
        text(tr("THERMAL / WATER"), 16, 55, 0x8BB5CB)
        text(tr("Surface: %1$ C", decimal(d.get(5)/10.0)), 34, 68, 0xFFFFFF)
        text(tr("Air: %1$ C", decimal(d.get(6)/10.0)), 34, 84)
        text(tr("Humidity: %1$%", decimal(d.get(7)/10.0)), 34, 100)
        text(tr("Wet bulb: %1$ C", decimal(d.get(8)/10.0)), 34, 116)
        text(tr("Water: %1$ mB", d.get(9)), 34, 137, 0x82BDF0)
        text(tr("Use: %1$ mB/s", decimal(d.get(12)/1000.0)), 12, 158)
        text(tr("Net cooling: %1$ W", d.get(11)), 12, 175, 0x80DBCB)
        text(tr("Input: %1$ W / %2$ V", d.get(10), (d.get(14)/10.0).roundToInt()), 12, 192)
        text(tr("OPERATING MODE"), 166, 53, 0x8BB5CB)
        text(tr("Target: %1$ C", d.get(1)), 166, 92, 0xFFFFFF)
        text(tr("Fan limit: %1$%", d.get(2)), 166, 133)
        text(tr("%1$% actual", d.get(13)), 203, 151, 0x80DBCB)
        text(tr("AUTOMATION"), 166, 174, 0x8BB5CB)
        // Keep the full localized port/safety descriptions within the minimum viewport.
        fun footer(s: String, y: Int) {
            val scale = minOf(1f, 304f / font.width(s).coerceAtLeast(1))
            g.pose().pushPose()
            g.pose().translate(8f, y.toFloat(), 0f)
            g.pose().scale(scale, scale, 1f)
            g.drawString(font, s, 0, 0, 0x9CB5C5, false)
            g.pose().popPose()
        }
        footer(tr("Heat: copper sides | Power: front/rear | Water: top/bottom"), 215)
        footer(if (d.get(17) == 1) tr("Outdoor airflow verified. Wet-bulb value is an estimate.")
            else tr("Wet mode needs an unobstructed path to sky within 8 blocks."), 228)
    }
    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(g, mouseX, mouseY, partialTick)
        val x = mouseX - leftPos; val y = mouseY - topPos
        if (x in 8..154 && y in 171..184) g.renderTooltip(font, Component.literal(
            tr("Sensible: %1$ W; evaporation: %2$ W; motor heat: %3$ W. Negative net means warming.",
                menu.values.get(16), menu.values.get(15), menu.values.get(10))), mouseX, mouseY)
    }
}
