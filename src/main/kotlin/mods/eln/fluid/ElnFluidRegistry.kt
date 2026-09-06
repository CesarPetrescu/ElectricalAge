package mods.eln.fluid

/** The mod's own fluids (registered by FluidRegistration; NeoForge FluidType/BaseFlowingFluid, phase 2). */
@Suppress("EnumEntryName")
enum class ElnFluidRegistry(
    val color: Int,
    val density: Int,
    val viscosity: Int,
    val luminosity: Int,
    val temperature: Int,
    val isGaseous: Boolean,
    val isBucketable: Boolean
) {
    //name(Color,Density,Viscosity, luminosity, isGaseous, isBucktable),
    hot_water(4644607, 1000, 1000, 0, 333, false, true),
    cold_water(4644607, 1000, 1000, 0, 288, false, true)
}
