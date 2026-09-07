package mods.eln.sixnode.electricalcable

import kotlin.test.*

class WirePhysicsTest {
    private val copper = UtilityCableMaterial.COPPER
    private val aluminum = UtilityCableMaterial.ALUMINUM
    @Test fun referenceCopperAndAluminumPerMeter() {
        assertEquals(.017241, WirePhysics.resistance(copper, 1.0), 1e-12)
        assertEquals(.028264, WirePhysics.resistance(aluminum, 1.0), 1e-12)
        assertEquals(.00521033545, WirePhysics.resistance(copper, 3.309), 1e-9)
    }
    @Test fun resistanceScalesWithLengthAndInverseArea() {
        for (material in UtilityCableMaterial.entries) for (area in listOf(.1288, .5176, 3.309, 107.219, 506.707)) {
            val r = WirePhysics.resistance(material, area)
            assertTrue(r > 0.0 && r.isFinite())
            assertEquals(r * 10, WirePhysics.resistance(material, area, 10.0), 1e-12)
            assertEquals(r / 2, WirePhysics.resistance(material, area * 2), 1e-12)
            assertEquals(0.0, WirePhysics.resistance(material, area, 0.0))
        }
    }
    @Test fun warmWireHasHigherResistance() {
        val r20 = WirePhysics.resistance(copper, 3.309)
        assertEquals(r20 * (1 + .00393 * 60), WirePhysics.resistance(copper, 3.309, celsius = 80.0), 1e-12)
        assertEquals(.028264 * (1 + .00403 * 60), WirePhysics.resistance(aluminum, 1.0, celsius = 80.0), 1e-12)
        assertTrue(WirePhysics.resistance(copper, 1.0, celsius = -273.15) > 0.0)
    }
    @Test fun metalAccountingDoesNotRoundSpoolLength() {
        assertEquals(.00896, WirePhysics.massKg(copper, 1.0, 1.0), 1e-12)
        assertEquals(.0027, WirePhysics.massKg(aluminum, 1.0, 1.0), 1e-12)
        assertEquals(WirePhysics.massKg(copper, 3.309, 64.0), WirePhysics.massKg(copper, 3.309, 32.0) * 2, 1e-12)
    }
    @Test fun invalidGeometryCannotPoisonSolver() {
        // NeoForge isolates kotlin-reflect from kotlin-test; assertFailsWith's KClass cast fails there.
        fun rejects(body: () -> Unit) {
            var rejected = false
            try { body() } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected, "Invalid geometry must throw IllegalArgumentException")
        }
        for (area in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY))
            rejects { WirePhysics.resistance(copper, area) }
        rejects { WirePhysics.resistance(copper, 1.0, -1.0) }
        rejects { WirePhysics.resistance(copper, 1.0, celsius = Double.NaN) }
    }
}
