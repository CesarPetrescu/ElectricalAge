package mods.eln.transparentnode

import mods.eln.Eln
import mods.eln.bootstrapMinecraft
import mods.eln.projectFile
import mods.eln.sixnode.electricalcable.*
import net.minecraft.core.registries.BuiltInRegistries
import kotlin.test.*

/** Requires the REAL FML launcher/registered Minecraft items. Not executed by the offline harness. */
class HvRegistryIntegrationTest {
    @Test fun legacyUtilityIdentitiesRemainExactlyRegistered() {
        bootstrapMinecraft()
        val rows = projectFile("tools/hv/fixtures/legacy-utility-identities.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith('#') }
        assertEquals(152, rows.size)
        for (row in rows) {
            val parts = row.split('\t')
            val id = parts[0].toInt()
            val descriptor = assertNotNull(Eln.sixNodeItem.getDescriptor(id), "missing legacy id $id")
            assertEquals(parts[1], descriptor.name, "legacy id $id changed meaning")
            val stack = descriptor.newItemStack(1)
            assertFalse(stack.isEmpty)
            assertEquals(parts[2], BuiltInRegistries.ITEM.getKey(stack.item).toString())
            assertSame(descriptor, Eln.sixNodeItem.getDescriptor(stack))
        }
    }

    @Test fun highVoltageVariantsAreObtainableWithoutChangingCopperOrLegacyDefault() {
        bootstrapMinecraft()
        for (spec in HvCableSpecifications.entries) {
            val cable = Eln.sixNodeItem.getDescriptor(spec.descriptorId) as? UtilityCableDescriptor
            assertNotNull(cable, "missing HV cable ${spec.descriptorId}")
            assertEquals(spec.areaMm2, cable.conductorAreaMm2, 1e-10)
            assertEquals(spec.volts, cable.insulationVoltageRating, 1e-10)
            assertEquals(spec.rubberMultiplier, cable.insulationMaterialMultiplier, 1e-10)
            assertNotNull(cable.meltedDescriptor)
            assertNotNull(cable.moltenPileDescriptor)
            val bare = assertNotNull(WireProduction.singleFor(cable, false))
            assertEquals(bare.resistanceOhms(), cable.resistanceOhms(), 1e-10)
            val choices = WireProduction.insulatorOptions(bare.newItemStack(1))
            assertEquals(600.0, choices.first().insulationVoltageRating, 1e-10,
                "old insulator selection zero must retain its old output")
            assertTrue(cable in choices)
            val stack = cable.newItemStack(1)
            cable.setRemainingLengthMeters(stack, 13.25)
            assertEquals(13.25, cable.getRemainingLengthMeters(stack), 1e-10)
            assertEquals(13.25 * spec.rubberMultiplier, WireProduction.insulationCostMeters(cable, 13.25), 1e-10)
            assertTrue(WireProductionRecipes.outputs(stack).any { it.kind == WireMachineKind.INSULATOR },
                "HV cable needs a documented survival manufacturing route")
        }
    }
}
