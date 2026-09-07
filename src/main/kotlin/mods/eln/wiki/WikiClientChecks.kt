package mods.eln.wiki

import com.mojang.blaze3d.platform.InputConstants
import mods.eln.Eln
import mods.eln.devtest.ContractReport
import mods.eln.gui.Gui
import mods.eln.gui.IGuiObject
import mods.eln.misc.Utils
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.core.registries.BuiltInRegistries
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

/** Real rendered-frame and input contracts, run ONLY by the opted-in headless client smoke suite. */
class WikiClientChecks {
    private val report = ContractReport("wiki-client")
    private var step = 0
    private var wait = 0
    private var scale = 1
    private var originalScale = 0
    private var originalHideGui = false
    private var root: Root? = null
    private var savedScroll = 0f
    private var finished = false
    private var awaitedPage: Default? = null
    private var requiredFrames = 0

    private fun test(name: String, body: () -> Unit) {
        report.test("eln:wiki", name, body)
        report.write(false)
    }

    private fun pressP(down: Boolean) {
        KeyMapping.set(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_P), down)
        Eln.clientKeyHandler.onKeyInput(null)
    }

    private fun shot(mc: Minecraft, name: String) {
        // Synchronous capture/write: the artifact cannot race the final test result.
        val file = mc.gameDirectory.toPath().resolve("screenshots/smoke-wiki-$name.png")
        java.nio.file.Files.createDirectories(file.parent)
        Screenshot.takeScreenshot(mc.mainRenderTarget).use { it.writeToFile(file) }
    }

    fun tick(mc: Minecraft): Boolean {
        if (finished) return true
        if (mc.screen === awaitedPage && awaitedPage != null && awaitedPage!!.renderedFrames < requiredFrames) return false
        if (wait++ < 12) return false
        wait = 0
        when (step) {
            0 -> {
                originalScale = mc.options.guiScale().get()
                originalHideGui = mc.options.hideGui
                mc.options.hideGui = false
                mc.setScreen(null)
                test("P-key-opens-guide") {
                    pressP(false); pressP(true); pressP(false)
                    check(mc.screen is Root)
                }
                test("catalogue-covers-every-ELN-item-once") {
                    val entries = WikiContent.groups().values.flatten()
                    val ids = entries.map { BuiltInRegistries.ITEM.getKey(it.item) }
                    val expected = BuiltInRegistries.ITEM.keySet().filter { it.namespace == "eln" }
                    check(ids.size == ids.toSet().size)
                    check(ids.containsAll(expected)) { "Missing ELN entries: ${expected - ids.toSet()}" }
                }
                mc.options.guiScale().set(scale)
                mc.resizeDisplay()
                step++
            }
            1 -> {
                shot(mc, "contents-scale-$scale")
                test("layout-and-visible-content-scale-$scale") {
                    val page = mc.screen as Root
                    val v = page.extender
                    check(page.left >= 0 && page.top >= 0)
                    check(v.posX >= page.left && v.posY >= page.top)
                    check(v.posX + v.w <= page.width && v.posY + v.h <= page.height)
                    check(v.maxScroll() > 0)
                    check(v.objectList.filterIsInstance<GuiItemStack>().all { it.posX + 18 <= v.contentWidth() })
                    // The first item's slot must really be drawn at the document's screen position.
                    val first = v.objectList.filterIsInstance<GuiItemStack>().first()
                    Screenshot.takeScreenshot(mc.mainRenderTarget).use { image ->
                        val x = ((v.posX + first.posX - .5) * mc.window.guiScale).toInt()
                        val y = ((v.posY + first.posY + 4) * mc.window.guiScale).toInt()
                        val actual = image.getPixelRGBA(x, y) and 0xFFFFFF
                        check(actual == 0x665B46) {
                            "First slot clipped/misplaced: scale=$scale pixel=($x,$y) ABGR=${actual.toString(16)} frames=${page.renderedFrames}"
                        }
                    }
                }
                if (++scale <= 3) {
                    mc.options.guiScale().set(scale); mc.resizeDisplay()
                } else {
                    root = mc.screen as Root
                    test("wheel-and-end-key-reach-last-entry") {
                        val page = root!!
                        val v = page.extender
                        check(page.mouseScrolled(v.posX + 20.0, v.posY + 20.0, 0.0, -3.0))
                        check(v.sliderPosition < 0)
                        page.keyPressed(GLFW.GLFW_KEY_END, 0, 0)
                        check(-v.sliderPosition == v.maxScroll().toFloat())
                        val last = v.objectList.filterIsInstance<GuiItemStack>().last()
                        check(last.posY + 18 + v.sliderPosition <= v.h)
                        savedScroll = v.sliderPosition
                    }
                    step++
                }
            }
            2 -> {
                shot(mc, "contents-scrolled")
                test("clipped-items-cannot-be-clicked") {
                    val page = root!!
                    val v = page.extender
                    val hidden = v.objectList.filterIsInstance<GuiItemStack>().first { it.posY + v.sliderPosition < 0 }
                    v.imouseClicked(v.posX + hidden.posX + 4,
                        (v.posY + hidden.posY + v.sliderPosition + 4).roundToInt(), 0)
                    check(mc.screen === page)
                }
                test("visible-item-click-opens-recipes") {
                    val page = root!!
                    val v = page.extender
                    val item = v.objectList.filterIsInstance<GuiItemStack>().last()
                    page.mouseClicked(v.posX + item.posX + 4.0, v.posY + item.posY + v.sliderPosition + 4.0, 0)
                    check(mc.screen is ItemDefault)
                }
                step++
            }
            3 -> {
                shot(mc, "item")
                test("back-restores-catalogue-scroll") {
                    mc.screen!!.onClose()
                    check(mc.screen === root && root!!.extender.sliderPosition == savedScroll)
                }
                test("native-search-keeps-P-and-shows-all-results") {
                    val page = root!!
                    page.mouseClicked(page.searchText.x + 4.0, page.searchText.y + 4.0, 0)
                    page.charTyped('p', 0)
                    pressP(true); pressP(false)
                    check(mc.screen === page && page.searchText.value == "p")
                    page.searchText.value = "eln:"
                    val count = page.extender.objectList.filterIsInstance<GuiItemStack>().size
                    check(count > 56 && count == WikiContent.search("eln:").size)
                }
                step++
            }
            4 -> {
                shot(mc, "search")
                test("empty-search-and-multiword-paste") {
                    val page = root!!
                    page.searchText.value = "no-such-eln-item-xyz"
                    check(page.extender.objectList.none { it is GuiItemStack })
                    check(page.extender.maxScroll() == 0)
                    page.searchText.value = "Low Voltage Cable"
                    check(page.extender.objectList.filterIsInstance<GuiItemStack>().isNotEmpty())
                }
                mc.setScreen(ItemDefault(Eln.findItemStack("48V Macerator", 1), root))
                step++
            }
            5 -> {
                test("machine-recipes-include-output-and-scroll") {
                    val page = mc.screen as ItemDefault
                    check(page.extender.maxScroll() > 0)
                    check(page.extender.objectList.filterIsInstance<GuiItemStack>().size > 10)
                    check(page.extender.objectList.filterIsInstance<GuiItemStack>().all { it.posX + 18 <= page.extender.contentWidth() })
                }
                shot(mc, "macerator")
                val math = WikiContent.groups().values.flatten().first {
                    Utils.getItemObject(it) is mods.eln.sixnode.electricalmath.ElectricalMathDescriptor
                }
                mc.setScreen(ItemDefault(math, root))
                step++
            }
            6 -> {
                test("logic-chip-help-is-scrollable") {
                    val page = mc.screen as ItemDefault
                    check(page.extender.maxScroll() > 0)
                    page.keyPressed(GLFW.GLFW_KEY_PAGE_DOWN, 0, 0)
                    check(page.extender.sliderPosition < 0)
                }
                shot(mc, "logic-help")
                mc.setScreen(ClipProbe())
                step++
            }
            7 -> {
                test("framebuffer-clipping-inside-outside-and-after-item-render") {
                    val v = (mc.screen as ClipProbe).extender
                    Screenshot.takeScreenshot(mc.mainRenderTarget).use { image ->
                        fun pixel(x: Int, y: Int) = image.getPixelRGBA(
                            ((x + .5) * mc.window.guiScale).toInt(),
                            ((y + .5) * mc.window.guiScale).toInt()) and 0xFFFFFF
                        check(pixel(v.posX + 80, v.posY + 40) == 0xC020D0) { "Visible document was clipped away" }
                        check(pixel(v.posX - 2, v.posY + 40) != 0xC020D0) { "Document escaped left clip" }
                        check(pixel(v.posX + 80, v.posY + v.h + 2) != 0xC020D0) { "Document escaped bottom clip" }
                    }
                }
                shot(mc, "clip-contract")
                val wire = mods.eln.sixnode.electricalcable.UtilityCableDescriptor.allDescriptors()
                    .first { it.insulated && !it.melted && it.conductorCount == 8 }
                mc.setScreen(ItemDefault(wire.newItemStack(), root))
                step++
            }
            8 -> {
                test("wire-page-shows-machine-inputs-and-output") {
                    val page = mc.screen as ItemDefault
                    val icons = page.extender.objectList.filterIsInstance<GuiItemStack>()
                    val machine = Eln.findItemStack("Wire Insulator", 1)
                    check(icons.any { net.minecraft.world.item.ItemStack.isSameItem(it.stack, machine) })
                    check(page.extender.maxScroll() > 0)
                }
                shot(mc, "wire-production")
                mc.setScreen(null)
                mc.options.guiScale().set(originalScale)
                mc.options.hideGui = originalHideGui
                mc.resizeDisplay()
                report.write(true)
                finished = true
                check(report.failures == 0) { "Wiki client contracts failed: ${report.failures}" }
            }
        }
        awaitedPage = mc.screen as? Default
        requiredFrames = (awaitedPage?.renderedFrames ?: 0) + 2
        return finished
    }

    private class ClipProbe : Default(null) {
        override fun initGui() {
            super.initGui()
            extender.add(GuiItemStack(8, 8, Eln.findItemStack("48V Macerator", 1), helper))
            extender.add(object : IGuiObject {
                override fun idraw(x: Int, y: Int, partial: Float) {
                    Gui.graphics().fill(-1000, 32, 2000, extender.h + 500, 0xFFD020C0.toInt())
                }
                override fun getYMax() = extender.h
                override fun translate(x: Int, y: Int) {}
                override fun idraw2(x: Int, y: Int) {}
                override fun ikeyTyped(key: Char, code: Int) = false
                override fun imouseClicked(x: Int, y: Int, code: Int) {}
                override fun imouseMove(x: Int, y: Int) {}
                override fun imouseMovedOrUp(x: Int, y: Int, code: Int) {}
            })
        }
    }
}
