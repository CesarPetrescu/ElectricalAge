package mods.eln.fluid

import mods.eln.Eln.MODID
import mods.eln.Eln.fluidBlocks
import mods.eln.Eln.fluids
import net.minecraft.world.level.block.Block
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.material.Fluid
import mods.eln.fluid.FluidRegistry

/**
 * 1.12.2: a Fluid carries its own still/flow sprites, and the per-fluid ItemBucket +
 * FluidContainerRegistry pair is replaced by Forge's universal bucket
 * (FluidRegistry.addBucketForFluid; enabled in Eln's static initializer). Picking the
 * fluid back up is handled by the universal bucket's FillBucketEvent listener, so the
 * BuildCraft-derived BucketHandler is no longer needed.
 */
fun registerElnFluids() {
    ElnFluidRegistry.values().forEach {
        val fluid = Fluid(
            it.name,
            ResourceLocation.fromNamespaceAndPath(MODID, "blocks/fluids/${it.name}_still"),
            ResourceLocation.fromNamespaceAndPath(MODID, "blocks/fluids/${it.name}_flow"),
            it.color or 0xFF000000.toInt()
        ).setDensity(it.density).setViscosity(it.viscosity).setLuminosity(it.luminosity)
            .setTemperature(it.temperature).setGaseous(it.isGaseous)
        FluidRegistry.registerFluid(fluid)
        val fluidBlock: Block
        if (!fluid.canBePlacedInWorld()) {
            fluidBlock = BlockElnFluid(it.name, fluid, it.material, it.color)
            fluid.block = fluidBlock
            fluid.unlocalizedName = fluidBlock.translationKey.substring(5)
            fluids[ElnFluidRegistry.valueOf(it.name)] = fluid
            fluidBlocks[ElnFluidRegistry.valueOf(it.name)] = fluidBlock
            if (it.isBucketable) {
                FluidRegistry.addBucketForFluid(fluid)
            }
        }
    }
}
