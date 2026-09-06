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
import net.minecraft.data.loot.LootTableProvider
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider

/**
 * Data generation (`./gradlew runData`): everything 1.7.10 registered in code that is data since
 * 1.13, written to src/generated/resources from the mod's own registrations - the crafting and
 * smelting recipes [mods.eln.craft.RecipeBook] holds, the ore-dictionary names as `c:` item
 * tags, the ore blocks' loot tables, mining tags and world generation, and a JSON model per item
 * and block (~600 of them). The output is committed; the generator is the source.
 */
@EventBusSubscriber(modid = Eln.MODID, bus = EventBusSubscriber.Bus.MOD)
object ElnDataGenerators {
    @SubscribeEvent
    fun gather(event: GatherDataEvent) {
        val generator = event.generator
        val output = generator.packOutput
        val lookup = event.lookupProvider
        val helper = event.existingFileHelper

        val blockTags = generator.addProvider(event.includeServer(), ElnBlockTags(output, lookup, helper))
        generator.addProvider(event.includeServer(), ElnItemTags(output, lookup, blockTags.contentsGetter(), helper))
        generator.addProvider(event.includeServer(), ElnRecipeProvider(output, lookup))
        generator.addProvider(event.includeServer(), LootTableProvider(output, emptySet(),
            listOf(LootTableProvider.SubProviderEntry({ ElnBlockLoot(it) }, LootContextParamSets.BLOCK)), lookup))
        generator.addProvider(event.includeServer(), DatapackBuiltinEntriesProvider(output, lookup, ElnWorldgen.builder(), setOf(Eln.MODID)))

        generator.addProvider(event.includeClient(), ElnBlockStateProvider(output, helper))
        generator.addProvider(event.includeClient(), ElnItemModelProvider(output, helper))
    }
}

class ElnItemModelProvider(output: PackOutput, helper: ExistingFileHelper) : ItemModelProvider(output, Eln.MODID, helper) {
    override fun registerModels() {
        ElnRegistry.registeredItems.forEach { (id, item) ->
            when (item) {
                is DescriptorItem<*> -> flatItem(id, item.descriptor.iconPath, item.descriptor.voltageLevelColor)
                is DescriptorBlockItem<*> -> {
                    // Node items (six-node, transparent-node) are drawn by their descriptor's
                    // renderer, not a JSON model; only blocks with a generated block model get one.
                    val model = modLoc("block/${id.path}")
                    if (existingFileHelper.exists(model, PackType.CLIENT_RESOURCES, ".json", "models")) withExistingParent(id.path, model)
                }
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
