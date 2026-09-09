package mods.eln.transparentnode

import mods.eln.sim.power.*

import mods.eln.sim.mna.component.Resistor

import mods.eln.Eln
import mods.eln.cable.CableRenderDescriptor
import mods.eln.cable.CableRenderType
import mods.eln.generic.GenericItemBlockUsingDamageDescriptor
import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.generic.GenericItemUsingDamageSlot
import mods.eln.gui.GuiContainerEln
import mods.eln.gui.GuiHelperContainer
import mods.eln.gui.ISlotSkin
import mods.eln.i18n.I18N.tr
import mods.eln.item.CaseItemDescriptor
import mods.eln.item.ConfigCopyToolDescriptor
import mods.eln.item.FerromagneticCoreDescriptor
import mods.eln.item.IConfigurable
import mods.eln.misc.BasicContainer
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.LRDUMask
import mods.eln.misc.Obj3D
import mods.eln.misc.PhysicalInterpolator
import mods.eln.misc.RealisticEnum
import mods.eln.misc.SlewLimiter
import mods.eln.misc.Utils
import mods.eln.misc.VoltageLevelColor
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.six.SixNodeEntity
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeDescriptor
import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.node.transparent.TransparentNodeElementInventory
import mods.eln.node.transparent.TransparentNodeElementRender
import mods.eln.node.transparent.TransparentNodeEntity
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.sim.ThermalLoad
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.misc.IRootSystemPreStepProcess
import mods.eln.sim.mna.misc.MnaConst
import mods.eln.sim.mna.SubSystem
import mods.eln.sim.mna.state.State
import mods.eln.sim.nbt.NbtElectricalGateInput
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sim.nbt.NbtThermalLoad
import mods.eln.sim.process.destruct.VoltageStateWatchDog
import mods.eln.sim.process.destruct.WorldExplosion
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import mods.eln.sound.LoopedSound
import mods.eln.wiki.Data
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.Container
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.client.gl.GL11
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import mods.eln.misc.getBlockEntity
import mods.eln.misc.isNothing


class OneWayDcDcDescriptor(
    name: String,
    objM: Obj3D,
    coreM: Obj3D,
    casingM: Obj3D,
    val mode: OneWayDcDcMode,
    val minimalLoadToHum: Float = 0.5f,
    val guiTexture: String = if (mode == OneWayDcDcMode.BOOST || mode == OneWayDcDcMode.BUCK || mode == OneWayDcDcMode.BOOST_BUCK) {
        "vdcdc.png"
    } else {
        "dcdc.png"
    }
) : TransparentNodeDescriptor(name, OneWayDcDcElement::class.java, OneWayDcDcRender::class.java) {

    companion object {
        const val COIL_SCALE = 4.0f
        const val COIL_SCALE_LIMIT = 16
        const val COIL_BASE_HEIGHT = 0.031f
        const val COIL_OUTER_HALF_WIDTH = 0.0868f
        const val COIL_CORE_HALF_WIDTH = 0.0625f
        const val COIL_CENTER_Y = 0.0155f
        const val COIL_CENTER_Z = -0.3125f
        const val COIL_STACK_MIN_Y = -0.1575f
        const val COIL_STACK_MAX_Y = 0.25f
        const val COIL_STACK_CENTER_Y = (COIL_STACK_MIN_Y + COIL_STACK_MAX_Y) * 0.5f
        const val COIL_STACK_HEIGHT = COIL_STACK_MAX_Y - COIL_STACK_MIN_Y
        const val MAX_RATIO = 256.0
        const val MIN_RATIO = 1.0 / 256.0
        const val MAX_VARIABLE_RATIO = 50.0
        const val MIN_VARIABLE_RATIO = 1.0 / 50.0
    }

    val variable: Boolean
        get() = mode == OneWayDcDcMode.BOOST || mode == OneWayDcDcMode.BUCK || mode == OneWayDcDcMode.BOOST_BUCK

    val isolated: Boolean
        get() = mode == OneWayDcDcMode.ISOLATION

    private var main: Obj3D.Obj3DPart? = objM.getPart("main")
    private var core: Obj3D.Obj3DPart? = coreM.getPart("fero")
    private var coil: Obj3D.Obj3DPart? = objM.getPart("sbire")
    private var casing: Obj3D.Obj3DPart? = casingM.getPart("Case")
    private var casingLeftDoor: Obj3D.Obj3DPart? = casingM.getPart("DoorL")
    private var casingRightDoor: Obj3D.Obj3DPart? = casingM.getPart("DoorR")

    init {
        voltageLevelColor = VoltageLevelColor.Neutral
    }

    override fun setParent(item: Item, damage: Int) {
        super.setParent(item, damage)
        Data.addWiring(newItemStack())
    }

    override fun addInformation(itemStack: ItemStack, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)
        Collections.addAll(list, *tr("Moves power from the input side\nto the output side only.").split("\n").toTypedArray())
        if (variable) {
            Collections.addAll(list, *tr("Choose a manual ratio or voltage target,\nor use an external signal.").split("\n").toTypedArray())
        }
        if (isolated) {
            Collections.addAll(list, *tr("Front and back connections are isolated\nground references for each side.").split("\n").toTypedArray())
        }
    }

    override fun addRealismContext(list: MutableList<String>?): RealisticEnum {
        list?.add(tr("This DC/DC uses an ideal power sink and source to move power in one direction."))
        return RealisticEnum.IDEAL
    }

    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType, item: ItemStack, helper: IItemRenderer.ItemRendererHelper): Boolean {
        return type != IItemRenderer.ItemRenderType.INVENTORY
    }

    override fun handleRenderType(item: ItemStack, type: IItemRenderer.ItemRenderType): Boolean = true

    override fun renderItem(type: IItemRenderer.ItemRenderType, item: ItemStack, vararg data: Any) {
        if (type == IItemRenderer.ItemRenderType.INVENTORY) {
            super.renderItem(type, item, *data)
        } else {
            draw(core, 1, 4, 1.0f, 1.0f, false, 0f)
        }
    }

    fun draw(
        core: Obj3D.Obj3DPart?,
        priCableNbr: Int,
        secCableNbr: Int,
        primaryThickness: Float,
        secondaryThickness: Float,
        hasCasing: Boolean,
        doorOpen: Float
    ) {
        main?.draw()
        core?.draw()
        if (core != null) {
            drawCoils(priCableNbr, false, primaryThickness)
            drawCoils(secCableNbr, true, secondaryThickness)
        }

        if (hasCasing) {
            casing?.draw()
            casingLeftDoor?.draw(-doorOpen * 90, 0f, 1f, 0f)
            casingRightDoor?.draw(doorOpen * 90, 0f, 1f, 0f)
        }
    }

    private fun drawCoils(count: Int, secondary: Boolean, thickness: Float) {
        if (count == 0) return
        val wireScale = thickness.coerceIn(0.05f, 1.85f)
        val wireHeight = COIL_BASE_HEIGHT * wireScale
        val gap = wireHeight * 0.5f
        val pitch = wireHeight + gap
        val displayedCount = min(count, ((COIL_STACK_HEIGHT + gap) / pitch).toInt().coerceAtLeast(1))
        val firstCenter = -pitch * (displayedCount - 1) / 2f
        val radialScale = (COIL_CORE_HALF_WIDTH + wireHeight) / COIL_OUTER_HALF_WIDTH
        GL11.glPushMatrix()
        if (secondary) GL11.glRotatef(180f, 0f, 1f, 0f)
        for (idx in 0 until displayedCount) {
            GL11.glPushMatrix()
            GL11.glTranslatef(0f, COIL_STACK_CENTER_Y + firstCenter + pitch * idx, COIL_CENTER_Z)
            GL11.glScalef(radialScale, wireScale, radialScale)
            GL11.glTranslatef(0f, -COIL_CENTER_Y, -COIL_CENTER_Z)
            coil?.draw()
            GL11.glPopMatrix()
        }
        GL11.glPopMatrix()
    }
}

