package mods.eln.fluid

import net.minecraftforge.fml.common.registry.GameRegistry
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import net.minecraft.block.material.Material
import net.minecraft.client.renderer.texture.IIconRegister
import net.minecraft.init.Blocks
import net.minecraft.item.ItemBlock
import net.minecraft.util.IIcon
import net.minecraftforge.fluids.BlockFluidClassic
import net.minecraftforge.fluids.Fluid
import net.minecraftforge.fluids.FluidRegistry
import kotlin.Array
import kotlin.Int
import kotlin.String
import kotlin.arrayOf

//shamelessly lifted from IC2
class BlockElnFluid(
    internalName: String?,
    fluid: Fluid,
    material: Material?, val color: Int
) : BlockFluidClassic(fluid, material) {

    var fluidIcon: Array<IIcon> = arrayOf()

    init {
        setTranslationKey(internalName)
        GameRegistry.registerBlock(this, ItemBlock::class.java, internalName)
        if (density <= FluidRegistry.WATER.density) {
            displacements[Blocks.WATER] = false
            displacements[Blocks.FLOWING_WATER] = false
        }
        if (density <= FluidRegistry.LAVA.density) {
            displacements[Blocks.LAVA] = false
            displacements[Blocks.FLOWING_LAVA] = false
        }
    }

    @SideOnly(Side.CLIENT)
    override fun registerBlockIcons(iconRegister: IIconRegister) {
        fluidIcon = arrayOf(
            iconRegister.registerIcon("eln:" + "fluids/" + fluidName + "_still"),
            iconRegister.registerIcon("eln:" + "fluids/" + fluidName + "_flow")
        )
    }

    @SideOnly(Side.CLIENT)
    override fun getIcon(side: Int, meta: Int): IIcon {
        return if (side != 0 && side != 1) fluidIcon[1] else fluidIcon[0]
    }
}
