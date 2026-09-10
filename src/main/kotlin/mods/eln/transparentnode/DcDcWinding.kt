package mods.eln.transparentnode

import mods.eln.gui.ISlotSkin
import mods.eln.gui.ISlotWithComment
import mods.eln.gui.SlotWithSkin
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Utils
import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.process.heater.ElectricalHeatAccumulator
import mods.eln.sixnode.electricalcable.WireThermalPhysics
import mods.eln.misc.editTag
import mods.eln.misc.tagCompound
import mods.eln.sim.IProcess
import mods.eln.sim.nbt.NbtThermalLoad
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt
import mods.eln.misc.isNothing

private const val MAX_RENDERED_WINDINGS = 256
private const val NATIVE_RENDER_AREA_MM2 = 33.631

internal data class DcDcWinding(
    val descriptor: ElectricalCableDescriptor,
    val amount: Double
)

internal data class DcDcConstructionStatus(
    val operational: Boolean,
    val broken: Boolean,
    val messages: List<String>
)

internal fun dcDcWinding(stack: ItemStack?): DcDcWinding? {
    if (stack.isNothing()) return null
    val descriptor = ElectricalCableDescriptor.getDescriptor(
        stack,
        ElectricalCableDescriptor::class.java
    ) as? ElectricalCableDescriptor ?: return null
    if (descriptor.signalWire) return null
    if (descriptor is UtilityCableDescriptor && descriptor.melted) return null

    val amount = if (descriptor is UtilityCableDescriptor) {
        descriptor.getRemainingLengthMeters(stack)
    } else {
        stack.count.toDouble()
    }
    if (!amount.isFinite() || amount <= UtilityCableDescriptor.LENGTH_METERS_EPSILON) return null
    return DcDcWinding(descriptor, amount)
}

internal fun dcDcConstructionStatus(
    core: ItemStack?,
    primary: ItemStack?,
    secondary: ItemStack?
): DcDcConstructionStatus {
    val messages = mutableListOf<String>()
    val hasCore = mods.eln.generic.GenericItemUsingDamageDescriptor.getDescriptor(
        core,
        mods.eln.item.FerromagneticCoreDescriptor::class.java
    ) != null
    if (!hasCore) messages += tr("Needs Core")
    val primaryWinding = strictWindingConstructionProblem(primary, tr("Primary")).also {
        it.message?.let(messages::add)
    }
    val secondaryWinding = strictWindingConstructionProblem(secondary, tr("Secondary")).also {
        it.message?.let(messages::add)
    }
    if (primaryWinding.message == null && secondaryWinding.message == null &&
        primaryWinding.amount != null && secondaryWinding.amount != null &&
        abs(primaryWinding.amount - secondaryWinding.amount) > UtilityCableDescriptor.LENGTH_METERS_EPSILON
    ) {
        messages += tr(
            "Primary and secondary windings must match (%1$ vs %2$)",
            formatWindingMeters(primaryWinding.amount),
            formatWindingMeters(secondaryWinding.amount)
        )
    }
    val broken = messages.any { it.contains(tr("Melted")) }
    return DcDcConstructionStatus(messages.isEmpty(), broken, messages)
}

internal fun dcDcConstructionWaila(status: DcDcConstructionStatus): String {
    if (status.operational) return tr("Operational")
    return status.messages.joinToString(separator = "\n") { "  * $it" }
}

internal fun dcDcFlexibleConstructionStatus(
    core: ItemStack?,
    primary: ItemStack?,
    secondary: ItemStack?
): DcDcConstructionStatus {
    val messages = mutableListOf<String>()
    val hasCore = mods.eln.generic.GenericItemUsingDamageDescriptor.getDescriptor(
        core,
        mods.eln.item.FerromagneticCoreDescriptor::class.java
    ) != null
    if (!hasCore) messages += tr("Needs Core")
    flexibleWindingConstructionProblem(primary, tr("Primary"))?.let(messages::add)
    flexibleWindingConstructionProblem(secondary, tr("Secondary"))?.let(messages::add)
    val broken = messages.any { it.contains(tr("Melted")) }
    return DcDcConstructionStatus(messages.isEmpty(), broken, messages)
}

