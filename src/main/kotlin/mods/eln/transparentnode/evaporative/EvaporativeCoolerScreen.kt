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
    init { imageWidth = 360; imageHeight = 260 }
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
        modes.forEachIndexed { i, text -> modeButtons.add(button(190 + i * 39, 66, 36, i, text, tips[i])) }
        button(190, 116, 34, 10, "-5", tr("Lower target by 5 C."))
        button(230, 116, 34, 11, "-1", tr("Lower target by 1 C."))
        button(270, 116, 34, 12, "+1", tr("Raise target by 1 C."))
        button(310, 116, 34, 13, "+5", tr("Raise target by 5 C."))
        button(190, 157, 34, 20, "-10", tr("Lower maximum fan speed by 10 percent."))
        button(310, 157, 34, 21, "+10", tr("Raise maximum fan speed by 10 percent."))
        redstoneButton = button(190, 197, 154, 30, tr("Redstone: ignored"),
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
        g.fill(x + 8, y + 49, x + 180, y + 218, 0xFF1A2A36.toInt())
        g.fill(x + 184, y + 49, x + 352, y + 218, 0xFF1A2A36.toInt())
        g.fill(x + 8, y + 30, x + 352, y + 44, 0xFF233846.toInt())
        // Water level gauge with discrete quarter marks. Independent of text, readable at a glance.
        g.fill(x + 16, y + 72, x + 34, y + 158, 0xFF0B141D.toInt())
        val fill = (80.0 * menu.values.get(9).coerceIn(0, 4000) / 4000).roundToInt()
        g.fill(x + 18, y + 155 - fill, x + 32, y + 155, 0xFF489DE0.toInt())
        for (i in 0..4) g.fill(x + 15, y + 155 - i*20, x + 20, y + 156 - i*20, 0xFFB7D4E6.toInt())
        val selected = menu.values.get(0)
        if (selected in 0..3) g.fill(x + 189 + selected*39, y + 65, x + 227 + selected*39, y + 85, 0xFF54C7C1.toInt())
    }
    private fun decimal(v: Double) = String.format(Locale.ROOT, "%.1f", v)
    override fun renderLabels(g: GuiGraphics, mouseX: Int, mouseY: Int) {
        val d = menu.values
        fun text(s: String, x: Int, y: Int, color: Int = 0xD7E6EE) { g.drawString(font, s, x, y, color, false) }
        text(title.string, 12, 12, 0xFFFFFF)
        val status = d.get(4)
        text(EvaporativeStatus.text(status), 12, 33, if (status >= 4) 0xF2C078 else 0x80DBCB)
        text(tr("THERMAL / WATER"), 16, 55, 0x8BB5CB)
        text(tr("Surface: %1$ C", decimal(d.get(5)/10.0)), 42, 73, 0xFFFFFF)
        text(tr("Air: %1$ C", decimal(d.get(6)/10.0)), 42, 89)
        text(tr("Humidity: %1$%", decimal(d.get(7)/10.0)), 42, 105)
        text(tr("Wet bulb: %1$ C", decimal(d.get(8)/10.0)), 42, 121)
        text(tr("Water: %1$ mB", d.get(9)), 42, 141, 0x82BDF0)
        text(tr("Use: %1$ mB/s", decimal(d.get(12)/1000.0)), 16, 165)
        text(tr("Net cooling: %1$ W", d.get(11)), 16, 181, 0x80DBCB)
        text(tr("Input: %1$ W / %2$ V", d.get(10), (d.get(14)/10.0).roundToInt()), 16, 197)
        text(tr("OPERATING MODE"), 190, 55, 0x8BB5CB)
        text(tr("Target: %1$ C", d.get(1)), 190, 99, 0xFFFFFF)
        text(tr("Fan limit: %1$%", d.get(2)), 190, 142)
        text(tr("%1$% actual", d.get(13)), 233, 162, 0x80DBCB)
        text(tr("AUTOMATION"), 190, 183, 0x8BB5CB)
        text(tr("Heat: copper sides | Power: front/rear | Water: top/bottom"), 12, 228, 0x9CB5C5)
        text(if (d.get(17) == 1) tr("Outdoor airflow verified. Wet-bulb value is an estimate.")
            else tr("Wet mode needs an unobstructed path to sky within 8 blocks."), 12, 244, 0x9CB5C5)
    }
    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(g, mouseX, mouseY, partialTick)
        val x = mouseX - leftPos; val y = mouseY - topPos
        if (x in 8..178 && y in 176..192) g.renderTooltip(font, Component.literal(
            tr("Sensible: %1$ W; evaporation: %2$ W; motor heat: %3$ W. Negative net means warming.",
                menu.values.get(16), menu.values.get(15), menu.values.get(10))), mouseX, mouseY)
    }
}
