package mods.eln.sixnode.powersocket

import mods.eln.gui.GuiHelperContainer
import mods.eln.gui.GuiScreenEln
import mods.eln.gui.GuiTextFieldEln
import mods.eln.gui.IGuiObject
import mods.eln.i18n.I18N.tr
import net.minecraft.world.entity.player.Player
import net.minecraft.world.Container

class PowerSocketGui(
    private val render: PowerSocketRender,
    @Suppress("UNUSED_PARAMETER") player: Player?,
    @Suppress("UNUSED_PARAMETER") inventory: Container?
) :
    GuiScreenEln() {

        var device: GuiTextFieldEln? = null
    override fun initGui() {
        super.initGui()

        device = newGuiTextField(8, 8, 138)
        device?.setText(render.channel)
        device?.setComment(0, tr("Specify the power channel"))
    }

    override fun newHelper(): GuiHelperContainer {
        return GuiHelperContainer(this, 154, 30, 0, 0)
    }

    override fun guiObjectEvent(`object`: IGuiObject) {
        if (`object` === device) {
            render.clientSetString(PowerSocketElement.setChannelId, device?.text?: "")
        }
        super.guiObjectEvent(`object`)
    }
}