private data class WindingConstructionProblem(
    val amount: Double?,
    val message: String?
)

private fun strictWindingConstructionProblem(stack: ItemStack?, label: String): WindingConstructionProblem {
    if (stack.isNothing()) return WindingConstructionProblem(null, tr("Needs %1$ Winding", label))
    val descriptor = ElectricalCableDescriptor.getDescriptor(
        stack,
        ElectricalCableDescriptor::class.java
    ) as? ElectricalCableDescriptor ?: return WindingConstructionProblem(null, tr("Needs %1$ Winding", label))
    if (descriptor.signalWire) return WindingConstructionProblem(null, tr("Needs %1$ Winding", label))
    if (descriptor is UtilityCableDescriptor && descriptor.melted) {
        return WindingConstructionProblem(null, tr("Melted %1$ requires replacement", label))
    }
    val amount = if (descriptor is UtilityCableDescriptor) {
        descriptor.getRemainingLengthMeters(stack)
    } else {
        stack.count.toDouble()
    }
    if (!amount.isFinite() || amount <= UtilityCableDescriptor.LENGTH_METERS_EPSILON) {
        return WindingConstructionProblem(null, tr("Needs %1$ Winding", label))
    }
    return WindingConstructionProblem(amount, null)
}

private fun flexibleWindingConstructionProblem(stack: ItemStack?, label: String): String? {
    if (stack.isNothing()) return tr("Needs %1$ Winding", label)
    val descriptor = ElectricalCableDescriptor.getDescriptor(
        stack,
        ElectricalCableDescriptor::class.java
    ) as? ElectricalCableDescriptor ?: return tr("Needs %1$ Winding", label)
    if (descriptor.signalWire) return tr("Needs %1$ Winding", label)
    if (descriptor is UtilityCableDescriptor && descriptor.melted) {
        return tr("Melted %1$ requires replacement", label)
    }
    return if (dcDcWinding(stack) == null) tr("Needs %1$ Winding", label) else null
}

private fun formatWindingMeters(amount: Double): String {
    return if (abs(amount - amount.toInt()) < 0.001) {
        "${amount.toInt()}m"
    } else {
        String.format(java.util.Locale.US, "%.2fm", amount)
    }
}

private fun dcDcWindingRenderAmount(stack: ItemStack?): Double? {
    if (stack.isNothing()) return null
    val descriptor = ElectricalCableDescriptor.getDescriptor(
        stack,
        ElectricalCableDescriptor::class.java
    ) as? ElectricalCableDescriptor ?: return null
    if (descriptor.signalWire) return null
    val amount = if (descriptor is UtilityCableDescriptor) {
        descriptor.getRemainingLengthMeters(stack)
    } else {
        stack.count.toDouble()
    }
    return amount.takeIf { it.isFinite() && it > UtilityCableDescriptor.LENGTH_METERS_EPSILON }
}

internal fun dcDcRenderedWindingThickness(stack: ItemStack?): Float {
    if (stack.isNothing()) return 1.0f
    val descriptor = ElectricalCableDescriptor.getDescriptor(
        stack,
        ElectricalCableDescriptor::class.java
    ) as? ElectricalCableDescriptor ?: return 1.0f
    if (descriptor.signalWire) return 1.0f
    if (descriptor is UtilityCableDescriptor) {
        val conductors = descriptor.conductorCount.coerceAtLeast(1)
        val areaPerConductor = descriptor.totalConductorAreaMm2 / conductors
        return sqrt(areaPerConductor / NATIVE_RENDER_AREA_MM2).toFloat().coerceIn(0.05f, 1.85f)
    }
    return (descriptor.render.widthPixel / 1.95f).coerceIn(0.35f, 1.85f)
}

