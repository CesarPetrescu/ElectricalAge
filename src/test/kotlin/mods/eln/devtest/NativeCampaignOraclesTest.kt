package mods.eln.devtest

import mods.eln.mechanical.ShaftNetwork
import mods.eln.mechanical.wouldExplode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeCampaignOraclesTest {
    @Test fun actualFiveVoltInputsAreAccepted() {
        NativeCampaignOracles.inputs(listOf(0.0, 5.0, 5.0), listOf(.001, 4.999, 4.999), 5.0)
        NativeCampaignOracles.digitalOutput(4.999, true, 5.0)
        NativeCampaignOracles.digitalOutput(.001, false, 5.0)
    }
    @Test fun burnedInputWiresCannotProduceFalseTruthTablePasses() {
        assertFailsWith<IllegalStateException> {
            NativeCampaignOracles.inputs(listOf(5.0, 0.0, 0.0), listOf(0.0, 0.0, 0.0), 5.0)
        }
    }
    @Test fun fiftyVoltFixturesAreRejected() {
        assertFailsWith<IllegalStateException> { NativeCampaignOracles.inputs(listOf(50.0), listOf(50.0), 5.0) }
    }
    @Test fun schmittMiddleVoltageIsAnActualAnalogInput() {
        NativeCampaignOracles.inputs(listOf(2.0), listOf(1.999), 5.0)
    }
    @Test fun missingAndNonfiniteInputsFail() {
        assertFailsWith<IllegalStateException> { NativeCampaignOracles.inputs(listOf(5.0), emptyList(), 5.0) }
        assertFailsWith<IllegalStateException> { NativeCampaignOracles.inputs(listOf(5.0), listOf(Double.NaN), 5.0) }
    }
    @Test fun wrongDigitalOutputFails() {
        assertFailsWith<IllegalStateException> { NativeCampaignOracles.digitalOutput(0.0, true, 5.0) }
    }
    @Test fun openBatteryCanHaveIntentionalSelfDischarge() {
        val internal = 121.394 / 288.0
        assertTrue(internal > .1)
        NativeCampaignOracles.batteryCurrent(internal, 0.0, internal, 1e-6)
    }
    @Test fun loadedBatteryAccountsForBothBranches() {
        NativeCampaignOracles.batteryCurrent(2.42, 2.0, .42, 1e-6)
    }
    @Test fun unexplainedBatteryDrainFails() {
        assertFailsWith<IllegalStateException> { NativeCampaignOracles.batteryCurrent(1.42, 0.0, .42, 1e-6) }
    }
    @Test fun stationaryAndLowSpeedRigidJoinsAreSafe() {
        assertFalse(NativeCampaignOracles.unsafeRigidMerge(0.0, 0.0))
        assertFalse(NativeCampaignOracles.unsafeRigidMerge(20.0, 0.0))
    }
    @Test fun observedHighSpeedFreshWheelPlacementIsUnsafe() {
        assertTrue(NativeCampaignOracles.unsafeRigidMerge(187.6, 0.0))
        // Old sides being synchronous is not enough: the new part is still stationary.
        assertFalse(NativeCampaignOracles.unsafeRigidMerge(187.6, 187.6))
    }
    @Test fun independentReferenceMatchesProductionAcrossBoundarySamples() {
        for (a in listOf(0.0, 20.0, 45.0, 45.46, 100.0, 187.6, 250.0)) {
            for (b in listOf(0.0, 20.0, 45.0, 100.0, 187.6, 250.0)) {
                val left = ShaftNetwork().apply { rads = a }
                val right = ShaftNetwork().apply { rads = b }
                assertEquals(NativeCampaignOracles.unsafeRigidMerge(a, b), wouldExplode(left, right))
            }
        }
    }
}
