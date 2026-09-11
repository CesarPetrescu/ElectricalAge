package mods.eln.transparentnode

import java.io.File
import mods.eln.sixnode.electricalcable.UtilityCableMaterial
import mods.eln.sixnode.electricalcable.WirePhysics
import mods.eln.sixnode.electricalcable.WireThermalPhysics
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Source-binding guard plus production thermal-math checks, not a native block-placement test. */
class WindingThermalBindingRegressionTest {
    @Test fun productionWindingUsesTheSameActiveConductorAsItsElectricalResistance() {
        val source = File(System.getProperty("eln.projectDir", "."),
            "src/main/kotlin/mods/eln/transparentnode/DcDcWinding.kt").readText()
        assertTrue(Regex("WireThermalPhysics\\(\\s*utility\\.material,\\s*utility\\.conductorAreaMm2,\\s*w\\.amount\\s*\\)").containsMatchIn(source))
        assertFalse(Regex("WireThermalPhysics\\(\\s*utility\\.material,\\s*utility\\.totalConductorAreaMm2").containsMatchIn(source))
        assertTrue(source.contains("utility.resistanceOhms(w.amount, temperature)"))
    }

    @Test fun unusedCoresDoNotIncreaseTheActiveWindingMassOrCooling() {
        val material = UtilityCableMaterial.COPPER
        val area = 2.5
        val meters = 10.0
        val single = WireThermalPhysics(material, area, meters)
        for (cores in listOf(1, 3, 5)) {
            val totalArea = cores * area
            val active = WireThermalPhysics(material, totalArea / cores, meters)
            assertEquals(single.massKg, active.massKg, 1e-12)
            assertEquals(single.capacity(80.0), active.capacity(80.0), 1e-9)
            assertEquals(single.coolingConductance(80.0, 20.0, true),
                active.coolingConductance(80.0, 20.0, true), 1e-9)
            assertEquals(WirePhysics.resistance(material, area, meters, 80.0),
                WirePhysics.resistance(material, totalArea / cores, meters, 80.0), 1e-12)
        }
    }

    @Test fun increasingWindingLengthChangesBothCopperLossAndStoredThermalCapacity() {
        val material = UtilityCableMaterial.COPPER
        val short = WireThermalPhysics(material, 2.5, 5.0)
        val long = WireThermalPhysics(material, 2.5, 50.0)
        for (temperature in listOf(20.0, 80.0, 200.0)) {
            assertEquals(short.capacity(temperature) * 10.0, long.capacity(temperature), 1e-8)
            assertEquals(WirePhysics.resistance(material, 2.5, 5.0, temperature) * 10.0,
                WirePhysics.resistance(material, 2.5, 50.0, temperature), 1e-10)
        }
    }
}