internal fun dcDcWindingMeltCurrent(stack: ItemStack?): Double {
    val descriptor = dcDcWinding(stack)?.descriptor ?: return 5.0
    return descriptor.electricalMaximalCurrent.takeIf { it.isFinite() && it > 0.0 } ?: 5.0
}

internal class DcDcWindingThermalProcess(
    private val owner: TransparentNodeElement,
    private val inventory: Container,
    private val thermalLoad: NbtThermalLoad,
    private val slot: Int,
    private val current: () -> Double,
    private val label: String,
    private val onMelted: () -> Unit,
    private val windingResistance: Resistor,
    private val terminal: ElectricalLoad
) : IProcess {
    private var descriptor: UtilityCableDescriptor? = null
    private var winding: DcDcWinding? = null
    private var heldStack: ItemStack? = null
    private var configured = false
    private var lastPublishedTemperatureCelsius = Double.NaN
    val heating = ElectricalHeatAccumulator({
        val amps = current()
        (amps * amps * windingResistance.resistance + terminal.serialPower).coerceAtLeast(0.0)
    }, thermalLoad)

    fun configure(stack: ItemStack?) {
        heating.flushIntoLoad()
        if (configured && heldStack !== stack) {
            // Removed wire carries its last accepted temperature instead of becoming a free cooler.
            heldStack?.takeUnless { it.isEmpty }?.editTag {
                it.putDouble("windingTemperatureCelsius", thermalLoad.temperatureCelsius + owner.getAmbientTemperatureCelsius())
            }
            val saved = stack?.tagCompound?.getDouble("windingTemperatureCelsius")
            thermalLoad.temperatureCelsius = if (stack?.tagCompound?.contains("windingTemperatureCelsius") == true && saved != null && saved.isFinite())
                saved - owner.getAmbientTemperatureCelsius() else 0.0
        }
        heldStack = stack
        configured = true
        winding = dcDcWinding(stack)
        descriptor = winding?.descriptor as? UtilityCableDescriptor
        updateProperties()
    }

    private fun updateProperties() {
        val w = winding
        if (w == null) {
            windingResistance.resistance = mods.eln.sim.mna.misc.MnaConst.highImpedance
            thermalLoad.setHighImpedance()
            return
        }
        val utility = descriptor
        val ambient = owner.getAmbientTemperatureCelsius()
        val temperature = thermalLoad.temperatureCelsius + ambient
        val resistance = if (utility != null) utility.resistanceOhms(w.amount, temperature)
            else w.descriptor.electricalRs * 2.0 * w.amount
        // Positive resistance also avoids incompatible ideal source constraints at zero length.
        val next = resistance.coerceAtLeast(1e-9)
        if (abs(windingResistance.resistance - next) > next * 0.001) windingResistance.resistance = next
        if (utility != null) {
            // Match resistanceOhms: one selected conductor, not free mass/cooling from unused cores.
            val physics = WireThermalPhysics(utility.material, utility.conductorAreaMm2, w.amount)
            // A packed coil exposes less area than the equivalent straight wire (gameplay geometry).
            val cooling = physics.coolingConductance(temperature, ambient, utility.insulated) /
                sqrt(w.amount.coerceAtLeast(1.0))
            thermalLoad.set(physics.endpointThermalResistance, 1.0 / cooling, physics.capacity(temperature))
        } else {
            w.descriptor.applyTo(thermalLoad)
            thermalLoad.heatCapacity *= w.amount
        }
    }

    override fun process(time: Double) {
        updateProperties()
        val utility = descriptor ?: return
        val absoluteTemperatureCelsius = thermalLoad.temperatureCelsius + owner.getAmbientTemperatureCelsius()
        heldStack?.takeUnless { it.isEmpty }?.editTag {
            it.putDouble("windingTemperatureCelsius", absoluteTemperatureCelsius)
        }
        maybePublishTemperature(absoluteTemperatureCelsius, utility)
        if (absoluteTemperatureCelsius >= utility.material.meltingPointCelsius ||
            utility.insulated && absoluteTemperatureCelsius >= utility.meltTemperatureCelsius
        ) meltInsertedWire(utility)
    }

    private fun maybePublishTemperature(absoluteTemperatureCelsius: Double, utility: UtilityCableDescriptor) {
        val previous = lastPublishedTemperatureCelsius
        val crossedGlowThreshold = previous <= 550.0 && absoluteTemperatureCelsius > 550.0 ||
            previous > 550.0 && absoluteTemperatureCelsius <= 550.0
        val smokeThreshold = utility.meltTemperatureCelsius * 0.8
        val crossedSmokeThreshold = utility.insulated && !utility.melted && (
            previous <= smokeThreshold && absoluteTemperatureCelsius > smokeThreshold ||
                previous > smokeThreshold && absoluteTemperatureCelsius <= smokeThreshold
            )
        val changedEnough = previous.isNaN() || abs(absoluteTemperatureCelsius - previous) >= 10.0
        if (changedEnough || crossedGlowThreshold || crossedSmokeThreshold) {
            owner.needPublish()
            lastPublishedTemperatureCelsius = absoluteTemperatureCelsius
        }
    }

    private fun meltInsertedWire(utility: UtilityCableDescriptor) {
        val stack = inventory.getItem(slot).takeUnless { it.isEmpty } ?: return
        val melted = utility.meltedDescriptor ?: return
        val replacement = melted.newItemStack(1)
        melted.setRemainingLengthMeters(replacement, utility.getRemainingLengthMeters(stack))
        replacement.editTag { it.putDouble("windingTemperatureCelsius", thermalLoad.temperatureCelsius + owner.getAmbientTemperatureCelsius()) }
        inventory.setItem(slot, replacement)
        inventory.setChanged()
        Utils.println("${owner.javaClass.simpleName} $label winding melted at ${owner.node?.coordinate}")
        onMelted()
        owner.needPublish()
    }
}