class OneWayDcDcElement(
    transparentNode: TransparentNode,
    descriptor: TransparentNodeDescriptor
) : TransparentNodeElement(transparentNode, descriptor), IConfigurable, OneWayDcDcAccess {
    private val oneWayDescriptor = descriptor as OneWayDcDcDescriptor
    override val mode get() = oneWayDescriptor.mode
    override var protectedMode = true
    override var converterStatus = OneWayDcDcStatus.UNCONFIGURED


    override val isolated: Boolean
        get() = oneWayDescriptor.isolated

    override val primaryLoad = NbtElectricalLoad("primaryLoad")
    override val secondaryLoad = NbtElectricalLoad("secondaryLoad")
    override val primaryConversionLoad = NbtElectricalLoad("primaryConversionLoad")
    override val secondaryConversionLoad = NbtElectricalLoad("secondaryConversionLoad")
    private val primaryWindingResistor = Resistor(primaryLoad, primaryConversionLoad)
    private val secondaryWindingResistor = Resistor(secondaryLoad, secondaryConversionLoad)

    override val primaryReferenceLoad = NbtElectricalLoad("primaryReferenceLoad")
    override val secondaryReferenceLoad = NbtElectricalLoad("secondaryReferenceLoad")
    val control = NbtElectricalGateInput("control")
    val controlSettings = ConverterControlSettings()

    override val inputSink = VoltageSource("inputSink")
    override val outputSource = VoltageSource("outputSource")
    override val inventory = TransparentNodeElementInventory(4, 64, this)
    private val primaryThermalLoad = WindingThermalLoad("primaryThermalLoad")
    private val secondaryThermalLoad = WindingThermalLoad("secondaryThermalLoad")
    private val primaryThermalProcess = DcDcWindingThermalProcess(
        owner = this,
        inventory = inventory,
        thermalLoad = primaryThermalLoad,
        slot = OneWayDcDcContainer.primaryCableSlotId,
        windingResistor = primaryWindingResistor,
        label = "Primary",
        onMelted = { computeInventory(); reconnect() }
    )
    private val secondaryThermalProcess = DcDcWindingThermalProcess(
        owner = this,
        inventory = inventory,
        thermalLoad = secondaryThermalLoad,
        slot = OneWayDcDcContainer.secondaryCableSlotId,
        windingResistor = secondaryWindingResistor,
        label = "Secondary",
        onMelted = { computeInventory(); reconnect() }
    )
    private val transferProcess = OneWayDcDcProcess(this)


    override var primaryMeltCurrent = 0.0
    override var secondaryMeltCurrent = 0.0
    var ratioControl = 1.0
    override var activeRatio = 1.0
    override var movedPower = 0.0
    override var populated = false

    private val primaryVoltageWatchdog = VoltageStateWatchDog(primaryLoad, if (isolated) primaryReferenceLoad else null)
    private val secondaryVoltageWatchdog = VoltageStateWatchDog(secondaryLoad, if (isolated) secondaryReferenceLoad else null)

    init {
        electricalLoadList.add(primaryLoad)
        electricalLoadList.add(secondaryLoad)
        electricalLoadList.add(primaryConversionLoad)
        electricalLoadList.add(secondaryConversionLoad)
        electricalComponentList.add(primaryWindingResistor)
        electricalComponentList.add(secondaryWindingResistor)
        electricalProcessList.add(primaryThermalProcess.sample)
        electricalProcessList.add(secondaryThermalProcess.sample)
        if (oneWayDescriptor.isolated) {
            electricalLoadList.add(primaryReferenceLoad)
            electricalLoadList.add(secondaryReferenceLoad)
        }
        if (oneWayDescriptor.variable) electricalLoadList.add(control)
        electricalComponentList.add(inputSink)
        electricalComponentList.add(outputSource)
        electricalProcessList.add(IProcess {
            movedPower = outputSource.power.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        })
        thermalLoadList.add(primaryThermalLoad)
        thermalLoadList.add(secondaryThermalLoad)
        slowProcessList.add(primaryThermalProcess)
        slowProcessList.add(secondaryThermalProcess)
        primaryThermalLoad.setAsSlow()
        secondaryThermalLoad.setAsSlow()

        val exp = WorldExplosion(this).machineExplosion()
        slowProcessList.add(primaryVoltageWatchdog.setDestroys(exp))
        slowProcessList.add(secondaryVoltageWatchdog.setDestroys(exp))
        if (isolated) {
            listOf(primaryLoad, secondaryLoad, primaryReferenceLoad, secondaryReferenceLoad).forEach {
                slowProcessList.add(VoltageStateWatchDog(it).setNominalVoltage(120_000.0).setDestroys(exp))
            }
        }
        slowProcessList.add(NodePeriodicPublishProcess(node!!, 1.0, .5))
    }

    override fun connectJob() {
        Eln.simulator.addThermalSlowProcess(primaryThermalProcess.deliver)
        Eln.simulator.addThermalSlowProcess(secondaryThermalProcess.deliver)
        Eln.simulator.mna.addProcess(transferProcess)
        super.connectJob()
    }

    override fun disconnectJob() {
        primaryThermalProcess.flush()
        secondaryThermalProcess.flush()
        Eln.simulator.removeThermalSlowProcess(primaryThermalProcess.deliver)
        Eln.simulator.removeThermalSlowProcess(secondaryThermalProcess.deliver)
        super.disconnectJob()
        Eln.simulator.mna.removeProcess(transferProcess)
    }

    override fun initialize() {
        inputSink.connectTo(primaryConversionLoad, if (oneWayDescriptor.isolated) primaryReferenceLoad else null)
        outputSource.connectTo(secondaryConversionLoad, if (oneWayDescriptor.isolated) secondaryReferenceLoad else null)
        computeInventory()
        connect()
    }

    override fun getElectricalLoad(side: Direction, lrdu: LRDU): ElectricalLoad? {
        if (lrdu != LRDU.Down) return null
        return when (side) {
            front.left() -> primaryLoad
            front.right() -> secondaryLoad
            front -> if (oneWayDescriptor.isolated) primaryReferenceLoad else if (oneWayDescriptor.variable) control else null
            front.back() -> if (oneWayDescriptor.isolated) secondaryReferenceLoad else if (oneWayDescriptor.variable) control else null
            else -> null
        }
    }

    override fun getThermalLoad(side: Direction, lrdu: LRDU): ThermalLoad? {
        if (lrdu != LRDU.Down) return null
        return when (side) {
            front.left() -> primaryThermalLoad
            front.right() -> secondaryThermalLoad
            front -> if (oneWayDescriptor.isolated) primaryThermalLoad else null
            front.back() -> if (oneWayDescriptor.isolated) secondaryThermalLoad else null
            else -> null
        }
    }

    override fun thermoMeterString(side: Direction): String {
        return when (side) {
            front.left() -> plotAmbientCelsius("T", primaryThermalLoad.temperatureCelsius)
            front.right() -> plotAmbientCelsius("T", secondaryThermalLoad.temperatureCelsius)
            else -> tr(
                "P: %1$ S: %2$",
                plotAmbientCelsius("", primaryThermalLoad.temperatureCelsius).trim(),
                plotAmbientCelsius("", secondaryThermalLoad.temperatureCelsius).trim()
            )
        }
    }

    override fun getConnectionMask(side: Direction, lrdu: LRDU): Int {
        if (lrdu != LRDU.Down) return 0
        return when (side) {
            front.left(), front.right() -> NodeBase.maskElectricalPower
            front, front.back() -> when {
                oneWayDescriptor.isolated -> NodeBase.maskElectricalPower
                oneWayDescriptor.variable -> NodeBase.maskElectricalInputGate
                else -> 0
            }
            else -> 0
        }
    }

    override fun multiMeterString(side: Direction): String {
        return when (side) {
            front.left() -> Utils.plotVolt("IN:", primaryLoad.voltage - if (isolated) primaryReferenceLoad.voltage else 0.0) + Utils.plotAmpere("I:", -inputSink.current)
            front.right() -> Utils.plotVolt("OUT:", secondaryLoad.voltage - if (isolated) secondaryReferenceLoad.voltage else 0.0) + Utils.plotAmpere("I:", outputSource.current)
            else -> Utils.plotVolt("IN:", primaryLoad.voltage - if (isolated) primaryReferenceLoad.voltage else 0.0) + Utils.plotVolt(" OUT:", secondaryLoad.voltage - if (isolated) secondaryReferenceLoad.voltage else 0.0) + Utils.plotPower(" P:", movedPower)
        }
    }

    override fun hasGui(): Boolean = true

    override fun newContainer(side: Direction, player: Player): AbstractContainerMenu {
        return OneWayDcDcContainer(player, inventory, oneWayDescriptor.variable)
    }

    override fun onBlockActivated(player: Player, side: Direction, vx: Float, vy: Float, vz: Float): Boolean = false

    override fun getLightOpacity(): Float = 1.0f

    override fun onGroundedChangedByClient() {
        super.onGroundedChangedByClient()
        computeInventory()
        reconnect()
    }

    override fun inventoryChange(inventory: Container?) {
        disconnect()
        computeInventory()
        connect()
        needPublish()
    }

    private fun computeInventory() {
        val primaryCable = inventory.getItem(OneWayDcDcContainer.primaryCableSlotId)
        val secondaryCable = inventory.getItem(OneWayDcDcContainer.secondaryCableSlotId)
        val core = inventory.getItem(OneWayDcDcContainer.ferromagneticSlotId)
        val primaryWinding = dcDcWinding(primaryCable)
        val secondaryWinding = dcDcWinding(secondaryCable)

        primaryVoltageWatchdog.setNominalVoltage(dcDcWindingVoltageRating(primaryCable))
        secondaryVoltageWatchdog.setNominalVoltage(dcDcWindingVoltageRating(secondaryCable))
        primaryMeltCurrent = dcDcWindingMeltCurrent(primaryCable)
        secondaryMeltCurrent = dcDcWindingMeltCurrent(secondaryCable)
        primaryThermalProcess.configure(primaryCable)
        secondaryThermalProcess.configure(secondaryCable)

        val constructionStatus = if (oneWayDescriptor.mode == OneWayDcDcMode.FIXED || controlSettings.mapping == MappingVersion.LOGARITHMIC_V2) {
            dcDcFlexibleConstructionStatus(core, primaryCable, secondaryCable)
        } else {
            dcDcConstructionStatus(core, primaryCable, secondaryCable)
        }
        val coreDescriptor = GenericItemUsingDamageDescriptor.getDescriptor(
            core, FerromagneticCoreDescriptor::class.java
        ) as? FerromagneticCoreDescriptor
        val coreFactor = coreDescriptor?.cableMultiplicator ?: 1.0
        val hasValidConstruction = constructionStatus.operational

        if (primaryWinding == null || !hasValidConstruction) {
            primaryLoad.highImpedance()
            if (oneWayDescriptor.isolated) primaryReferenceLoad.highImpedance()
        } else {
            primaryLoad.serialResistance = MnaConst.noImpedance
            if (oneWayDescriptor.isolated) primaryReferenceLoad.serialResistance = MnaConst.noImpedance
        }

        if (secondaryWinding == null || !hasValidConstruction) {
            secondaryLoad.highImpedance()
            if (oneWayDescriptor.isolated) secondaryReferenceLoad.highImpedance()
        } else {
            secondaryLoad.serialResistance = MnaConst.noImpedance
            if (oneWayDescriptor.isolated) secondaryReferenceLoad.serialResistance = MnaConst.noImpedance
        }

        populated = primaryWinding != null && secondaryWinding != null && hasValidConstruction
        ratioControl = if (oneWayDescriptor.mode == OneWayDcDcMode.FIXED &&
            populated && primaryWinding != null && secondaryWinding != null
        ) {
            secondaryWinding.amount / primaryWinding.amount
        } else {
            1.0
        }
    }

    override fun computeRatio(): Double {
        if (!populated || !controlSettings.valid) return Double.NaN
        return when (mode) {
            OneWayDcDcMode.FIXED -> ratioControl.coerceIn(1.0 / 256.0, 256.0)
            OneWayDcDcMode.ISOLATION -> 1.0
            OneWayDcDcMode.BOOST -> controlSettings.ratio(ConverterKind.BOOST, control.normalized)
            OneWayDcDcMode.BUCK -> controlSettings.ratio(ConverterKind.BUCK, control.normalized)
            OneWayDcDcMode.BOOST_BUCK -> controlSettings.ratio(ConverterKind.BUCK_BOOST, control.normalized)
        }
    }

    override val requestedOutputVoltage: Double?
        get() = if (oneWayDescriptor.variable) controlSettings.voltageTarget() else null
    override val maxInputVoltage get() = dcDcWindingVoltageRating(inventory.getItem(OneWayDcDcContainer.primaryCableSlotId))
    override val maxOutputVoltage get() = dcDcWindingVoltageRating(inventory.getItem(OneWayDcDcContainer.secondaryCableSlotId))

    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        try {
            stream.writeShort(dcDcRenderedWindingCount(inventory.getItem(0)))
            stream.writeShort(dcDcRenderedWindingCount(inventory.getItem(1)))
            stream.writeFloat(dcDcRenderedWindingThickness(inventory.getItem(OneWayDcDcContainer.primaryCableSlotId)))
            stream.writeFloat(dcDcRenderedWindingThickness(inventory.getItem(OneWayDcDcContainer.secondaryCableSlotId)))
            Utils.serialiseItemStack(stream, inventory.getItem(OneWayDcDcContainer.ferromagneticSlotId))
            Utils.serialiseItemStack(stream, inventory.getItem(OneWayDcDcContainer.primaryCableSlotId))
            Utils.serialiseItemStack(stream, inventory.getItem(OneWayDcDcContainer.secondaryCableSlotId))
            node!!.lrduCubeMask.getTranslate(front.down()).serialize(stream)
            val load = if (primaryMeltCurrent != 0.0 && secondaryMeltCurrent != 0.0) {
                Utils.limit(max(-inputSink.current / primaryMeltCurrent, outputSource.current / secondaryMeltCurrent).toFloat(), 0f, 1f)
            } else {
                0f
            }
            stream.writeFloat(load)
            stream.writeBoolean(!inventory.getItem(OneWayDcDcContainer.CasingSlotId).isNothing())
            controlSettings.writeWire(stream)
            stream.writeBoolean(protectedMode)
            stream.writeDouble(primaryLoad.voltage - if (isolated) primaryReferenceLoad.voltage else 0.0)
            stream.writeDouble(secondaryLoad.voltage - if (isolated) secondaryReferenceLoad.voltage else 0.0)
            stream.writeDouble(movedPower)
            stream.writeUTF(converterStatus.name)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun getWaila(): Map<String, String> {
        val info = linkedMapOf<String, String>()
        info[tr("Converter status")] = when (converterStatus) {
            OneWayDcDcStatus.UNCONFIGURED -> tr("Check core, windings and settings")
            OneWayDcDcStatus.RUNNING -> tr("Transferring power")
            OneWayDcDcStatus.LIMITED -> tr("Current, power or gain limited")
            OneWayDcDcStatus.NO_INPUT -> tr("No usable input supply")
            OneWayDcDcStatus.OUTPUT_HIGH -> tr("Output already powered; reverse transfer blocked")
            OneWayDcDcStatus.OVERLOAD -> tr("Protection tripped: operating limits")
            OneWayDcDcStatus.INVALID_NETWORK -> tr("Unsupported or singular electrical network")
            OneWayDcDcStatus.NON_CONVERGENT -> tr("Network did not converge; outputs disconnected")
        }
        info[tr("Protection")] = if (protectedMode) tr("Enabled") else tr("Legacy unprotected")
        val constructionStatus = if (oneWayDescriptor.mode == OneWayDcDcMode.FIXED || controlSettings.mapping == MappingVersion.LOGARITHMIC_V2) {
            dcDcFlexibleConstructionStatus(
                inventory.getItem(OneWayDcDcContainer.ferromagneticSlotId),
                inventory.getItem(OneWayDcDcContainer.primaryCableSlotId),
                inventory.getItem(OneWayDcDcContainer.secondaryCableSlotId)
            )
        } else {
            dcDcConstructionStatus(
                inventory.getItem(OneWayDcDcContainer.ferromagneticSlotId),
                inventory.getItem(OneWayDcDcContainer.primaryCableSlotId),
                inventory.getItem(OneWayDcDcContainer.secondaryCableSlotId)
            )
        }
        info[tr("Construction")] = dcDcConstructionWaila(constructionStatus)
        info[tr("Control mode")] = when (controlSettings.mode) {
            ConverterInputMode.EXTERNAL_SIGNAL -> tr("External signal")
            ConverterInputMode.MANUAL_RATIO -> tr("Manual ratio")
            ConverterInputMode.MANUAL_VOLTAGE -> tr("Internal voltage target")
        }
        if (!controlSettings.valid) info[tr("Control fault")] = tr("Invalid saved settings")
        info[tr("Ratio")] = Utils.plotValue(activeRatio)
        info[tr("Transferred power")] = Utils.plotPower("", movedPower)
        info[tr("Winding resistance")] = tr("Primary %1$; secondary %2$",
            Utils.plotValue(primaryWindingResistor.resistance, "ohm"), Utils.plotValue(secondaryWindingResistor.resistance, "ohm"))
        info[tr("Winding loss")] = Utils.plotPower("", primaryWindingResistor.power + secondaryWindingResistor.power)
        info[tr("Primary winding")] = windingStatus(
            inventory.getItem(OneWayDcDcContainer.primaryCableSlotId),
            -inputSink.current,
            primaryThermalLoad
        )
        info[tr("Secondary winding")] = windingStatus(
            inventory.getItem(OneWayDcDcContainer.secondaryCableSlotId),
            outputSource.current,
            secondaryThermalLoad
        )
        if (oneWayDescriptor.variable) info[tr("Control Voltage")] = Utils.plotVolt(control.voltage)
        if (Eln.config.getBooleanOrElse("ui.waila.easyMode", false) || oneWayDescriptor.variable) {
            val primaryVoltage = primaryLoad.voltage - if (oneWayDescriptor.isolated) primaryReferenceLoad.voltage else 0.0
            val secondaryVoltage = secondaryLoad.voltage - if (oneWayDescriptor.isolated) secondaryReferenceLoad.voltage else 0.0
            info[tr("Voltages")] = "\u00A7a" + Utils.plotVolt("", primaryVoltage) + " " +
                "\u00A7e" + Utils.plotVolt("", secondaryVoltage)
        }
        info[tr("Subsystem Matrix Size")] = Utils.renderDoubleSubsystemWaila(primaryLoad.subSystem, secondaryLoad.subSystem)
        return info
    }

    private fun windingStatus(stack: ItemStack?, current: Double, thermalLoad: NbtThermalLoad): String {
        val descriptor = if (stack.isNothing()) {
            null
        } else {
            ElectricalCableDescriptor.getDescriptor(
                stack,
                ElectricalCableDescriptor::class.java
            ) as? ElectricalCableDescriptor
        }
        val name = descriptor?.getName(stack) ?: tr("empty")
        return tr(
            "%1$, %2$, %3$",
            name,
            Utils.plotAmpere("", current).trim(),
            plotAmbientCelsius("", thermalLoad.temperatureCelsius).trim()
        )
    }

    override fun readFromNBT(nbt: CompoundTag) {
        controlSettings.readNbt(nbt, oneWayDescriptor.variable)
        // Existing worlds retain their unprotected behavior; fixes to shutdown/energy apply to both.
        protectedMode = nbt.contains("converterProtection") && nbt.getBoolean("converterProtection")
        super.readFromNBT(nbt)
    }

    override fun writeToNBT(nbt: CompoundTag) {
        primaryThermalProcess.flush(); secondaryThermalProcess.flush()
        controlSettings.writeNbt(nbt)
        super.writeToNBT(nbt)
        nbt.putBoolean("converterProtection", protectedMode)
    }

    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id = super.networkUnserialize(stream)
        val handled = when (id) {
            ConverterPackets.MODE -> controlSettings.setMode(stream.readInt(), oneWayDescriptor.variable)
            ConverterPackets.VALUE -> controlSettings.setValue(stream.readDouble())
            ConverterPackets.MAPPING -> controlSettings.setMapping(stream.readUTF())
            ConverterPackets.PROTECTION -> { protectedMode = stream.readBoolean(); true }
            else -> return id
        }
        if (handled) {
            computeInventory()
            needPublish()
        }
        return TransparentNodeElement.unserializeNulldId
    }

    override fun readConfigTool(compound: CompoundTag, invoker: Player) {
        if (compound.contains("converterMapping")) controlSettings.readNbt(compound, oneWayDescriptor.variable)
        if (compound.contains("converterProtection")) protectedMode = compound.getBoolean("converterProtection")

        if (ConfigCopyToolDescriptor.readGenDescriptor(compound, "primary", inventory, OneWayDcDcContainer.primaryCableSlotId, invoker))
            inventoryChange(inventory)
        if (ConfigCopyToolDescriptor.readGenDescriptor(compound, "secondary", inventory, OneWayDcDcContainer.secondaryCableSlotId, invoker))
            inventoryChange(inventory)
        if (ConfigCopyToolDescriptor.readGenDescriptor(compound, "core", inventory, OneWayDcDcContainer.ferromagneticSlotId, invoker))
            inventoryChange(inventory)
        // A controls-only copy may not modify any inventory slot.
        computeInventory()
        needPublish()
    }

    override fun writeConfigTool(compound: CompoundTag, invoker: Player) {
        controlSettings.writeNbt(compound)
        compound.putBoolean("converterProtection", protectedMode)

        ConfigCopyToolDescriptor.writeGenDescriptor(compound, "primary", inventory.getItem(OneWayDcDcContainer.primaryCableSlotId))
        ConfigCopyToolDescriptor.writeGenDescriptor(compound, "secondary", inventory.getItem(OneWayDcDcContainer.secondaryCableSlotId))
        ConfigCopyToolDescriptor.writeGenDescriptor(compound, "core", inventory.getItem(OneWayDcDcContainer.ferromagneticSlotId))
    }

}

