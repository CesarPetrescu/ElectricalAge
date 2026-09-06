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
import net.minecraft.world.item.ItemStack
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
                    val model = modLoc("block/${id.path}")
                    if (existingFileHelper.exists(model, PackType.CLIENT_RESOURCES, ".json", "models")) withExistingParent(id.path, model)
                    else if (item.descriptor is mods.eln.client.itemrender.IItemRenderer) {
                        val asBlock = mods.eln.client.itemrender.NodeItemRenderer.inventoryAsBlock(item.descriptor, ItemStack(item)) { texture ->
                            existingFileHelper.exists(texture, PackType.CLIENT_RESOURCES)
                        }
                        if (asBlock && item.descriptor.iconName != null && !existingFileHelper.exists(modLoc("textures/blocks/${item.descriptor.iconName}.png"), PackType.CLIENT_RESOURCES))
                            Eln.LOGGER.warn("datagen: node item {} has no sprite textures/blocks/{}.png; its model is the inventory icon", id, item.descriptor.iconName)
                        nodeItem(id, asBlock)
                    }
                }
                is net.minecraft.world.item.SpawnEggItem -> withExistingParent(id.path, mcLoc("item/template_spawn_egg"))
                is net.minecraft.world.item.BucketItem ->
                    withExistingParent(id.path, ResourceLocation.fromNamespaceAndPath("neoforge", "item/bucket"))
                        .customLoader { parent, helper -> net.neoforged.neoforge.client.model.generators.loaders.DynamicFluidContainerModelBuilder.begin(parent, helper).fluid(item.content) }
                        .end()
                is net.minecraft.world.item.BlockItem -> {
                    // a block item is its block model; the dev conduit has none and is invisible
                    val model = modLoc("block/${id.path}")
                    if (existingFileHelper.exists(model, PackType.CLIENT_RESOURCES, ".json", "models")) withExistingParent(id.path, model)
                    else withExistingParent(id.path, modLoc("block/invisible"))
                }
            }
        }
    }

    /**
     * A node item (six-node or transparent-node placer): drawn by [mods.eln.client.itemrender.NodeItemRenderer]
     * through the descriptor's own render body, so the model is vanilla's built-in entity model
     * plus display transforms - a block's in hand and on the ground, none in the inventory, where
     * the descriptor draws its flat icon.
     */
    private fun nodeItem(id: ResourceLocation, guiAsBlock: Boolean) {
        val builder = getBuilder(id.path)
            .parent(net.neoforged.neoforge.client.model.generators.ModelFile.UncheckedModelFile("minecraft:builtin/entity"))
            .transforms()
        if (guiAsBlock) builder.transform(net.minecraft.world.item.ItemDisplayContext.GUI).rotation(30f, 225f, 0f).scale(0.625f).end()
        else builder.transform(net.minecraft.world.item.ItemDisplayContext.GUI).end()
        builder
            .transform(net.minecraft.world.item.ItemDisplayContext.GROUND).translation(0f, 3f, 0f).scale(0.25f).end()
            .transform(net.minecraft.world.item.ItemDisplayContext.FIXED).scale(0.5f).end()
            .transform(net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).rotation(75f, 45f, 0f).translation(0f, 2.5f, 0f).scale(0.375f).end()
            .transform(net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND).rotation(75f, 45f, 0f).translation(0f, 2.5f, 0f).scale(0.375f).end()
            .transform(net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).rotation(0f, 45f, 0f).scale(0.4f).end()
            .transform(net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND).rotation(0f, 225f, 0f).scale(0.4f).end()
            .end()
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
                // the two single-node blocks were plain textured cubes on 1.7.10 (registerBlockIcons)
                is mods.eln.simplenode.energyconverter.EnergyConverterElnToOtherBlock -> simpleBlock(block, models().cubeAll(id.path, modLoc("blocks/elntoic2lvu_side")))
                is mods.eln.simplenode.computerprobe.ComputerProbeBlock -> simpleBlock(block, models().cube(id.path,
                    modLoc("blocks/computerprobe_yn"), modLoc("blocks/computerprobe_yp"), modLoc("blocks/computerprobe_zn"),
                    modLoc("blocks/computerprobe_zp"), modLoc("blocks/computerprobe_xn"), modLoc("blocks/computerprobe_xp")
                ).texture("particle", modLoc("blocks/computerprobe_yp")))
                // a fluid block renders through the fluid's client extensions; vanilla's water model is the empty one
                is net.minecraft.world.level.block.LiquidBlock -> simpleBlock(block, models().getExistingFile(mcLoc("block/water")))
            }
        }
    }
}