internal fun meltDcDcWindingIfOverCurrent(inventory: Container, slot: Int, current: Double): Boolean {
    val stack = inventory.getItem(slot).takeUnless { it.isEmpty } ?: return false
    val descriptor = dcDcWinding(stack)?.descriptor as? UtilityCableDescriptor ?: return false
    val limit = dcDcWindingMeltCurrent(stack)
    if (abs(current) <= limit) return false

    val melted = descriptor.meltedDescriptor ?: return false
    val replacement = melted.newItemStack(1)
    melted.setRemainingLengthMeters(replacement, descriptor.getRemainingLengthMeters(stack))
    inventory.setItem(slot, replacement)
    inventory.setChanged()
    return true
}

internal fun dcDcRenderedWindingCount(stack: ItemStack?): Int {
    val amount = dcDcWindingRenderAmount(stack) ?: return 0
    return ceil(amount).toInt().coerceIn(1, MAX_RENDERED_WINDINGS)
}

internal class DcDcWindingSlot(
    inventory: Container,
    slot: Int,
    x: Int,
    y: Int,
    private val stackLimit: Int,
    private val comment: Array<String> = arrayOf(tr("Power cable or wire slot"))
) : SlotWithSkin(inventory, slot, x, y, ISlotSkin.SlotSkin.medium), ISlotWithComment {
    override fun mayPlace(itemStack: ItemStack): Boolean {
        return dcDcWinding(itemStack) != null
    }

    override fun getMaxStackSize(): Int {
        return stackLimit
    }

    override fun getComment(list: MutableList<String>) {
        comment.forEach(list::add)
    }
}

internal fun dcDcWindingVoltage(stack: ItemStack?): Double {
    val d = dcDcWinding(stack)?.descriptor ?: return 0.0
    return if (d is UtilityCableDescriptor && d.insulated) d.insulationVoltageRating.coerceAtMost(120_000.0)
        else 120_000.0
}