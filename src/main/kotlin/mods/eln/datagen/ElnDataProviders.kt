package mods.eln.datagen

import mods.eln.Eln
import mods.eln.misc.editTag
import mods.eln.misc.tagCompound
import mods.eln.craft.CraftingRecipes
import mods.eln.craft.ElnRecipe
import mods.eln.craft.RecipeBook
import mods.eln.misc.OreDict
import mods.eln.ore.OreBlock
import mods.eln.ore.OreDescriptor
import mods.eln.registration.ElnRegistry
import mods.eln.worldgen.ElnOreBiomeModifier
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.data.PackOutput
import net.minecraft.data.loot.BlockLootSubProvider
import net.minecraft.data.loot.LootTableProvider
import net.minecraft.data.recipes.RecipeCategory
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.RecipeProvider
import net.minecraft.data.recipes.ShapedRecipeBuilder
import net.minecraft.data.recipes.ShapelessRecipeBuilder
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder
import net.minecraft.data.worldgen.BootstrapContext
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.BlockTags
import net.minecraft.tags.ItemTags
import net.minecraft.tags.TagKey
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.levelgen.VerticalAnchor
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration
import net.minecraft.world.level.levelgen.placement.BiomeFilter
import net.minecraft.world.level.levelgen.placement.CountPlacement
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement
import net.minecraft.world.level.levelgen.placement.InSquarePlacement
import net.minecraft.world.level.levelgen.placement.PlacedFeature
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest
import net.neoforged.neoforge.common.Tags
import net.neoforged.neoforge.common.data.BlockTagsProvider
import net.neoforged.neoforge.common.data.ExistingFileHelper
import net.minecraft.data.tags.ItemTagsProvider
import net.minecraft.data.tags.TagsProvider
import net.neoforged.neoforge.common.world.BiomeModifier
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.util.concurrent.CompletableFuture

/** The server-side providers; [ElnDataGenerators] registers them. */
internal object ElnData {
    /** The ore descriptors, with the dictionary name each ore block was registered under ("oreCopper"). */
    fun ores(): List<Pair<OreDescriptor, String?>> =
        Eln.oreItem.subItemList.values.filterNotNull().map { desc ->
            val name = ElnRegistry.oreEntries.firstOrNull { (n, s) -> n.startsWith("ore") && s.get().item === desc.block.asItem() }?.first
            desc to name
        }

    fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(Eln.MODID, path)
}

class ElnRecipeProvider(output: PackOutput, lookup: CompletableFuture<HolderLookup.Provider>) : RecipeProvider(output, lookup) {
    private val used = HashMap<String, Int>()

    /**
     * The electrical tools stamp a random "rand" into every fresh stack (1.7.10's way of keeping
     * them from stacking). A recipe result is one fixed stack anyway, and a value that changes on
     * every run makes the generated files churn, so it is pinned.
     */
    private fun deterministic(stack: ItemStack): ItemStack {
        val tag = stack.tagCompound ?: return stack
        if (!tag.contains("rand")) return stack
        return stack.copy().also { copy -> copy.editTag { it.putInt("rand", 0) } }
    }

    /** eln:<output item>, then _2, _3 ... when several recipes make the same item. */
    private fun nameFor(output: ItemStack, suffix: String = ""): ResourceLocation {
        val base = BuiltInRegistries.ITEM.getKey(output.item).path + suffix
        val n = (used[base] ?: 0) + 1
        used[base] = n
        return ElnData.id(if (n == 1) base else "${base}_$n")
    }

    private fun unlock(spec: Any): Pair<String, net.minecraft.advancements.Criterion<*>> = when (spec) {
        is ItemStack -> "has_" + BuiltInRegistries.ITEM.getKey(spec.item).path to has(spec.item)
        is ItemLike -> "has_" + BuiltInRegistries.ITEM.getKey(spec.asItem()).path to has(spec)
        is String -> "has_" + OreDict.tagFor(spec).location().path.replace('/', '_') to has(OreDict.tagFor(spec))
        else -> throw IllegalArgumentException("$spec")
    }

    override fun buildRecipes(out: RecipeOutput) {
        // Data generation stops after the registry events; the declarations normally run at load-complete.
        CraftingRecipes.itemCrafting()
        for (recipe in RecipeBook.recipes) {
            if (recipe.output.isEmpty) continue
            val output = deterministic(recipe.output)
            when (recipe) {
                is ElnRecipe.Shaped -> {
                    val builder = ShapedRecipeBuilder.shaped(RecipeCategory.MISC, output)
                    recipe.rows.forEach { builder.pattern(it) }
                    recipe.keys.forEach { (c, spec) -> builder.define(c, ElnRecipe.ingredient(spec)) }
                    val (name, criterion) = unlock(recipe.keys.values.first())
                    builder.unlockedBy(name, criterion).save(out, nameFor(output))
                }
                is ElnRecipe.Shapeless -> {
                    val builder = ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, output)
                    recipe.inputs.forEach { builder.requires(ElnRecipe.ingredient(it)) }
                    val (name, criterion) = unlock(recipe.inputs.first())
                    builder.unlockedBy(name, criterion).save(out, nameFor(output))
                }
            }
        }
        for ((input, result, xp) in RecipeBook.smelting) {
            if (input.isEmpty || result.isEmpty) continue
            val (name, criterion) = unlock(input)
            SimpleCookingRecipeBuilder.smelting(Ingredient.of(input.item), RecipeCategory.MISC, result, xp, 200)
                .unlockedBy(name, criterion)
                .save(out, nameFor(result, "_from_smelting_" + BuiltInRegistries.ITEM.getKey(input.item).path))
        }
    }
}

