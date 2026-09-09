package mods.eln.transparentnode

import mods.eln.sim.mna.HvMnaRegression
import kotlin.test.Test
import kotlin.test.assertTrue

/** The normal Gradle/FML suite executes the same regression bodies as the offline harness. */
class HvRepairRegressionTest {
    @Test fun nativeMnaShutdownProbesAndTransformerPower() = assertTrue(HvMnaRegression.runAll() > 0)
    @Test fun nativeConverterModesLimitsAndNetworkGroups() = assertTrue(HvConverterRegression.runAll() > 0)
    @Test fun settingsWindingEnthalpyAndSelectiveFaults() = assertTrue(HvFeatureRegression.runAll() > 0)
}
