package mods.eln.datagen

import mods.eln.Eln
import mods.eln.generic.DescriptorBlockItem
import mods.eln.generic.DescriptorItem
import mods.eln.misc.VoltageLevelColor
import mods.eln.ore.OreBlock
import mods.eln.registration.ElnRegistry
import net.minecraft.data.PackOutput
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.model.generators.BlockStateProvider
import net.neoforged.neoforge.client.model.generators.ItemModelProvider
import net.neoforged.neoforge.common.data.ExistingFileHelper
import net.neoforged.neoforge.data.event.GatherDataEvent

/**
 * Data generation (`./gradlew runData`). 1.13+ wants a JSON model per item and block; with ~260
 * descriptor items that is not something to write by hand, so the providers walk what the mod
 * staged in [ElnRegistry] and emit the models into src/generated/resources.
 */
@EventBusSubscriber(modid = Eln.MODID, bus = EventBusSubscriber.Bus.MOD)
object ElnDataGenerators {
    @SubscribeEvent
    fun gather(event: GatherDataEvent) {
        val generator = event.generator
        val output = generator.packOutput
        val helper = event.existingFileHelper
        generator.addProvider(event.includeClient(), ElnBlockStateProvider(output, helper))
        generator.addProvider(event.includeClient(), ElnItemModelProvider(output, helper))
    }
}

class ElnItemModelProvider(output: PackOutput, helper: ExistingFileHelper) : ItemModelProvider(output, Eln.MODID, helper) {
    override fun registerModels() {
        ElnRegistry.registeredItems.forEach { (id, item) ->
            when (item) {
                is DescriptorItem<*> -> flatItem(id, item.descriptor.iconPath, item.descriptor.voltageLevelColor)
                is DescriptorBlockItem<*> -> withExistingParent(id.path, modLoc("block/${id.path}"))
            }
        }
    }

    /**
     * A plain icon item. The voltage-level background that 1.7.10 drew in a custom render pass is
     * the first layer of the model; the icon sits on top. Items without a texture are reported and
     * skipped rather than failing the whole run.
     */
    private fun flatItem(id: ResourceLocation, icon: String?, level: VoltageLevelColor) {
        val texture = icon?.let { modLoc("items/$it") }
        if (texture == null || !existingFileHelper.exists(texture, PackType.CLIENT_RESOURCES, ".png", "textures")) {
            Eln.LOGGER.warn("datagen: no item texture for {} ({})", id, texture)
            return
        }
        val builder = withExistingParent(id.path, mcLoc("item/generated"))
        val background = level.textureName?.let { modLoc("voltages/$it") }
        if (background != null) {
            builder.texture("layer0", background).texture("layer1", texture)
        } else {
            builder.texture("layer0", texture)
        }
    }
}

class ElnBlockStateProvider(output: PackOutput, helper: ExistingFileHelper) : BlockStateProvider(output, Eln.MODID, helper) {
    override fun registerStatesAndModels() {
        ElnRegistry.registeredBlocks.forEach { (id, block) ->
            when (block) {
                is OreBlock -> simpleBlock(block, models().cubeAll(id.path, modLoc("blocks/${block.descriptor.iconName}")))
            }
        }
    }
}
