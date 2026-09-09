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
import mods.eln.item.*
import mods.eln.misc.*
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.sim.ThermalLoad
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.power.*
import mods.eln.sim.mna.process.TransformerInterSystemProcess
import mods.eln.sim.nbt.NbtElectricalGateInput
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sim.nbt.NbtThermalLoad
import mods.eln.sim.process.destruct.VoltageStateWatchDog
import mods.eln.sim.process.destruct.WorldExplosion
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
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
import java.util.*
import mods.eln.misc.isNothing

class VariableDcDcDescriptor(
    name: String,
    objM: Obj3D,
    coreM: Obj3D,
    casingM: Obj3D,
    val guiTexture: String = "vdcdc.png"
): TransparentNodeDescriptor(name, VariableDcDcElement::class.java, VariableDcDcRender::class.java) {
    companion object {
        val MIN_LOAD_HUM = 0.5
        val COIL_SCALE: Float = 4.0f
        val COIL_SCALE_LIMIT: Int = 16
        const val COIL_BASE_HEIGHT: Float = 0.031f
        const val COIL_OUTER_HALF_WIDTH: Float = 0.0868f
        const val COIL_CORE_HALF_WIDTH: Float = 0.0625f
        const val COIL_CENTER_Y: Float = 0.0155f
        const val COIL_CENTER_Z: Float = -0.3125f
        const val COIL_STACK_MIN_Y: Float = -0.1575f
        const val COIL_STACK_MAX_Y: Float = 0.25f
        const val COIL_STACK_CENTER_Y: Float = (COIL_STACK_MIN_Y + COIL_STACK_MAX_Y) * 0.5f
        const val COIL_STACK_HEIGHT: Float = COIL_STACK_MAX_Y - COIL_STACK_MIN_Y
    }

    var main: Obj3D.Obj3DPart? = null
    var core: Obj3D.Obj3DPart? = null
    var coil: Obj3D.Obj3DPart? = null
    var casing: Obj3D.Obj3DPart? = null
    var casingLeftDoor: Obj3D.Obj3DPart? = null
    var casingRightDoor: Obj3D.Obj3DPart? = null

    init {
        main = objM.getPart("main")
        coil = objM.getPart("sbire")
        core = coreM.getPart("fero")
        casing = casingM.getPart("Case")
        casingLeftDoor = casingM.getPart("DoorL")
        casingRightDoor = casingM.getPart("DoorR")

        voltageLevelColor = VoltageLevelColor.Neutral
    }

    override fun setParent(item: Item, damage: Int) {
        super.setParent(item, damage)
        Data.addWiring(newItemStack())
    }

    override fun addInformation(itemStack: ItemStack, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)
        Collections.addAll(list, *tr("Transforms an input voltage to\nan output voltage.")!!.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        Collections.addAll(list, *tr("The output voltage is controlled\nfrom a signal input")!!.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
    }

    override fun addRealismContext(list: MutableList<String>?): RealisticEnum {
        list?.add(tr("This variable DC/DC has unrealistic capacitance effects and can sink/source power that violates Newton's laws"))
        list?.add(tr("It is made this way to improve the performance of the simulator in large power networks"))
        return RealisticEnum.UNREALISTIC
    }

    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType, item: ItemStack, helper: IItemRenderer.ItemRendererHelper): Boolean {
        return type != IItemRenderer.ItemRenderType.INVENTORY
    }

    override fun handleRenderType(item: ItemStack, type: IItemRenderer.ItemRenderType): Boolean {
        return true
    }

    override fun renderItem(type: IItemRenderer.ItemRenderType, item: ItemStack, vararg data: Any) {
        if (type == IItemRenderer.ItemRenderType.INVENTORY) {
            super.renderItem(type, item, *data)
        } else {
            draw(core, 1, 4, 1.0f, 1.0f, false, 0f)
        }
    }

    internal fun draw(
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
        val displayedCount = kotlin.math.min(count, ((COIL_STACK_HEIGHT + gap) / pitch).toInt().coerceAtLeast(1))
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

class VariableDcDcElement(transparentNode: TransparentNode, descriptor: TransparentNodeDescriptor): TransparentNodeElement(transparentNode, descriptor), IConfigurable {
    val primaryLoad = NbtElectricalLoad("primaryLoad")
    val secondaryLoad = NbtElectricalLoad("secondaryLoad")
    val primaryInternal = NbtElectricalLoad("primaryInternal")
    val secondaryInternal = NbtElectricalLoad("secondaryInternal")
    val primaryWindingResistance = Resistor(primaryLoad, primaryInternal)
    val secondaryWindingResistance = Resistor(secondaryInternal, secondaryLoad)


    val control = NbtElectricalGateInput("control")
    val settings = DcDcControl()

    val primaryVoltageSource = SwitchableVoltageSource("primaryVoltageSource")
    val secondaryVoltageSource = SwitchableVoltageSource("secondaryVoltageSource")

    val interSystemProcess = SafeTransformerProcess(primaryInternal, secondaryInternal, primaryVoltageSource, secondaryVoltageSource) { populated }
    override val inventory = TransparentNodeElementInventory(4, 64, this)
    private val primaryThermalLoad = NbtThermalLoad("primaryThermalLoad")
    private val secondaryThermalLoad = NbtThermalLoad("secondaryThermalLoad")
    private val primaryThermalProcess = DcDcWindingThermalProcess(
        owner = this,
        inventory = inventory,
        thermalLoad = primaryThermalLoad,
        slot = VariableDcDcContainer.primaryCableSlotId,
        current = { primaryVoltageSource.current },
        label = "Primary",
        onMelted = {
            computeInventory()
            reconnect()
        },
        windingResistance = primaryWindingResistance,
        terminal = primaryLoad
    )
    private val secondaryThermalProcess = DcDcWindingThermalProcess(
        owner = this,
        inventory = inventory,
        thermalLoad = secondaryThermalLoad,
        slot = VariableDcDcContainer.secondaryCableSlotId,
        current = { secondaryVoltageSource.current },
        label = "Secondary",
        onMelted = {
            computeInventory()
            reconnect()
        },
        windingResistance = secondaryWindingResistance,
        terminal = secondaryLoad
    )

    var primaryMeltCurrent = 0.0
    var secondaryMeltCurrent = 0.0
    var ratioControl = 1.0

    val primaryVoltageWatchdog = VoltageStateWatchDog(primaryLoad)
    val secondaryVoltageWatchdog = VoltageStateWatchDog(secondaryLoad)

    var populated = false

    init {
        electricalLoadList.add(primaryLoad)
        electricalLoadList.add(secondaryLoad)
        electricalLoadList.add(primaryInternal)
        electricalLoadList.add(secondaryInternal)
        electricalComponentList.add(primaryWindingResistance)
        electricalComponentList.add(secondaryWindingResistance)
        electricalLoadList.add(control)
        electricalComponentList.add(primaryVoltageSource)
        electricalComponentList.add(secondaryVoltageSource)
        thermalLoadList.add(primaryThermalLoad)
        thermalLoadList.add(secondaryThermalLoad)
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
        slowProcessList.add(VariableDcDcProcess(this))
    }

    override fun disconnectJob() {
        primaryThermalProcess.heating.flushIntoLoad()
        secondaryThermalProcess.heating.flushIntoLoad()
        Eln.simulator.removeThermalSlowProcess(primaryThermalProcess.heating.deliver)
        Eln.simulator.removeThermalSlowProcess(secondaryThermalProcess.heating.deliver)
        super.disconnectJob()
        Eln.simulator.mna.removeProcess(interSystemProcess)

    }

    override fun connectJob() {
        Eln.simulator.addThermalSlowProcess(primaryThermalProcess.heating.deliver)
        Eln.simulator.addThermalSlowProcess(secondaryThermalProcess.heating.deliver)
        Eln.simulator.mna.addProcess(interSystemProcess)
        super.connectJob()
    }

    override fun getElectricalLoad(side: Direction, lrdu: LRDU): ElectricalLoad? {
        if (lrdu != LRDU.Down) return null
        return when (side) {
            front.right() -> secondaryLoad
            front.left() -> primaryLoad
            front -> control
            front.back() -> control
            else -> null
        }
    }

    override fun getThermalLoad(side: Direction, lrdu: LRDU): ThermalLoad? {
        if (lrdu != LRDU.Down) return null
        return when (side) {
            front.left() -> primaryThermalLoad
            front.right() -> secondaryThermalLoad
            else -> null
        }
    }

    override fun getConnectionMask(side: Direction, lrdu: LRDU): Int {
        if (lrdu != LRDU.Down) return 0
        return when (side) {
            front -> NodeBase.maskElectricalInputGate
            front.back() -> NodeBase.maskElectricalInputGate
            else -> NodeBase.maskElectricalPower
        }
    }

    override fun multiMeterString(side: Direction): String {
        if (side == front.left())
            return Utils.plotVolt("UP+:", primaryLoad.voltage) + Utils.plotAmpere("IP+:", -primaryLoad.current)
        return if (side == front.right())
            Utils.plotVolt("US+:", secondaryLoad.voltage) + Utils.plotAmpere("IS+:", -secondaryLoad.current)
        else
            Utils.plotVolt("UP+:", primaryLoad.voltage) + Utils.plotAmpere("IP+:", primaryVoltageSource.current) + Utils.plotVolt("  US+:", secondaryLoad.voltage) + Utils.plotAmpere("IS+:", secondaryVoltageSource.current)
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

    override fun initialize() {
        primaryVoltageSource.connectTo(primaryInternal, null)
        secondaryVoltageSource.connectTo(secondaryInternal, null)
        interSystemProcess.ratio = 1.0
        computeInventory()
        connect()
    }

    private fun computeInventory() {
        val primaryCable = inventory.getItem(VariableDcDcContainer.primaryCableSlotId)
        val secondaryCable = inventory.getItem(VariableDcDcContainer.secondaryCableSlotId)
        val core = inventory.getItem(VariableDcDcContainer.ferromagneticSlotId)
        val primaryWinding = dcDcWinding(primaryCable)
        val secondaryWinding = dcDcWinding(secondaryCable)
        val constructionStatus = if (settings.version == 1) dcDcConstructionStatus(core, primaryCable, secondaryCable)
            else dcDcFlexibleConstructionStatus(core, primaryCable, secondaryCable)

        primaryVoltageWatchdog.setNominalVoltage(120_000.0)
        secondaryVoltageWatchdog.setNominalVoltage(120_000.0)

        interSystemProcess.maximumPrimaryVoltage = dcDcWindingVoltage(primaryCable)
        interSystemProcess.maximumSecondaryVoltage = dcDcWindingVoltage(secondaryCable)
        interSystemProcess.resetFault()
        primaryMeltCurrent = dcDcWindingMeltCurrent(primaryCable)
        secondaryMeltCurrent = dcDcWindingMeltCurrent(secondaryCable)
        primaryThermalProcess.configure(primaryCable)
        secondaryThermalProcess.configure(secondaryCable)

        val coreDescriptor = GenericItemUsingDamageDescriptor.getDescriptor(
            core, FerromagneticCoreDescriptor::class.java) as? FerromagneticCoreDescriptor
        val coreFactor = coreDescriptor?.cableMultiplicator ?: 1.0
        val hasValidConstruction = constructionStatus.operational

        if (primaryWinding == null || !hasValidConstruction) {
            primaryLoad.highImpedance()
            populated = false
        } else {
            primaryLoad.serialResistance = 1e-6
        }

        if (secondaryWinding == null || !hasValidConstruction) {
            secondaryLoad.highImpedance()
            populated = false
        } else {
            secondaryLoad.serialResistance = 1e-6
        }

        populated = primaryWinding != null && secondaryWinding != null && hasValidConstruction
        ratioControl = 1.0
    }

    fun meltWindingIfOverCurrent(): Boolean {
        val meltedPrimary = meltDcDcWindingIfOverCurrent(
            inventory,
            VariableDcDcContainer.primaryCableSlotId,
            primaryVoltageSource.current
        )
        val meltedSecondary = meltDcDcWindingIfOverCurrent(
            inventory,
            VariableDcDcContainer.secondaryCableSlotId,
            secondaryVoltageSource.current
        )
        if (meltedPrimary || meltedSecondary) {
            computeInventory()
            needPublish()
            return true
        }
        return false
    }

    override fun inventoryChange(inventory: Container?) {
        interSystemProcess.resetFault()
        disconnect()
        computeInventory()
        connect()
        needPublish()
    }

    override fun onBlockActivated(player: Player, side: Direction, vx: Float, vy: Float, vz: Float): Boolean {
        return false
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
        interSystemProcess.resetFault()
        computeInventory()
        needPublish()
        return TransparentNodeElement.unserializeNulldId
    }

    override fun hasGui(): Boolean {
        return true
    }

    override fun newContainer(side: Direction, player: Player): AbstractContainerMenu {
        return VariableDcDcContainer(player, inventory)
    }

    override fun getLightOpacity(): Float {
        return 1.0f
    }

    override fun onGroundedChangedByClient() {
        super.onGroundedChangedByClient()
        computeInventory()
        reconnect()
    }

    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        settings.write(stream)
        try {
            stream.writeShort(dcDcRenderedWindingCount(inventory.getItem(0)))
            stream.writeShort(dcDcRenderedWindingCount(inventory.getItem(1)))
            stream.writeFloat(dcDcRenderedWindingThickness(inventory.getItem(VariableDcDcContainer.primaryCableSlotId)))
            stream.writeFloat(dcDcRenderedWindingThickness(inventory.getItem(VariableDcDcContainer.secondaryCableSlotId)))
            Utils.serialiseItemStack(stream, inventory.getItem(VariableDcDcContainer.ferromagneticSlotId))
            Utils.serialiseItemStack(stream, inventory.getItem(VariableDcDcContainer.primaryCableSlotId))
            Utils.serialiseItemStack(stream, inventory.getItem(VariableDcDcContainer.secondaryCableSlotId))
            node!!.lrduCubeMask.getTranslate(front.down()).serialize(stream)
            var load = 0f
            if (primaryMeltCurrent != 0.0 && secondaryMeltCurrent != 0.0) {
                load = Utils.limit(Math.max(primaryLoad.current / primaryMeltCurrent,
                    secondaryLoad.current / secondaryMeltCurrent).toFloat(), 0f, 1f)
            }
            stream.writeFloat(load)
            stream.writeBoolean(!inventory.getItem(3).isNothing())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun getWaila(): Map<String, String> {
        val info = HashMap<String, String>()
        info[tr("Construction")] = dcDcConstructionWaila(
            (if (settings.version >= 2) ::dcDcFlexibleConstructionStatus else ::dcDcConstructionStatus)(
                inventory.getItem(VariableDcDcContainer.ferromagneticSlotId),
                inventory.getItem(VariableDcDcContainer.primaryCableSlotId),
                inventory.getItem(VariableDcDcContainer.secondaryCableSlotId)
            )
        )
        info[tr("Converter state")] = converterStateText(interSystemProcess.status)
        info[tr("Control mode")] = tr("%1$ (version %2$)", settings.mode, settings.version)
        info[tr("Winding resistance")] = tr("Primary %1$ ohm; secondary %2$ ohm", Utils.plotValue(primaryWindingResistance.resistance), Utils.plotValue(secondaryWindingResistance.resistance))
        info[tr("Ratio")] = Utils.plotValue(interSystemProcess.ratio)
        info[tr("Primary winding")] = windingStatus(
            inventory.getItem(VariableDcDcContainer.primaryCableSlotId),
            primaryVoltageSource.current,
            primaryThermalLoad
        )
        info[tr("Secondary winding")] = windingStatus(
            inventory.getItem(VariableDcDcContainer.secondaryCableSlotId),
            secondaryVoltageSource.current,
            secondaryThermalLoad
        )
        // It's just not fair not to show the voltages on the VDC/DC. It's so variable...
        info[tr("Voltages")] = "\u00A7a" + Utils.plotVolt("", primaryLoad.voltage) + " " +
            "\u00A7e" + Utils.plotVolt("", secondaryLoad.voltage)
        info[tr("Control Voltage")] = Utils.plotVolt(control.voltage)
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
        if (compound.contains("converterControlVersion")) { settings.load(compound); interSystemProcess.resetFault() }
        if (compound.contains("isolator")) {
            disconnect()
            reconnect()
            needPublish()
        }
        if (ConfigCopyToolDescriptor.readGenDescriptor(compound, "primary", inventory, VariableDcDcContainer.primaryCableSlotId, invoker))
            inventoryChange(inventory)
        if (ConfigCopyToolDescriptor.readGenDescriptor(compound, "secondary", inventory, VariableDcDcContainer.secondaryCableSlotId, invoker))
            inventoryChange(inventory)
        if (ConfigCopyToolDescriptor.readGenDescriptor(compound, "core", inventory, VariableDcDcContainer.ferromagneticSlotId, invoker))
            inventoryChange(inventory)
    }

    override fun writeConfigTool(compound: CompoundTag, invoker: Player) {
        settings.save(compound)
        ConfigCopyToolDescriptor.writeGenDescriptor(compound, "primary", inventory.getItem(VariableDcDcContainer.primaryCableSlotId))
        ConfigCopyToolDescriptor.writeGenDescriptor(compound, "secondary", inventory.getItem(VariableDcDcContainer.secondaryCableSlotId))
        ConfigCopyToolDescriptor.writeGenDescriptor(compound, "core", inventory.getItem(VariableDcDcContainer.ferromagneticSlotId))
    }
}

class VariableDcDcProcess(val element: VariableDcDcElement): IProcess {
    companion object {
        const val MAX_RATIO = 256.0
        const val MIN_RATIO = 1.0 / 256.0
    }

    override fun process(time: Double) {
        if (!element.populated) {
            element.interSystemProcess.ratio = 1.0
            return
        }
        element.interSystemProcess.enabled = element.settings.enabled
        element.interSystemProcess.voltageTarget = if (element.settings.mode == "VOLTAGE") element.settings.value else null
        try {
            element.interSystemProcess.ratio = element.settings.ratio(ConverterKind.VARIABLE, element.control.normalized)
        } catch (_: IllegalArgumentException) {
            element.interSystemProcess.enabled = false
        }
    }
}

class VariableDcDcRender(tileEntity: TransparentNodeEntity, val descriptor: TransparentNodeDescriptor): TransparentNodeElementRender(tileEntity, descriptor) {

    val settings = DcDcControl()
    override val inventory = TransparentNodeElementInventory(4, 64, this)

    val load = SlewLimiter(0.5f)

    var primaryStackSize = 0
    var secondaryStackSize = 0
    var primaryThickness = 1.0f
    var secondaryThickness = 1.0f
    var priRender: CableRenderDescriptor? = null
    var secRender: CableRenderDescriptor? = null
    var controlRender: CableRenderDescriptor? = null

    private var feroPart: Obj3D.Obj3DPart? = null
    private var hasCasing = false

    private val coordinate: Coordinate
    private val doorOpen: PhysicalInterpolator

    private val priConn = LRDUMask()
    private val secConn = LRDUMask()
    private val controlConn = LRDUMask()
    private val eConn = LRDUMask()
    private var cableRenderType: CableRenderType? = null

    init {
        addLoopedSound(object : LoopedSound("eln:transformer", coordinate(), SoundInstance.Attenuation.LINEAR) {
            override fun getVolume(): Float {
                return if (load.position > VariableDcDcDescriptor.MIN_LOAD_HUM)
                    0.1f * (load.position - VariableDcDcDescriptor.MIN_LOAD_HUM).toFloat() / (1 - VariableDcDcDescriptor.MIN_LOAD_HUM).toFloat()
                else
                    0f
            }
        })

        coordinate = Coordinate(tileEntity)
        doorOpen = PhysicalInterpolator(0.4f, 4.0f, 0.9f, 0.05f)
    }

    override fun draw() {
        GL11.glPushMatrix()
        front!!.glRotateXnRef()
        (descriptor as VariableDcDcDescriptor).draw(
            feroPart,
            primaryStackSize.toInt(),
            secondaryStackSize.toInt(),
            primaryThickness,
            secondaryThickness,
            hasCasing,
            doorOpen.get()
        )
        GL11.glPopMatrix()
        cableRenderType = drawCable(front!!.down(), priRender, priConn, cableRenderType)
        cableRenderType = drawCable(front!!.down(), secRender, secConn, cableRenderType)
        cableRenderType = drawCable(front!!.down(), Eln.instance.stdCableRenderSignal, controlConn, cableRenderType)
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
            if (!feroStack.isNothing()) {
                val feroDesc: GenericItemUsingDamageDescriptor? = GenericItemUsingDamageDescriptor.getDescriptor(feroStack, FerromagneticCoreDescriptor::class.java)
                if (feroDesc != null)
                    feroPart = (feroDesc as FerromagneticCoreDescriptor).feroPart
            }
            val priStack = Utils.unserialiseItemStack(stream)
            if (!priStack.isNothing()) {
                val priDesc: GenericItemBlockUsingDamageDescriptor? = ElectricalCableDescriptor.getDescriptor(priStack, ElectricalCableDescriptor::class.java)
                if (priDesc != null)
                    priRender = (priDesc as ElectricalCableDescriptor).render
            }

            val secStack = Utils.unserialiseItemStack(stream)
            if (!secStack.isNothing()) {
                val secDesc: GenericItemBlockUsingDamageDescriptor? = ElectricalCableDescriptor.getDescriptor(secStack, ElectricalCableDescriptor::class.java)
                if (secDesc != null)
                    secRender = (secDesc as ElectricalCableDescriptor).render
            }

            eConn.deserialize(stream)

            priConn.mask = 0
            secConn.mask = 0
            controlConn.mask = 0
            for (lrdu in LRDU.values()) {
                if(!eConn.get(lrdu)) continue
                if(front!!.down().applyLRDU(lrdu) == front!!.left()) {
                    priConn.set(lrdu, true)
                    continue
                }
                if(front!!.down().applyLRDU(lrdu) == front!!.right()) {
                    secConn.set(lrdu, true)
                    continue
                }
                controlConn.set(lrdu, true)
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
            if (side == front!!.left()) return priRender
            if (side == front!!.right()) return secRender
            if (side == front && !grounded) return priRender
            if (side == front!!.back() && !grounded) return secRender
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
            if (!Utils.isPlayerAround(tileEntity.level!!, coordinate.moved(front!!).getAxisAlignedBB(0)))
                doorOpen.target = 0f
            else
                doorOpen.target = 1f
            doorOpen.step(deltaT)
        }
    }

    override fun newGuiDraw(side: Direction, player: Player): Screen {
        return VariableDcDcGui(player, inventory, this)
    }
}

class VariableDcDcGui(player: Player, inventory: Container, val render: VariableDcDcRender) : GuiContainerEln(VariableDcDcContainer(player, inventory)) {
    private val controls = DcDcControlWidgets(this, render, render.settings, true)
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

class VariableDcDcContainer(player: Player, inventory: Container) : BasicContainer(player, inventory,
    arrayOf(
        DcDcWindingSlot(inventory, primaryCableSlotId, 58, 30, 16,
            arrayOf(tr("Power cable or wire slot"))),
        DcDcWindingSlot(inventory, secondaryCableSlotId, 100, 30, 16,
            arrayOf(tr("Power cable or wire slot"))),
        GenericItemUsingDamageSlot(inventory, ferromagneticSlotId, 58 + (100 - 58) / 2, 30, 1,
            arrayOf<Class<*>>(FerromagneticCoreDescriptor::class.java),
            ISlotSkin.SlotSkin.medium, arrayOf(tr("Ferromagnetic core slot"))),
        GenericItemUsingDamageSlot(inventory, CasingSlotId, 130, 74, 1,
            arrayOf<Class<*>>(CaseItemDescriptor::class.java),
            ISlotSkin.SlotSkin.medium, arrayOf(tr("Casing slot")))))
    {
    companion object {
        const val primaryCableSlotId = 0
        const val secondaryCableSlotId = 1
        const val ferromagneticSlotId = 2
        const val CasingSlotId = 3
    }
}
