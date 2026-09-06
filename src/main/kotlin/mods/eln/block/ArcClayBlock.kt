package mods.eln.block

import mods.eln.Eln
import mods.eln.generic.CreativeTabPopulator
import mods.eln.i18n.I18N.TR_NAME
import mods.eln.i18n.I18N.Type
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor

/** The two arc-furnace product blocks. The texture comes from assets/eln/blockstates/<registry name>.json. */
open class ArcProductBlock(private val key: String) : Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 6.0f).requiresCorrectToolForDrops()) {
    /** 1.7.10's `setTranslationKey`: the lang key stays `tile.<key>.name`. */
    override fun getDescriptionId(): String = "tile.$key.name"

    init {
        CreativeTabPopulator.register(Eln.creativeTabOresMaterials) { ItemStack(this) }
    }
}

class ArcClayBlock : ArcProductBlock(TR_NAME(Type.TILE, "arc_clay_block"))

class ArcMetalBlock : ArcProductBlock(TR_NAME(Type.TILE, "arc_metal_block"))
