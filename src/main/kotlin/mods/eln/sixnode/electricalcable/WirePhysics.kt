package mods.eln.sixnode.electricalcable

/** DC conductor model. Area is mm² PER CORE, length metres, temperature absolute °C.
 * Copper: NBS Handbook 100 (IACS); aluminum: electrical-conductor grade, 61% IACS.
 * See docs/wire-production.md for sources and simulation limits.
 */
object WirePhysics {
    fun resistivity20(material: UtilityCableMaterial): Double = when (material) {
        UtilityCableMaterial.COPPER -> 0.017241
        UtilityCableMaterial.ALUMINUM -> 0.028264
    }

    fun temperatureCoefficient(material: UtilityCableMaterial): Double = when (material) {
        UtilityCableMaterial.COPPER -> 0.00393
        UtilityCableMaterial.ALUMINUM -> 0.00403
    }

    fun density(material: UtilityCableMaterial): Double = when (material) {
        UtilityCableMaterial.COPPER -> 8960.0
        UtilityCableMaterial.ALUMINUM -> 2700.0
    }

    fun resistance(material: UtilityCableMaterial, areaMm2: Double, meters: Double = 1.0, celsius: Double = 20.0): Double {
        require(areaMm2.isFinite() && areaMm2 > 0.0)
        require(meters.isFinite() && meters >= 0.0)
        require(celsius.isFinite())
        // The linear model is not a cryogenic/superconductivity model. Keep the solver positive.
        val factor = (1.0 + temperatureCoefficient(material) * (celsius - 20.0)).coerceAtLeast(0.05)
        return resistivity20(material) * meters / areaMm2 * factor
    }

    fun massKg(material: UtilityCableMaterial, totalAreaMm2: Double, meters: Double): Double {
        require(totalAreaMm2.isFinite() && totalAreaMm2 > 0.0 && meters.isFinite() && meters >= 0.0)
        return density(material) * totalAreaMm2 * 1e-6 * meters
    }
}
