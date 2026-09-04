package mods.eln.block

import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import mods.eln.Eln
import mods.eln.i18n.I18N.TR_NAME
import mods.eln.i18n.I18N.Type
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.item.ItemBlock

class ArcClayBlock : Block(Material.ROCK) {

    init {
        setTranslationKey(TR_NAME(Type.TILE, "arc_clay_block"))
        setBlockTextureName("eln:$name")
        setCreativeTab(Eln.creativeTabOresMaterials)
    }



    companion object {
        private const val name = "arc_clay_block"
    }
}

class ArcMetalBlock : Block(Material.ROCK) {

    init {
        setTranslationKey(TR_NAME(Type.TILE, "arc_metal_block"))
        setBlockTextureName("eln:$name")
        setCreativeTab(Eln.creativeTabOresMaterials)
    }



    companion object {
        private const val name = "arc_metal_block"
    }
}

class ArcMetalItemBlock(block: Block?) : ItemBlock(block)

class ArcClayItemBlock(block: Block?) : ItemBlock(block)
