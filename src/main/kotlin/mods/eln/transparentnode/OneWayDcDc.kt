package mods.eln.transparentnode

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
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.power.*
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

enum class OneWayDcDcMode {
    FIXED,
    BOOST,
    BUCK,
    BOOST_BUCK,
    ISOLATION
}

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
            Collections.addAll(list, *tr("The output voltage ratio is controlled\nfrom a signal input.").split("\n").toTypedArray())
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
) : TransparentNodeElement(transparentNode, descriptor), IConfigurable {
    internal val oneWayDescriptor = descriptor as OneWayDcDcDescriptor

    val isolated: Boolean
        get() = oneWayDescriptor.isolated

    val primaryLoad = NbtElectricalLoad("primaryLoad")
    val secondaryLoad = NbtElectricalLoad("secondaryLoad")
    val primaryInternal = NbtElectricalLoad("primaryInternal")
    val secondaryInternal = NbtElectricalLoad("secondaryInternal")
    val primaryWindingResistance = Resistor(primaryLoad, primaryInternal)
    val secondaryWindingResistance = Resistor(secondaryInternal, secondaryLoad)

    val primaryReferenceLoad = NbtElectricalLoad("primaryReferenceLoad")
    val secondaryReferenceLoad = NbtElectricalLoad("secondaryReferenceLoad")
    val control = NbtElectricalGateInput("control")
    val settings = DcDcControl()

    override val inventory = TransparentNodeElementInventory(4, 64, this)

    val inputSink = SwitchableVoltageSource("inputSink")
    val outputSource = SwitchableVoltageSource("outputSource")
    private val primaryThermalLoad = NbtThermalLoad("primaryThermalLoad")
    private val secondaryThermalLoad = NbtThermalLoad("secondaryThermalLoad")
    private val primaryThermalProcess = DcDcWindingThermalProcess(
        owner = this, inventory = inventory,
        windingResistance = primaryWindingResistance, terminal = primaryLoad,
        onMelted = { computeInventory(); reconnect() },
        thermalLoad = primaryThermalLoad,
        slot = OneWayDcDcContainer.primaryCableSlotId,
        current = { inputSink.current },
        label = "Primary"
    )
    private val secondaryThermalProcess = DcDcWindingThermalProcess(
        owner = this, inventory = inventory,
        windingResistance = secondaryWindingResistance, terminal = secondaryLoad,
        onMelted = { computeInventory(); reconnect() },
        thermalLoad = secondaryThermalLoad,
        slot = OneWayDcDcContainer.secondaryCableSlotId,
        current = { outputSource.current },
        label = "Secondary"
    )
    private val transferProcess = OneWayDcDcProcess(this)
    private val electronicsHeating = mods.eln.sim.process.heater.ElectricalHeatAccumulator({
        (-inputSink.power - outputSource.power).coerceAtLeast(0.0)
    }, primaryThermalLoad)


    var primaryMeltCurrent = 0.0
    var secondaryMeltCurrent = 0.0
    var ratioControl = 1.0
    var activeRatio = 1.0
    var movedPower = 0.0
    var populated = false

    private val primaryVoltageWatchdog = VoltageStateWatchDog(primaryLoad)
    private val secondaryVoltageWatchdog = VoltageStateWatchDog(secondaryLoad)

    init {
        electricalLoadList.add(primaryLoad)
        electricalLoadList.add(secondaryLoad)
        electricalLoadList.add(primaryInternal)
        electricalLoadList.add(secondaryInternal)
        electricalComponentList.add(primaryWindingResistance)
        electricalComponentList.add(secondaryWindingResistance)
        if (oneWayDescriptor.isolated) {
            electricalLoadList.add(primaryReferenceLoad)
            electricalLoadList.add(secondaryReferenceLoad)
        }
        if (oneWayDescriptor.variable) electricalLoadList.add(control)
        electricalComponentList.add(inputSink)
        electricalComponentList.add(outputSource)
        thermalLoadList.add(primaryThermalLoad)
        thermalLoadList.add(secondaryThermalLoad)
        electricalProcessList.add(electronicsHeating.sample)
        electricalProcessList.add(IProcess { movedPower = outputSource.power.coerceAtLeast(0.0) })
        electricalProcessList.add(primaryThermalProcess.heating.sample)
        electricalProcessList.add(secondaryThermalProcess.heating.sample)
        slowProcessList.add(primaryThermalProcess)
        slowProcessList.add(secondaryThermalProcess)
        primaryThermalLoad.setAsSlow()
        secondaryThermalLoad.setAsSlow()

        val exp = WorldExplosion(this).machineExplosion()
        slowProcessList.add(primaryVoltageWatchdog.setDestroys(exp))
        slowProcessList.add(secondaryVoltageWatchdog.setDestroys(exp))
        slowProcessList.add(NodePeriodicPublishProcess(node!!, 1.0, .5))
    }

    override fun connectJob() {
        Eln.simulator.addThermalSlowProcess(electronicsHeating.deliver)
        Eln.simulator.addThermalSlowProcess(primaryThermalProcess.heating.deliver)
        Eln.simulator.addThermalSlowProcess(secondaryThermalProcess.heating.deliver)
        Eln.simulator.mna.addProcess(transferProcess)
        super.connectJob()
    }

    override fun disconnectJob() {
        electronicsHeating.flushIntoLoad()
        Eln.simulator.removeThermalSlowProcess(electronicsHeating.deliver)
        primaryThermalProcess.heating.flushIntoLoad()
        secondaryThermalProcess.heating.flushIntoLoad()
        Eln.simulator.removeThermalSlowProcess(primaryThermalProcess.heating.deliver)
        Eln.simulator.removeThermalSlowProcess(secondaryThermalProcess.heating.deliver)
        super.disconnectJob()
        Eln.simulator.mna.removeProcess(transferProcess)
    }

    override fun initialize() {
        inputSink.connectTo(primaryInternal, if (oneWayDescriptor.isolated) primaryReferenceLoad else null)
        outputSource.connectTo(secondaryInternal, if (oneWayDescriptor.isolated) secondaryReferenceLoad else null)
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

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        settings.load(nbt)
    }

    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)
        settings.save(nbt)
    }

    override fun getItemStackNBT(): CompoundTag = CompoundTag().also(settings::save)
    override fun readItemStackNBT(nbt: CompoundTag?) { if (nbt != null) settings.load(nbt) }

    override fun networkUnserialize(stream: DataInputStream): Byte {
        val id = super.networkUnserialize(stream)
        if (!settings.handle(id, stream)) return id
        transferProcess.resetFault()
        computeInventory()
        needPublish()
        return TransparentNodeElement.unserializeNulldId
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
        transferProcess.resetFault()
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

        primaryVoltageWatchdog.setNominalVoltage(120_000.0)
        secondaryVoltageWatchdog.setNominalVoltage(120_000.0)
        primaryMeltCurrent = dcDcWindingMeltCurrent(primaryCable)
        secondaryMeltCurrent = dcDcWindingMeltCurrent(secondaryCable)
        primaryThermalProcess.configure(primaryCable)
        secondaryThermalProcess.configure(secondaryCable)

        val constructionStatus = if (oneWayDescriptor.mode == OneWayDcDcMode.FIXED || settings.version >= 2) {
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
            primaryLoad.serialResistance = 1e-6
            if (oneWayDescriptor.isolated) primaryReferenceLoad.serialResistance = 1e-6
        }

        if (secondaryWinding == null || !hasValidConstruction) {
            secondaryLoad.highImpedance()
            if (oneWayDescriptor.isolated) secondaryReferenceLoad.highImpedance()
        } else {
            secondaryLoad.serialResistance = 1e-6
            if (oneWayDescriptor.isolated) secondaryReferenceLoad.serialResistance = 1e-6
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

    fun computeRatio(): Double {
        if (!populated) return 1.0
        if (oneWayDescriptor.mode == OneWayDcDcMode.FIXED) return ratioControl.coerceIn(1.0 / 256, 256.0)
        if (oneWayDescriptor.mode == OneWayDcDcMode.ISOLATION) return 1.0
        val kind = when (oneWayDescriptor.mode) {
            OneWayDcDcMode.BOOST -> ConverterKind.BOOST
            OneWayDcDcMode.BUCK -> ConverterKind.BUCK
            else -> ConverterKind.BUCK_BOOST
        }
        return settings.ratio(kind, control.normalized)
    }

    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        settings.write(stream)
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
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun getWaila(): Map<String, String> {
        val info = linkedMapOf<String, String>()
        val constructionStatus = if (oneWayDescriptor.mode == OneWayDcDcMode.FIXED || settings.version >= 2) {
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
        info[tr("Converter state")] = converterStateText(transferProcess.status)
        info[tr("Control mode")] = tr("%1$ (version %2$)", settings.mode, settings.version)
        if (settings.mode == "VOLTAGE") info[tr("Internal output target")] = Utils.plotVolt("", settings.value)
        info[tr("Winding resistance")] = tr("Primary %1$ ohm; secondary %2$ ohm", Utils.plotValue(primaryWindingResistance.resistance), Utils.plotValue(secondaryWindingResistance.resistance))
        info[tr("Construction")] = dcDcConstructionWaila(constructionStatus)
        info[tr("Ratio")] = Utils.plotValue(activeRatio)
        info[tr("Transferred power")] = Utils.plotPower("", movedPower)
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

    override fun readConfigTool(compound: CompoundTag, invoker: Player) {
        if (compound.contains("converterControlVersion")) { settings.load(compound); transferProcess.resetFault() }
        if (ConfigCopyToolDescriptor.readGenDescriptor(compound, "primary", inventory, OneWayDcDcContainer.primaryCableSlotId, invoker))
            inventoryChange(inventory)
        if (ConfigCopyToolDescriptor.readGenDescriptor(compound, "secondary", inventory, OneWayDcDcContainer.secondaryCableSlotId, invoker))
            inventoryChange(inventory)
        if (ConfigCopyToolDescriptor.readGenDescriptor(compound, "core", inventory, OneWayDcDcContainer.ferromagneticSlotId, invoker))
            inventoryChange(inventory)
    }

    override fun writeConfigTool(compound: CompoundTag, invoker: Player) {
        settings.save(compound)
        ConfigCopyToolDescriptor.writeGenDescriptor(compound, "primary", inventory.getItem(OneWayDcDcContainer.primaryCableSlotId))
        ConfigCopyToolDescriptor.writeGenDescriptor(compound, "secondary", inventory.getItem(OneWayDcDcContainer.secondaryCableSlotId))
        ConfigCopyToolDescriptor.writeGenDescriptor(compound, "core", inventory.getItem(OneWayDcDcContainer.ferromagneticSlotId))
    }

}

class OneWayDcDcProcess(private val element: OneWayDcDcElement) : ConservativePowerProcess {
    var status = "IDLE"
        private set
    private var tripped = false
    private var efficiency = 1.0
    private var inputLimit = Double.MAX_VALUE
    private var outputLimit = Double.MAX_VALUE

    fun resetFault() { tripped = false }
    private fun open(reason: String) {
        element.inputSink.enabled = false
        element.outputSource.enabled = false
        element.movedPower = 0.0
        status = reason
    }

    override fun rootSystemPreStepProcess() {
        if (tripped) { open("NON_CONVERGENT"); return }
        if (!element.settings.enabled || !element.populated) { open("DISABLED"); return }
        val a = probePort(element.primaryInternal, if (element.isolated) element.primaryReferenceLoad else null, element.inputSink)
        val b = probePort(element.secondaryInternal, if (element.isolated) element.secondaryReferenceLoad else null, element.outputSource)
        if (!a.volts.isFinite() || !b.volts.isFinite() || a.ohms.isNaN() || b.ohms.isNaN()) { open("INVALID_NETWORK"); return }
        if (a.volts <= 0 || a.ohms >= RegulatedConverter.OPEN_OHMS) { open("NO_INPUT"); return }
        val ratio = try { element.computeRatio() } catch (_: IllegalArgumentException) { open("INVALID_CONTROL"); return }
        element.activeRatio = ratio
        val modern = element.settings.version >= 2
        inputLimit = if (modern) element.primaryMeltCurrent else Double.MAX_VALUE
        outputLimit = if (modern) element.secondaryMeltCurrent else Double.MAX_VALUE
        val maxInput = if (modern) dcDcWindingVoltage(element.inventory.getItem(0)) else 120_000.0
        val maxOutput = if (modern) dcDcWindingVoltage(element.inventory.getItem(1)) else 120_000.0
        if (a.volts > maxInput) { open("INPUT_OVERVOLTAGE"); return }
        val mode = element.oneWayDescriptor.mode
        val fixed = mode == OneWayDcDcMode.FIXED || mode == OneWayDcDcMode.ISOLATION
        efficiency = if (modern && !fixed) 0.97 else 1.0
        val point: OperatingPoint = if (fixed && modern) {
            val p = if (b.ohms >= RegulatedConverter.OPEN_OHMS)
                OperatingPoint(a.volts, a.volts * ratio, 0.0, 0.0, 0.0, 0.0, 0.0)
            else RatioTransformer.solve(a, b, ratio)
            if (p == null || p.outputAmps < -1e-9 || p.inputAmps < -1e-9) { open("OUTPUT_ALREADY_HIGH"); return }
            if (p.inputAmps > inputLimit || p.outputAmps > outputLimit || p.outputVolts > maxOutput) {
                open("FIXED_RATIO_LIMIT"); return
            }
            p
        } else if (modern) {
            val kind = when (mode) {
                OneWayDcDcMode.BOOST -> 1.0 to 256.0
                OneWayDcDcMode.BUCK -> (1.0 / 256) to 1.0
                else -> (1.0 / 256) to 256.0
            }
            val target = if (element.settings.mode == "VOLTAGE") element.settings.value else a.volts * ratio
            when (val result = RegulatedConverter.solve(a, b, target, ConverterLimits(
                0.1, maxInput, maxOutput, inputLimit, outputLimit, 1_000_000.0, efficiency, kind.first, kind.second
            ))) {
                is TransferResult.Off -> { open(result.reason.name); return }
                is TransferResult.Running -> {
                    status = if (result.point.limitedBy.isEmpty()) "REGULATING" else result.point.limitedBy.first().name
                    result.point
                }
            }
        } else {
            // Existing saves retain their historical ratio/power policy, but get genuine open-circuit shutdown.
            if (b.ohms >= RegulatedConverter.OPEN_OHMS) {
                OperatingPoint(a.volts, (a.volts * ratio).coerceAtMost(maxOutput), 0.0, 0.0, 0.0, 0.0, 0.0)
            } else {
                val transfer = OneWayDcDcMath.solve(th(a), th(b), ratio, maxOutput)
                if (transfer == null) { open("OUTPUT_ALREADY_HIGH"); return }
                OperatingPoint(transfer.inputSourceVoltage, transfer.outputSourceVoltage, 0.0, 0.0,
                    transfer.power, transfer.power, 0.0)
            }
        }
        if (fixed || !modern) status = "TRANSFERRING"
        element.inputSink.voltage = point.inputVolts
        element.outputSource.voltage = point.outputVolts
        element.inputSink.enabled = true
        element.outputSource.enabled = true
    }

    override fun acceptsCandidate(): Boolean {
        if (!element.inputSink.enabled && !element.outputSource.enabled) return true
        val inputSystem = element.inputSink.subSystem ?: return false
        val outputSystem = element.outputSource.subSystem ?: return false
        val inputAmps = inputSystem.pendingValue(element.inputSink.currentState)
        val outputAmps = -outputSystem.pendingValue(element.outputSource.currentState)
        if (inputAmps < -1e-7 || outputAmps < -1e-7 || inputAmps > inputLimit * (1 + 1e-6) || outputAmps > outputLimit * (1 + 1e-6)) return false
        return balancedPower(-pendingSourcePower(element.inputSink), pendingSourcePower(element.outputSource), efficiency)
    }

    override fun failClosed() { tripped = true; open("NON_CONVERGENT") }
    override fun connectedSystems(): Set<SubSystem> = setOfNotNull(element.primaryInternal.subSystem, element.secondaryInternal.subSystem)
    private fun th(value: PortThevenin) = object : OneWayDcDcThevenin {
        override val voltage = value.volts
        override val resistance = value.ohms
    }
}

internal interface OneWayDcDcThevenin {
    val voltage: Double
    val resistance: Double
}

internal data class OneWayDcDcTransfer(
    val inputSourceVoltage: Double,
    val outputSourceVoltage: Double,
    val power: Double
)

internal object OneWayDcDcMath {
    fun solve(
        inputTh: OneWayDcDcThevenin,
        outputTh: OneWayDcDcThevenin,
        ratio: Double,
        maxOutputVoltage: Double
    ): OneWayDcDcTransfer? {
        if (!inputTh.voltage.isFinite() || !outputTh.voltage.isFinite()) return null
        if (!inputTh.resistance.isFinite() || !outputTh.resistance.isFinite()) return null
        if (inputTh.voltage <= 0.0 || ratio <= 0.0) return null

        val targetOutputVoltage = Utils.limit(inputTh.voltage * ratio, 0.0, maxOutputVoltage)
        if (targetOutputVoltage <= outputTh.voltage || targetOutputVoltage <= 0.0) return null

        val outputResistance = outputTh.resistance.coerceAtLeast(0.0)
        if (outputResistance <= 0.0) return null
        val demandedOutputCurrent = ((targetOutputVoltage - outputTh.voltage) / outputResistance).coerceAtLeast(0.0)

        var outputPower = targetOutputVoltage * demandedOutputCurrent
        val inputResistance = inputTh.resistance.coerceAtLeast(0.0)
        val inputMaxPower = if (inputResistance <= 0.0) {
            Double.POSITIVE_INFINITY
        } else {
            inputTh.voltage * inputTh.voltage / (4.0 * inputResistance)
        }
        outputPower = outputPower.coerceAtMost(inputMaxPower).coerceAtLeast(0.0)
        if (outputPower <= 0.0) return null

        val outputSourceVoltage = sourceVoltageForPower(
            theveninVoltage = outputTh.voltage,
            resistance = outputResistance,
            power = outputPower,
            maxVoltage = targetOutputVoltage
        )
        val actualOutputPower = outputPowerAtSource(outputTh.voltage, outputResistance, outputSourceVoltage)
            .coerceAtMost(inputMaxPower)
            .coerceAtLeast(0.0)
        if (actualOutputPower <= 0.0) return null

        val inputSourceVoltage = sinkVoltageForPower(
            theveninVoltage = inputTh.voltage,
            resistance = inputResistance,
            power = actualOutputPower
        )

        return OneWayDcDcTransfer(inputSourceVoltage, outputSourceVoltage, actualOutputPower)
    }

    private fun outputPowerAtSource(theveninVoltage: Double, resistance: Double, sourceVoltage: Double): Double {
        val current = if (resistance <= 0.0) {
            return 0.0
        } else {
            ((sourceVoltage - theveninVoltage) / resistance).coerceAtLeast(0.0)
        }
        return sourceVoltage * current
    }

    private fun sourceVoltageForPower(theveninVoltage: Double, resistance: Double, power: Double, maxVoltage: Double): Double {
        if (power <= 0.0) return theveninVoltage
        if (resistance <= 0.0) return min(maxVoltage, theveninVoltage).coerceAtLeast(theveninVoltage)
        val voltage = (sqrt(theveninVoltage * theveninVoltage + 4.0 * power * resistance) + theveninVoltage) / 2.0
        return min(maxVoltage, voltage).coerceAtLeast(theveninVoltage)
    }

    private fun sinkVoltageForPower(theveninVoltage: Double, resistance: Double, power: Double): Double {
        if (power <= 0.0) return theveninVoltage
        if (resistance <= 0.0) return theveninVoltage
        val clampedPower = min(power, theveninVoltage * theveninVoltage / (4.0 * resistance))
        val discriminant = (theveninVoltage * theveninVoltage - 4.0 * clampedPower * resistance).coerceAtLeast(0.0)
        val voltageByPower = (theveninVoltage + sqrt(discriminant)) / 2.0
        return max(0.0, voltageByPower)
    }
}

class OneWayDcDcRender(
    tileEntity: TransparentNodeEntity,
    private val descriptor: TransparentNodeDescriptor
) : TransparentNodeElementRender(tileEntity, descriptor) {

    val settings = DcDcControl()
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
        settings.read(stream)
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
        return OneWayDcDcGui(player, inventory, this, oneWayDescriptor.variable)
    }
}

class OneWayDcDcGui(player: Player, inventory: Container, val render: OneWayDcDcRender, private val variable: Boolean) : GuiContainerEln(OneWayDcDcContainer(player, inventory, variable)) {
    private val controls = DcDcControlWidgets(this, render, render.settings, variable)
    override fun newHelper(): GuiHelperContainer = GuiHelperContainer(this, 176, 238, 8, 156)
    override fun initGui() { super.initGui(); controls.init() }
    override fun guiObjectEvent(obj: mods.eln.gui.IGuiObject) { controls.event(obj) }
    override fun textFieldNewValue(field: mods.eln.gui.GuiTextFieldEln, value: String) { controls.text(field, value) }
    override fun postDraw(f: Float, x: Int, y: Int) {
        super.postDraw(f, x, y)
        controls.refresh()
        drawString(8, 6, tr("Input / Output windings"))
        drawString(8, 142, tr("V2: protected; V1: legacy"))
    }
}

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