class ElnBlockTags(output: PackOutput, lookup: CompletableFuture<HolderLookup.Provider>, helper: ExistingFileHelper?) :
    BlockTagsProvider(output, lookup, Eln.MODID, helper) {
    override fun addTags(provider: HolderLookup.Provider) {
        val pickaxe = tag(BlockTags.MINEABLE_WITH_PICKAXE)
        for ((desc, oreName) in ElnData.ores()) {
            pickaxe.add(desc.block)
            tag(BlockTags.NEEDS_STONE_TOOL).add(desc.block)
            tag(Tags.Blocks.ORES).add(desc.block)
            tag(Tags.Blocks.ORES_IN_GROUND_STONE).add(desc.block)
            if (oreName != null) tag(BlockTags.create(OreDict.tagFor(oreName).location())).add(desc.block)
        }
        for ((id, block) in ElnRegistry.registeredBlocks) {
            if (block !is OreBlock && block !is net.minecraft.world.level.block.LiquidBlock &&
                id.path != "sixnode" && id.path != "transparentnode" && id.path != "ghostblock" && id.path != "lightblock") pickaxe.add(block)
        }
    }
}

class ElnItemTags(output: PackOutput, lookup: CompletableFuture<HolderLookup.Provider>, blocks: CompletableFuture<TagsProvider.TagLookup<Block>>, helper: ExistingFileHelper?) :
    ItemTagsProvider(output, lookup, blocks, Eln.MODID, helper) {
    override fun addTags(provider: HolderLookup.Provider) {
        // Every OreDictionary.registerOre of 1.7.10 is the conventional item tag; the folder tag
        // (c:ingots) contains the specific one (c:ingots/copper), as the convention has it.
        val seen = HashSet<TagKey<Item>>()
        for ((name, stack) in ElnRegistry.oreEntries) {
            val tagKey = OreDict.tagFor(name)
            tag(tagKey).add(stack.get().item)
            val path = tagKey.location().path
            if (tagKey.location().namespace == "c" && path.contains('/') && seen.add(tagKey)) {
                tag(ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", path.substringBefore('/')))).addTag(tagKey)
            }
        }
    }
}

class ElnBlockLoot(provider: HolderLookup.Provider) : BlockLootSubProvider(emptySet(), FeatureFlags.REGISTRY.allFlags(), provider) {
    private val blocks: List<Block> = ElnRegistry.registeredBlocks.filter { (id, block) ->
        id.path != "sixnode" && id.path != "transparentnode" && id.path != "ghostblock" && id.path != "lightblock" &&
            block !is net.minecraft.world.level.block.LiquidBlock
    }.values.toList()

    override fun generate() = blocks.forEach { dropSelf(it) }

    override fun getKnownBlocks(): Iterable<Block> = blocks
}

/** The ore veins, from the 1.7.10 spawn numbers: `spawnRate` veins per chunk, uniform between the two heights, of about the mean vein size. */
object ElnWorldgen {
    private fun keyPath(desc: OreDescriptor) = BuiltInRegistries.BLOCK.getKey(desc.block).path

    fun builder(): net.minecraft.core.RegistrySetBuilder = net.minecraft.core.RegistrySetBuilder()
        .add(Registries.CONFIGURED_FEATURE) { ctx: BootstrapContext<ConfiguredFeature<*, *>> ->
            for ((desc, _) in ElnData.ores()) {
                val targets = listOf(
                    OreConfiguration.target(TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES), desc.block.defaultBlockState()),
                    OreConfiguration.target(TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES), desc.block.defaultBlockState())
                )
                val size = (desc.spawnSizeMin + desc.spawnSizeMax) / 2
                ctx.register(ResourceKey.create(Registries.CONFIGURED_FEATURE, ElnData.id(keyPath(desc))),
                    ConfiguredFeature(Feature.ORE, OreConfiguration(targets, size)))
            }
        }
        .add(Registries.PLACED_FEATURE) { ctx: BootstrapContext<PlacedFeature> ->
            val features = ctx.lookup(Registries.CONFIGURED_FEATURE)
            for ((desc, _) in ElnData.ores()) {
                val configured = features.getOrThrow(ResourceKey.create(Registries.CONFIGURED_FEATURE, ElnData.id(keyPath(desc))))
                ctx.register(ResourceKey.create(Registries.PLACED_FEATURE, ElnData.id(keyPath(desc))),
                    PlacedFeature(configured, listOf(
                        CountPlacement.of(desc.spawnRate), InSquarePlacement.spread(),
                        HeightRangePlacement.uniform(VerticalAnchor.absolute(desc.spawnHeightMin), VerticalAnchor.absolute(desc.spawnHeightMax)),
                        BiomeFilter.biome()
                    )))
            }
        }
        .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS) { ctx: BootstrapContext<BiomeModifier> ->
            val placed = ctx.lookup(Registries.PLACED_FEATURE)
            for ((desc, _) in ElnData.ores()) {
                val feature = placed.getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, ElnData.id(keyPath(desc))))
                ctx.register(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, ElnData.id(keyPath(desc))),
                    ElnOreBiomeModifier(feature, desc.configKey ?: "worldgen.ores.${keyPath(desc)}.enabled", desc.configDefault))
            }
        }
}
