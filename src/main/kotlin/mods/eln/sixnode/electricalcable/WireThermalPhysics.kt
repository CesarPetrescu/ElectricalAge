package mods.eln.sixnode.electricalcable

import kotlin.math.*

/** Lumped, one-temperature conductor model; SI throughout. See docs/wire-thermal-model.md.
 * Cp is a linear approximation between room-temperature and solid melting-point NIST values.
 * A generic 0.5 mm jacket adds thermal resistance, not fictitious conductor mass or ampacity.
 */
class WireThermalPhysics(val material: UtilityCableMaterial, val totalAreaMm2: Double, val meters: Double = 1.0) {
    init { require(totalAreaMm2.isFinite() && totalAreaMm2 > 0 && meters.isFinite() && meters > 0) }
    val massKg = WirePhysics.massKg(material, totalAreaMm2, meters)
    private val cp20 = if (material == UtilityCableMaterial.COPPER) 385.0 else 897.0
    private val cpSlope = if (material == UtilityCableMaterial.COPPER) .132 else .506
    private val conductivity = if (material == UtilityCableMaterial.COPPER) 400.0 else 237.0
    val meltingCelsius = material.meltingPointCelsius
    val fusionJoules = massKg * if (material == UtilityCableMaterial.COPPER) 206_700.0 else 397_000.0
    val meltingEnthalpy = sensibleEnthalpy(meltingCelsius)
    val failureEnthalpy = meltingEnthalpy + fusionJoules
    val endpointThermalResistance = meters / (2 * conductivity * totalAreaMm2 * 1e-6)

    fun capacity(celsius: Double) = massKg * (cp20 + cpSlope * (celsius - 20).coerceIn(0.0, meltingCelsius - 20))

    /** Joules relative to solid copper/aluminum at 20 C, not relative to changing biome ambient. */
    fun sensibleEnthalpy(celsius: Double): Double {
        require(celsius.isFinite())
        val delta = celsius - 20.0
        return massKg * (cp20 * delta + .5 * cpSlope * max(0.0, delta).pow(2))
    }

    fun temperature(enthalpy: Double): Double {
        require(enthalpy.isFinite())
        if (enthalpy >= meltingEnthalpy) return meltingCelsius
        val specific = enthalpy / massKg
        return 20 + if (specific < 0) specific / cp20
            else 2 * specific / (cp20 + sqrt(cp20 * cp20 + 2 * cpSlope * specific))
    }

    /** Secant radiation conductance + still-air convection, with jacket conduction in series.
     * Uses Kelvin for radiation; outer surface represented by an equivalent round bundle.
     * No unsupported claim of certified ampacity, airflow or floor-contact heat sinking.
     */
    fun coolingConductance(celsius: Double, ambientCelsius: Double, insulated: Boolean): Double {
        require(celsius.isFinite() && ambientCelsius.isFinite())
        val inner = sqrt(totalAreaMm2 * 1e-6 / PI)
        val outer = inner + if (insulated) .0005 else 0.0
        val surface = 2 * PI * outer * meters
        val t = (celsius + 273.15).coerceAtLeast(1.0)
        val a = (ambientCelsius + 273.15).coerceAtLeast(1.0)
        val emissivity = if (insulated) .9 else .7 // generic jacket / oxidized conductor
        val h = 8.0 + emissivity * 5.670374419e-8 * (t*t + a*a) * (t+a)
        val jacketR = if (insulated) ln(outer / inner) / (2 * PI * .2 * meters) else 0.0
        return 1.0 / (1.0 / (surface * h) + jacketR)
    }
}
