package mods.eln.wiki

import mods.eln.Eln
import mods.eln.gui.Gui
import mods.eln.gui.IGuiObject
import mods.eln.i18n.I18N.tr
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

/** Small reproducible exercises; no world edits and no commands/cheats hidden behind guide links. */
object CircuitLessons {
    fun stack(name: String) = if (name == "Data Logger Print") Eln.instance.dataLogsPrintDescriptor.newItemStack() else Eln.findItemStack(name, 1)
    data class Lesson(val id: String, val title: String, val items: List<String>, val paragraphs: List<String>)
    @JvmStatic fun all() = listOf(
        Lesson("meter", tr("Measure between two terminals"), listOf("MultiMeter", "AllMeter", "Low Voltage Cable"), listOf(
            tr("1. Hold a Multimeter or AllMeter. Normal right-click keeps the existing machine readings. Sneak-click a connector to select reference A, then sneak-click a second connector B."),
            tr("2. Read B minus A, not just B relative to ground. Both voltages are read again at the second click. A remains selected for further comparisons; sneak-use in air clears it."),
            tr("3. Aim near the intended terminal on a face. Multicore cables require a single-core breakout; the meter will not silently choose a core. Probes must be in the same dimension, loaded and within 64 blocks of one another."),
            tr("4. A near-zero current can mean a switch is open, a return is missing, or the load is idle. Inspect both sides of the switch and the return path. The meter does not claim that every idle circuit is broken."))),
        Lesson("battery", tr("Understand a floating 12 V battery"), listOf("MultiMeter", "Ground Cable"), listOf(
            tr("1. Place a charged 12 V battery. Locate its positive and negative cable terminals; normal right-click with the meter also reports terminal voltage and both ground-referenced voltages."),
            tr("2. Select the negative terminal as A and the positive terminal as B. If the terminal-to-terminal output is 12 V, the meter reads +12 V even when A is -6 V and B is +6 V relative to ground. Reversing the probes reads -12 V."),
            tr("3. Connecting only the negative terminal to ELN ground establishes approximately 0 V there and +12 V on the positive terminal. Do not ground both terminals: that shorts the battery."),
            tr("4. Under load, terminal voltage can fall because of internal and cable resistance. Nominal voltage is a rating, not a promise of exactly that reading at every charge level."))),
        Lesson("print", tr("Record and print a monitor chart"), listOf("Signal Cable", "Signal Source", "Data Logger Print"), listOf(
            tr("1. Place a monitor and connect a Signal Source through Signal Cable to its input connector. Use a low signal level first; do not feed a power cable directly into a signal input."),
            tr("2. In the monitor, choose a sampling period, unit and minimum/maximum display values. Those labels scale the signal graph; selecting volts does not turn a monitor into a power-voltage sensor."),
            tr("3. Change the source level and wait for several samples. Pause the monitor to freeze the trace. Put vanilla paper in its input slot, leave the print output empty, then press Print."),
            tr("4. One paper produces one saved printout. Take it and right-click in air to read it. The newest sample is on the right. Hover over the line to inspect sample values and ages."),
            tr("5. Change or reset the monitor: the old print must stay unchanged. Paper with no samples opens an explicit empty-chart page. Printed data survives saving and reloading."))),
        Lesson("load", tr("Check a simple powered load"), listOf("Electrical Source", "Low Voltage Cable", "Creative Power Resistor", "Ground Cable"), listOf(
            tr("Creative-mode exercise: place Electrical Source, Low Voltage Cable, a Creative Power Resistor and Ground Cable in a line on the same mounting surface. Rotate the resistor so its two electrical terminals face the source and the return."),
            tr("Set the source to 12 V and the resistor to 12 ohms. With negligible cable resistance, expect approximately 1 A and 12 W at the load. Real cable resistance reduces the load voltage and current, and dissipates some power as heat."),
            tr("Measure across the resistor with probes A and B. Breaking the return path stops useful current even if a source-side cable still shows voltage relative to ground. Restore the return before drawing conclusions about the generator."),
            tr("For a survival build, replace the creative source and load with a battery and a voltage-matched lamp or machine, checking the bulb, installed cable and switch first. Never test a source by shorting it directly to ground.")))
    )

    @JvmStatic fun addLinks(page: Default, start: Int): Int {
        var y = start
        for (lesson in all()) {
            page.extender.add(LessonLink(y, lesson, page))
            y += 18
        }
        return y + 8
    }

    private class LessonLink(private var y: Int, private val lesson: Lesson, private val parent: Default) : IGuiObject {
        private var x = 8
        override fun idraw(mx: Int, my: Int, partial: Float) {
            val g = Gui.graphics() ?: return
            val text = Minecraft.getInstance().font.plainSubstrByWidth(lesson.title, parent.extender.contentWidth() - 16)
            g.drawString(Minecraft.getInstance().font, text, x, y, 0xFF72CDB3.toInt(), false)
        }
        override fun imouseClicked(mx: Int, my: Int, button: Int) {
            if (button == 0 && mx >= x && mx < parent.extender.contentWidth() - 8 && my in y until y + 14)
                Minecraft.getInstance().setScreen(CircuitLessonPage(lesson, parent))
        }
        override fun getYMax() = y + 14
        override fun translate(dx: Int, dy: Int) { x += dx; y += dy }
        override fun idraw2(x: Int, y: Int) {}
        override fun ikeyTyped(key: Char, code: Int) = false
        override fun imouseMove(x: Int, y: Int) {}
        override fun imouseMovedOrUp(x: Int, y: Int, code: Int) {}
    }
}

class CircuitLessonPage(val lesson: CircuitLessons.Lesson, previous: Screen?) : Default(previous) {
    override fun initGui() {
        super.initGui()
        var y = 8
        fun paragraph(text: String) {
            val block = WikiText(8, y, text, extender.contentWidth() - 16)
            extender.add(block); y = block.yMax + 12
        }
        paragraph(lesson.title)
        lesson.items.forEachIndexed { i, name ->
            extender.add(GuiItemStack(8 + i * 22, y, CircuitLessons.stack(name), helper))
        }
        y += 30
        lesson.paragraphs.forEach(::paragraph)
    }
}
