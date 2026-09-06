package mods.eln.fluid

import mods.eln.registration.ElnRegistry

import net.minecraft.block.material.Material
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.BlockItem
import net.minecraftforge.fluids.BlockFluidClassic
import net.minecraft.world.level.material.Fluid
import mods.eln.fluid.FluidRegistry

//shamelessly lifted from IC2
class BlockElnFluid(
    internalName: String,
    fluid: Fluid,
    material: Material, val color: Int
) : BlockFluidClassic(fluid, material) {

    init {
        setTranslationKey(internalName)
        ElnRegistry.registerBlock(this, internalName, BlockItem::class.java)
        if (density <= FluidRegistry.WATER.density) {
            displacements[Blocks.WATER] = false
            displacements[Blocks.FLOWING_WATER] = false
        }
        if (density <= FluidRegistry.LAVA.density) {
            displacements[Blocks.LAVA] = false
            displacements[Blocks.FLOWING_LAVA] = false
        }
    }


}
