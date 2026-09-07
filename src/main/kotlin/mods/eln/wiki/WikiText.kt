package mods.eln.wiki

import mods.eln.gui.Gui
import mods.eln.gui.IGuiObject
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/** Wrapped document text, with its true height included in scroll bounds. */
class WikiText(private var x: Int, private var y: Int, text: String, width: Int) : IGuiObject {
    private val lines = Minecraft.getInstance().font.split(Component.literal(text), width.coerceAtLeast(1))
    override fun idraw(mouseX: Int, mouseY: Int, partial: Float) {
        val g = Gui.graphics() ?: return
        lines.forEachIndexed { index, line ->
            g.drawString(Minecraft.getInstance().font, line, x, y + index * 11, 0xFFE3EDF0.toInt(), false)
        }
    }
    override fun getYMax() = y + lines.size * 11
    override fun translate(dx: Int, dy: Int) { x += dx; y += dy }
    override fun idraw2(x: Int, y: Int) {}
    override fun ikeyTyped(key: Char, code: Int) = false
    override fun imouseClicked(x: Int, y: Int, code: Int) {}
    override fun imouseMove(x: Int, y: Int) {}
    override fun imouseMovedOrUp(x: Int, y: Int, code: Int) {}
}