class OneWayDcDcRender(
    tileEntity: TransparentNodeEntity,
    private val descriptor: TransparentNodeDescriptor
) : TransparentNodeElementRender(tileEntity, descriptor), ConverterScreenAccess {
    override val controlSettings = ConverterControlSettings()
    override var protection = false
    override var displayedInputVolts = 0.0
    override var displayedOutputVolts = 0.0
    override var displayedPower = 0.0
    override var displayedStatus = "UNCONFIGURED"
    override val variableControl get() = oneWayDescriptor.variable
    override val allowVoltageControl get() = oneWayDescriptor.variable
    override val allowProtectionControl get() = true
    override fun sendMode(code: Int) = clientSendInt(ConverterPackets.MODE, code)
    override fun sendValue(value: Double) = clientSendDouble(ConverterPackets.VALUE, value)
    override fun sendMapping(name: String) = clientSendString(ConverterPackets.MAPPING, name)
    override fun sendProtection(value: Boolean) = clientSendBoolean(ConverterPackets.PROTECTION, value)
    override val inventory = TransparentNodeElementInventory(4, 64, this)

    private val oneWayDescriptor = descriptor as OneWayDcDcDescriptor
    private val load = SlewLimiter(0.5f)
    private var primaryStackSize = 0
    private var secondaryStackSize = 0
    private var primaryThickness = 1.0f
    private var secondaryThickness = 1.0f
    private var priRender: CableRenderDescriptor? = null
    private var secRender: CableRenderDescriptor? = null
    private var feroPart: Obj3D.Obj3DPart? = null
    private var hasCasing = false
    private val coordinate = Coordinate(tileEntity)
    private val doorOpen = PhysicalInterpolator(0.4f, 4.0f, 0.9f, 0.05f)
    private val priConn = LRDUMask()
    private val secConn = LRDUMask()
    private val priRefConn = LRDUMask()
    private val secRefConn = LRDUMask()
    private val controlConn = LRDUMask()
    private val eConn = LRDUMask()
    private var cableRenderType: CableRenderType? = null

    init {
        addLoopedSound(object : LoopedSound("eln:transformer", coordinate(), SoundInstance.Attenuation.LINEAR) {
            override fun getVolume(): Float {
                return if (load.position > oneWayDescriptor.minimalLoadToHum)
                    0.1f * (load.position - oneWayDescriptor.minimalLoadToHum) / (1 - oneWayDescriptor.minimalLoadToHum)
                else
                    0f
            }
        })
    }

    override fun draw() {
        GL11.glPushMatrix()
        front!!.glRotateXnRef()
        oneWayDescriptor.draw(
            feroPart,
            primaryStackSize.toInt(),
            secondaryStackSize.toInt(),
            primaryThickness,
            secondaryThickness,
            hasCasing,
            doorOpen.get()
        )
        GL11.glPopMatrix()
        cableRenderType = drawCable(front!!.down(), primaryRender(), priConn, cableRenderType)
        cableRenderType = drawCable(front!!.down(), secondaryRender(), secConn, cableRenderType)
        if (oneWayDescriptor.isolated) {
            cableRenderType = drawCable(front!!.down(), primaryReferenceRender(), priRefConn, cableRenderType)
            cableRenderType = drawCable(front!!.down(), secondaryReferenceRender(), secRefConn, cableRenderType)
        }
        if (oneWayDescriptor.variable) {
            cableRenderType = drawCable(front!!.down(), Eln.instance.stdCableRenderSignal, controlConn, cableRenderType)
        }
    }

    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream)
        try {
            primaryStackSize = stream.readShort().toInt()
            secondaryStackSize = stream.readShort().toInt()
            primaryThickness = stream.readFloat()
            secondaryThickness = stream.readFloat()
            val feroStack = Utils.unserialiseItemStack(stream)
            feroPart = null
            if (!feroStack.isNothing()) {
                val feroDesc = GenericItemUsingDamageDescriptor.getDescriptor(feroStack, FerromagneticCoreDescriptor::class.java)
                if (feroDesc != null) feroPart = (feroDesc as FerromagneticCoreDescriptor).feroPart
            }
            val priStack = Utils.unserialiseItemStack(stream)
            priRender = null
            if (!priStack.isNothing()) {
                val priDesc: GenericItemBlockUsingDamageDescriptor? = ElectricalCableDescriptor.getDescriptor(priStack, ElectricalCableDescriptor::class.java)
                if (priDesc != null) priRender = (priDesc as ElectricalCableDescriptor).render
            }
            val secStack = Utils.unserialiseItemStack(stream)
            secRender = null
            if (!secStack.isNothing()) {
                val secDesc: GenericItemBlockUsingDamageDescriptor? = ElectricalCableDescriptor.getDescriptor(secStack, ElectricalCableDescriptor::class.java)
                if (secDesc != null) secRender = (secDesc as ElectricalCableDescriptor).render
            }

            eConn.deserialize(stream)
            priConn.mask = 0
            secConn.mask = 0
            priRefConn.mask = 0
            secRefConn.mask = 0
            controlConn.mask = 0
            for (lrdu in LRDU.values()) {
                if (!eConn.get(lrdu)) continue
                when (front!!.down().applyLRDU(lrdu)) {
                    front!!.left() -> priConn.set(lrdu, true)
                    front!!.right() -> secConn.set(lrdu, true)
                    front -> if (oneWayDescriptor.isolated) priRefConn.set(lrdu, true) else controlConn.set(lrdu, true)
                    front!!.back() -> if (oneWayDescriptor.isolated) secRefConn.set(lrdu, true) else controlConn.set(lrdu, true)
                    else -> controlConn.set(lrdu, true)
                }
            }
            cableRenderType = null
            load.target = stream.readFloat()
            hasCasing = stream.readBoolean()
            controlSettings.readWire(stream, allowVoltageControl)
            protection = stream.readBoolean()
            displayedInputVolts = stream.readDouble()
            displayedOutputVolts = stream.readDouble()
            displayedPower = stream.readDouble()
            displayedStatus = stream.readUTF()

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun getCableRenderSide(side: Direction, lrdu: LRDU): CableRenderDescriptor? {
        if (lrdu == LRDU.Down) {
            if (side == front!!.left()) return primaryRender()
            if (side == front!!.right()) return secondaryRender()
            if (side == front && oneWayDescriptor.isolated) return primaryReferenceRender()
            if (side == front!!.back() && oneWayDescriptor.isolated) return secondaryReferenceRender()
            if (side == front && oneWayDescriptor.variable && !grounded) return Eln.instance.stdCableRenderSignal
            if (side == front!!.back() && oneWayDescriptor.variable && !grounded) return Eln.instance.stdCableRenderSignal
        }
        return null
    }

    private fun primaryRender(): CableRenderDescriptor? {
        return resolveAdjacentCableRender(front!!.left())
    }

    private fun secondaryRender(): CableRenderDescriptor? {
        return resolveAdjacentCableRender(front!!.right())
    }

    private fun primaryReferenceRender(): CableRenderDescriptor? {
        return resolveAdjacentCableRender(front!!)
    }

    private fun secondaryReferenceRender(): CableRenderDescriptor? {
        return resolveAdjacentCableRender(front!!.back())
    }

    private fun resolveAdjacentCableRender(side: Direction): CableRenderDescriptor? {
        val neighborCoordinate = Coordinate(tileEntity).moved(side)
        if (!neighborCoordinate.blockExist) return null
        val neighbor = neighborCoordinate.world().getBlockEntity(
            neighborCoordinate.x,
            neighborCoordinate.y,
            neighborCoordinate.z
        )

        if (neighbor is SixNodeEntity) {
            val neighborSide = side.inverse
            val elementRender = neighbor.elementRenderList[neighborSide.int] ?: return null
            for (neighborLrdu in LRDU.values()) {
                val render = elementRender.getCableRender(neighborLrdu)
                if (render != null) return render
            }
        }

        return null
    }

    override fun notifyNeighborSpawn() {
        super.notifyNeighborSpawn()
        cableRenderType = null
    }

    override fun refresh(deltaT: Float) {
        super.refresh(deltaT)
        load.step(deltaT)
        if (hasCasing) {
            doorOpen.target = if (!Utils.isPlayerAround(tileEntity.level!!, coordinate.moved(front!!).getAxisAlignedBB(0))) 0f else 1f
            doorOpen.step(deltaT)
        }
    }

    override fun newGuiDraw(side: Direction, player: Player): Screen {
        return OneWayDcDcGui(player, inventory, this)
    }
}

