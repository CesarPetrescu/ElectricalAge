package mods.eln.ore

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor

/** One block per [OreDescriptor]; the 1.7.10 metadata variants became separate blocks with 1.13. */
class OreBlock(val descriptor: OreDescriptor) : Block(
    BlockBehaviour.Properties.of()
        .mapColor(MapColor.STONE)
        .strength(3.0f, 5.0f)   // hardness, explosion resistance, as before
        .requiresCorrectToolForDrops()
)
