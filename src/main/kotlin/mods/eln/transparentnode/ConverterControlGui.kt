package mods.eln.transparentnode

import mods.eln.gui.*
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Utils
import mods.eln.sim.power.*
import net.minecraft.world.inventory.AbstractContainerMenu

interface ConverterScreenAccess {
    val controlSettings: ConverterControlSettings
    val protection: Boolean
    val displayedInputVolts: Double
    val displayedOutputVolts: Double
    val displayedPower: Double
    val displayedStatus: String
    val variableControl: Boolean
    val allowVoltageControl: Boolean
    val allowProtectionControl: Boolean
    fun sendMode(code: Int)
    fun sendValue(value: Double)
    fun sendMapping(name: String)
    fun sendProtection(value: Boolean)
}

/** Existing native UI toolkit. Physical winding slots and terminal orientation stay unchanged. */
open class ConverterControlGui(menu: AbstractContainerMenu, private val peer: ConverterScreenAccess) : GuiContainerEln(menu) {
    private lateinit var modeButton: GuiButtonEln
    private lateinit var mappingButton: GuiButtonEln
    private lateinit var protectionButton: GuiButtonEln
    private lateinit var valueField: GuiTextFieldEln
    private var shownMode: ConverterInputMode? = null

    override fun newHelper() = GuiHelperContainer(this, 176, 264, 8, 182)

    override fun initGui() {
        super.initGui()
        modeButton = newGuiButton(8, 52, 160, "")
        valueField = newGuiTextField(8, 78, 82, 16)
        mappingButton = newGuiButton(8, 102, 160, "")
        protectionButton = newGuiButton(8, 126, 160, "")
        valueField.setComment(0, tr("Ratio: 1/256 to 256. Voltage is the internal converter target, before winding loss."))
        shownMode = null
        updateControls()
    }

    private fun updateControls() {
        val settings = peer.controlSettings
        val mode = settings.mode
        modeButton.displayString = when (mode) {
            ConverterInputMode.EXTERNAL_SIGNAL -> tr("External signal")
            ConverterInputMode.MANUAL_RATIO -> tr("Manual ratio")
            ConverterInputMode.MANUAL_VOLTAGE -> tr("Internal voltage target")
        }
        modeButton.enabled = peer.variableControl
        valueField.enabled = peer.variableControl && mode != ConverterInputMode.EXTERNAL_SIGNAL
        if (shownMode != mode) {
            valueField.text = (if (mode == ConverterInputMode.MANUAL_VOLTAGE) settings.manualVoltage else settings.manualRatio).toString()
            shownMode = mode
        }
        mappingButton.displayString = if (settings.mapping == MappingVersion.LEGACY_LINEAR_V1) tr("Signal: legacy linear") else tr("Signal: logarithmic")
        mappingButton.enabled = peer.variableControl && mode == ConverterInputMode.EXTERNAL_SIGNAL
        protectionButton.displayString = if (peer.protection) tr("Current protection: on") else tr("Current protection: off")
        protectionButton.visible = peer.allowProtectionControl
    }

    override fun guiObjectEvent(obj: IGuiObject) {
        when (obj) {
            modeButton -> {
                val modes = ConverterInputMode.values().filter { peer.allowVoltageControl || it != ConverterInputMode.MANUAL_VOLTAGE }
                peer.sendMode(modes[(modes.indexOf(peer.controlSettings.mode) + 1) % modes.size].code)
            }
            mappingButton -> peer.sendMapping(if (peer.controlSettings.mapping == MappingVersion.LEGACY_LINEAR_V1)
                MappingVersion.LOGARITHMIC_V2.name else MappingVersion.LEGACY_LINEAR_V1.name)
            protectionButton -> peer.sendProtection(!peer.protection)
        }
    }

    override fun textFieldNewValue(textField: GuiTextFieldEln, value: String) {
        if (textField !== valueField) return
        val number = value.toDoubleOrNull() ?: return
        if (number.isFinite()) peer.sendValue(number)
    }

    override fun postDraw(f: Float, x: Int, y: Int) {
        super.postDraw(f, x, y)
        updateControls()
        drawString(8, 8, tr("Input"))
        drawString(132, 8, tr("Output"))
        drawString(8, 150, tr("IN %1$ OUT %2$", Utils.plotVolt("", peer.displayedInputVolts).trim(),
            Utils.plotVolt("", peer.displayedOutputVolts).trim()))
        drawString(8, 162, tr("Power: %1$", Utils.plotPower("", peer.displayedPower).trim()))
        drawString(8, 174, converterStatusText(peer.displayedStatus))
    }
}

internal fun converterStatusText(status: String): String = when (status) {
    "RUNNING" -> tr("Running")
    "LIMITED" -> tr("Current, power or ratio limited")
    "NO_INPUT" -> tr("No usable input")
    "OUTPUT_HIGH" -> tr("Output already above target")
    "OVERLOAD" -> tr("Outside operating limits")
    "INVALID_NETWORK" -> tr("Unsupported electrical network")
    "NON_CONVERGENT" -> tr("Network did not converge; output open")
    else -> tr("Check core, windings and settings")
}
