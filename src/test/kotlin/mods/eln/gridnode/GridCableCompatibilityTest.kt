package mods.eln.gridnode

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.UserError
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeDescriptor
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import mods.eln.sixnode.electricalcable.UtilityCableMaterial
import net.minecraft.nbt.CompoundTag
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Registered production descriptors, real terminal adapters and the production MNA span.
 * Click-to-link inventory/placement is additionally exercised in GridCableSmokeChecks.
 */
class GridCableCompatibilityTest {
    private val expectedDevices = setOf("Grid DC-DC Converter", "Utility Pole",
        "Utility Pole w/DC-DC Converter", "Transmission Tower", "Direct Utility Pole", "Grid Switch")

    private fun devices(): List<GridDescriptor> = Eln.transparentNodeItem.subItemList.values
        .filterIsInstance<GridDescriptor>().distinct().also {
            assertEquals(expectedDevices, it.map { descriptor -> descriptor.name }.toSet())
        }

    private fun cables(): List<ElectricalCableDescriptor> = Eln.sixNodeItem.subItemList.values
        .filterIsInstance<ElectricalCableDescriptor>().distinct().also { assertTrue(it.isNotEmpty()) }

    private fun hv(rating: Double = 1000.0) = UtilityCableDescriptor.allDescriptors().single {
        it.material == UtilityCableMaterial.COPPER && it.sizeLabel == "2 AWG" &&
            it.insulated && !it.melted && it.insulationVoltageRating == rating
    }

    private fun element(descriptor: GridDescriptor, x: Int): GridElement {
        val node = TransparentNode().apply { coordinate = Coordinate(x, 80, 0, 0) }
        return (descriptor.ElementClass.getConstructor(TransparentNode::class.java, TransparentNodeDescriptor::class.java)
            .newInstance(node, descriptor) as GridElement).also { node.element = it }
    }

    private fun rejects(block: () -> Unit) {
        try { block() } catch (_: UserError) { return }
        throw AssertionError("Expected invalid grid link to be rejected")
    }

    @Test fun intactLowVoltageAndBarePowerCablesQualify() {
        val lowSpools = UtilityCableDescriptor.allDescriptors().filter {
            !it.melted && it.insulated && it.insulationVoltageRating < 1000.0
        }
        val bare = UtilityCableDescriptor.allDescriptors().filter { !it.melted && !it.insulated }
        val legacyLow = cables().filter {
            it !is UtilityCableDescriptor && !it.signalWire && it.electricalNominalVoltage < 1000.0
        }
        assertTrue(lowSpools.isNotEmpty() && bare.isNotEmpty() && legacyLow.isNotEmpty())
        for (cable in lowSpools + bare + legacyLow) {
            assertTrue(GridCablePolicy.accepts(cable), cable.name)
        }
    }

    @Test fun everyRegisteredGridDeviceAcceptsEveryIntactPowerCable() {
        var accepted = 0
        var rejected = 0
        for (device in devices()) for (cable in cables()) {
            val expected = !cable.signalWire && !(cable is UtilityCableDescriptor && cable.melted)
            assertEquals(expected, device.acceptsGridCable(cable), "${device.name}: ${cable.name}")
            if (expected) accepted++ else rejected++
        }
        assertTrue(accepted > 0 && rejected > 0)
    }

    @Test fun poleFlagsDoNotRestrictLowOrHighVoltageSpools() {
        val newHv = UtilityCableDescriptor.allDescriptors().filter {
            !it.melted && it.parentItemDamage in (38 shl 6)..((38 shl 6) + 19)
        }
        assertEquals(10, newHv.size)
        newHv.forEach { assertFalse(it.poleEligible, "Exercise the missing historic flag") }
        val low = UtilityCableDescriptor.allDescriptors().filter {
            !it.melted && it.insulated && it.insulationVoltageRating < 1000.0
        }
        assertTrue(low.any { it.poleEligible } && low.any { !it.poleEligible })
        (newHv + low).forEach { cable -> devices().forEach { assertTrue(it.acceptsGridCable(cable)) } }
    }

    @Test fun compatibilityDoesNotChangeVoltageRatingsOrInsulation() {
        for (cable in cables()) {
            val nominal = cable.electricalNominalVoltage
            val utility = cable as? UtilityCableDescriptor
            val insulated = utility?.insulated
            val insulationRating = utility?.insulationVoltageRating
            val expectedRating = if (utility != null && utility.insulated) utility.insulationVoltageRating else nominal
            devices().forEach { it.acceptsGridCable(cable) }
            assertEquals(expectedRating, GridCablePolicy.ratedVoltage(cable))
            assertEquals(nominal, cable.electricalNominalVoltage)
            assertEquals(insulated, utility?.insulated)
            assertEquals(insulationRating, utility?.insulationVoltageRating)
            if (utility != null && !utility.insulated && !utility.melted) {
                assertEquals(0.0, utility.insulationVoltageRating, 0.0)
                assertTrue(GridCablePolicy.accepts(utility))
            }
        }
    }

