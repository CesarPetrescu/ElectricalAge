package mods.eln.block

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import mods.eln.Eln
import mods.eln.i18n.I18N.TR_NAME
import mods.eln.i18n.I18N.Type
import net.minecraft.world.level.block.Block
import net.minecraft.block.material.Material
import net.minecraft.world.item.BlockItem

class ArcClayBlock : Block(Material.ROCK) {

    init {
        setTranslationKey(TR_NAME(Type.TILE, "arc_clay_block"))
        // 1.12.2: the texture comes from assets/eln/blockstates/<registry name>.json, not the block.
        setCreativeTab(Eln.creativeTabOresMaterials)
    }

}

class ArcMetalBlock : Block(Material.ROCK) {

    init {
        setTranslationKey(TR_NAME(Type.TILE, "arc_metal_block"))
        setCreativeTab(Eln.creativeTabOresMaterials)
    }

}

class ArcMetalItemBlock(block: Block?) : BlockItem(block)

class ArcClayItemBlock(block: Block?) : BlockItem(block)