class OneWayDcDcGui(player: Player, inventory: Container, render: OneWayDcDcRender) :
    ConverterControlGui(OneWayDcDcContainer(player, inventory, render.variableControl), render)

class OneWayDcDcContainer(player: Player, inventory: Container, variable: Boolean) : BasicContainer(
    player,
    inventory,
    arrayOf(
        DcDcWindingSlot(
            inventory, primaryCableSlotId, 58, 30, 16,
            arrayOf(tr("Power cable or wire slot"))
        ),
        DcDcWindingSlot(
            inventory, secondaryCableSlotId, 100, 30, 16,
            arrayOf(tr("Power cable or wire slot"))
        ),
        GenericItemUsingDamageSlot(
            inventory, ferromagneticSlotId, 58 + (100 - 58) / 2, 30, 1,
            arrayOf<Class<*>>(FerromagneticCoreDescriptor::class.java),
            ISlotSkin.SlotSkin.medium, arrayOf(tr("Ferromagnetic core slot"))
        ),
        GenericItemUsingDamageSlot(
            inventory, CasingSlotId, 130, 74, 1,
            arrayOf<Class<*>>(CaseItemDescriptor::class.java),
            ISlotSkin.SlotSkin.medium, arrayOf(tr("Casing slot"))
        )
    )
) {
    companion object {
        const val primaryCableSlotId = 0
        const val secondaryCableSlotId = 1
        const val ferromagneticSlotId = 2
        const val CasingSlotId = 3
    }
}
