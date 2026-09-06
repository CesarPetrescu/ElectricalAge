package mods.eln.fluid

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.Fluids

/**
 * The Forge 1.7-1.12 `FluidRegistry` calls the mod makes, on the vanilla fluid registry. Fluids
 * are registry objects since 1.13; a "fluid name" is a registry id (`minecraft:water`), and the
 * bare names the config files use are looked up in every namespace.
 */
object FluidRegistry {
    @JvmField
    val WATER: Fluid = Fluids.WATER

    @JvmField
    val LAVA: Fluid = Fluids.LAVA

    @JvmStatic
    fun getFluid(name: String?): Fluid? {
        if (name.isNullOrEmpty()) return null
        ResourceLocation.tryParse(name)?.let { id ->
            if (id.namespace != "minecraft" || name.contains(':')) BuiltInRegistries.FLUID.getOptional(id).orElse(null)?.let { return it }
        }
        // a bare 1.7.10 name: the first registered fluid with that path
        return BuiltInRegistries.FLUID.entrySet().firstOrNull { it.key.location().path == name }?.value
    }

    @JvmStatic
    fun getFluidName(fluid: Fluid?): String? = fluid?.let { BuiltInRegistries.FLUID.getKey(it).toString() }

    /** The bare 1.7.10 fluid name ("water", "hot_water"): the registry path, which the fuel config keys on. */
    @JvmStatic
    fun legacyName(fluid: Fluid?): String? = fluid?.let { BuiltInRegistries.FLUID.getKey(it).path }

    @JvmStatic
    fun isFluidRegistered(name: String?): Boolean = getFluid(name) != null
}