    @Test fun terminalValidationIsSymmetricForAllDevicesAndHorizontalRotations() {
        val descriptors = devices()
        val connectionCables = listOf(
            hv(), hv(150000.0),
            UtilityCableDescriptor.allDescriptors().first { !it.melted && it.insulated && it.insulationVoltageRating < 1000.0 },
            UtilityCableDescriptor.allDescriptors().first { !it.melted && !it.insulated },
            cables().first { it !is UtilityCableDescriptor && !it.signalWire && it.electricalNominalVoltage < 1000.0 }
        )
        for ((i, aDesc) in descriptors.withIndex()) for ((j, bDesc) in descriptors.withIndex()) {
            val a = element(aDesc, i * 32)
            val b = element(bDesc, 256 + j * 32)
            for (facing in listOf(Direction.XN, Direction.XP, Direction.ZN, Direction.ZP)) {
                a.front = facing; b.front = facing.inverse
                val aSides = Direction.values().filter { a.getGridElectricalLoad(it) != null }
                val bSides = Direction.values().filter { b.getGridElectricalLoad(it) != null }
                assertTrue(aSides.isNotEmpty() && bSides.isNotEmpty())
                for (aSide in aSides) for (bSide in bSides) {
                    for (cable in connectionCables) {
                        GridLink.validateNewLink(a, b, aSide, bSide, cable, 6)
                        GridLink.validateNewLink(b, a, bSide, aSide, cable, 6)
                    }
                }
                for (side in Direction.values().filter { it !in aSides }) {
                    rejects { GridLink.validateNewLink(a, b, side, bSides.first(), hv(), 6) }
                    rejects { GridLink.validateNewLink(b, a, bSides.first(), side, hv(), 6) }
                }
                assertTrue(a.gridLinkList.isEmpty() && b.gridLinkList.isEmpty())
            }
        }
    }

    @Test fun directApiCannotBypassSignalDamageAndSpanValidation() {
        val a = element(devices().first(), 0)
        val b = element(devices().last(), 6)
        val sa = Direction.values().first { a.getGridElectricalLoad(it) != null }
        val sb = Direction.values().first { b.getGridElectricalLoad(it) != null }
        val signal = cables().first { it.signalWire }
        for (cable in listOf(signal, checkNotNull(hv().meltedDescriptor))) {
            rejects { GridLink.validateNewLink(a, b, sa, sb, cable, 6) }
            rejects { GridLink.validateNewLink(b, a, sb, sa, cable, 6) }
        }
        for (length in listOf(0, -1)) rejects { GridLink.validateNewLink(a, b, sa, sb, hv(), length) }
        rejects { GridLink.validateNewLink(a, a, sa, sa, hv(), 6) }
    }

    @Test fun paidLengthAndCableIdentityAreValidatedAndCopiedWithoutAliasing() {
        val cable = hv()
        val supplied = cable.newItemStack().also { cable.setRemainingLengthMeters(it, 6.0) }
        val linked = GridLink.linkStack(cable, 6, supplied)
        assertTrue(linked !== supplied)
        assertTrue(ElectricalCableDescriptor.getDescriptor(linked) === cable)
        assertEquals(6.0, cable.getRemainingLengthMeters(linked), 0.0)
        cable.setRemainingLengthMeters(supplied, 90.0)
        assertEquals(6.0, cable.getRemainingLengthMeters(linked), 0.0)
        rejects { GridLink.linkStack(cable, 6, supplied) }
        rejects { GridLink.linkStack(hv(5000.0), 6, linked) }
        cable.setRemainingLengthMeters(supplied, Double.NaN)
        rejects { GridLink.linkStack(cable, 6, supplied) }
        val native = Eln.instance.veryHighVoltageCableDescriptor
        assertEquals(6, GridLink.linkStack(native, 6, null).count)
        rejects { GridLink.linkStack(native, 6, native.newItemStack(5)) }
    }

    @Test fun existingSaved600VoltLinksRetainTheirCableAndPaidLength() {
        val cable = UtilityCableDescriptor.allDescriptors().first {
            it.insulated && !it.melted && it.insulationVoltageRating == 600.0
        }
        val stack = cable.newItemStack().also { cable.setRemainingLengthMeters(it, 6.0) }
        val old = GridLink(Coordinate(0, 80, 0, 0), Coordinate(6, 80, 0, 0), Direction.XP,
            Direction.XN, stack, cable.resistanceOhms(6.0))
        val tag = CompoundTag(); old.writeToNBT(tag, "")
        val restored = GridLink(tag, "")
        assertTrue(ElectricalCableDescriptor.getDescriptor(restored.cable) === cable)
        assertEquals(6.0, cable.getRemainingLengthMeters(restored.cable), 0.0)
        assertTrue(GridCablePolicy.accepts(cable))
        assertEquals(6.0, checkNotNull(restored.spanThermal).meters, 0.0)
    }

    @Test fun longerAcceptedSpansKeepRealResistanceDropAndPowerBalance() {
        val cable = hv()
        var previousOutput = Double.POSITIVE_INFINITY
        for (meters in listOf(1, 6, 32)) {
            val root = RootSystem(.01, 1)
            val a = ElectricalLoad().apply { serialResistance = .01 }
            val b = ElectricalLoad().apply { serialResistance = .02 }
            val span = WireSpanConnection(a, b, GridLink.resistanceForCable(cable, meters))
            val source = VoltageSource("grid-cable-test", a, null).setVoltage(100.0)
            val load = Resistor(b, null).apply { resistance = 10.0 }
            root.addState(a); root.addState(b)
            root.addComponent(source); root.addComponent(load); root.addComponent(span)
            repeat(5) { root.step() }
            val ohms = cable.resistanceOhms(meters.toDouble()) + .03
            assertEquals(100.0 * 10.0 / (10.0 + ohms), b.voltage, 1e-6)
            assertTrue(b.voltage < previousOutput)
            previousOutput = b.voltage
            assertEquals(source.power, load.power + span.current * span.current * span.resistance, 1e-6)
            a.serialResistance = .04
            span.notifyRsChange()
            assertEquals(cable.resistanceOhms(meters.toDouble()) + .06, span.resistance, 1e-10)
        }
    }
}
