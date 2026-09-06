package mods.eln.ore

import mods.eln.generic.DescriptorBlockItem
import mods.eln.generic.GenericItemBlockUsingDamage
import mods.eln.registration.ElnRegistry
import net.minecraft.world.item.Item

/** The ore family: each descriptor gets its own block (registered under the descriptor's name) and a BlockItem for it. */
class OreItem : GenericItemBlockUsingDamage<OreDescriptor>(null, "Eln.Ore") {
    override fun addDescriptor(id: Int, descriptor: OreDescriptor) {
        ElnRegistry.registerBlock(descriptor.name, { OreBlock(descriptor).also { descriptor.block = it } }, null)
        super.addDescriptor(id, descriptor)
    }

    override fun newItem(id: Int, descriptor: OreDescriptor): Item =
        DescriptorBlockItem(this, descriptor, id, descriptor.block, newProperties(descriptor))
}
