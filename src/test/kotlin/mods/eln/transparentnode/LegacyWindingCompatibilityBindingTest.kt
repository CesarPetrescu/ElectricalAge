package mods.eln.transparentnode

import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Wiring/compatibility guards; electrical open-circuit behavior is covered by the real MNA tests. */
class LegacyWindingCompatibilityBindingTest {
    private fun source() = File("src/main/kotlin/mods/eln/transparentnode/LegacyDcDc.kt").readText()

    @Test fun serverAndBothInventorySlotsUseTheSameLegacyWindingValidation() {
        val source = source()
        assertTrue(source.contains("val primaryCount = legacyDcDcWindingCount(primaryCable)"))
        assertTrue(source.contains("val secondaryCount = legacyDcDcWindingCount(secondaryCable)"))
        assertTrue(source.contains("val winding = dcDcWinding(stack) ?: return 0"))
        assertTrue(source.contains("if (winding.descriptor is UtilityCableDescriptor) return 0"))
        assertTrue(source.contains("legacyDcDcWindingCount(itemStack) > 0"))
        assertTrue(source.contains("LegacyDcDcWindingSlot(inventory, primaryCableSlotId"))
        assertTrue(source.contains("LegacyDcDcWindingSlot(inventory, secondaryCableSlotId"))
    }

    @Test fun legacyCountRatioAndCoreLossPolicyRemainIntact() {
        val source = source()
        assertTrue(source.contains("secondaryCount.toDouble() / primaryCount.toDouble()"))
        assertTrue(source.contains("const val MAX_RATIO = 16.0"))
        assertTrue(source.contains("const val MIN_RATIO = 1.0 / 16.0"))
        assertEquals(2, Regex("serialResistance = coreFactor \\* 0\\.01").findAll(source).count())
    }

    @Test fun legacyBranchesUsePopulationGatedSourcesRegisteredExactlyOnce() {
        val source = source()
        assertTrue(source.contains("SafeTransformerProcess(primaryLoad, secondaryLoad, primaryVoltageSource, secondaryVoltageSource) { populated }"))
        for (name in listOf("primaryVoltageSource", "secondaryVoltageSource")) {
            assertTrue(source.contains("val $name = SwitchableVoltageSource"))
            assertEquals(1, Regex("electricalComponentList\\.add\\($name\\)").findAll(source).count())
        }
    }
}
