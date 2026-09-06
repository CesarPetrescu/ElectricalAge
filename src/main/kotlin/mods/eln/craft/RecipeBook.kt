package mods.eln.craft

import mods.eln.misc.OreDict
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.DyeItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

/**
 * The crafting recipes [CraftingRecipes] declares, kept as data. 1.7.10 registered them into the
 * live crafting manager; since 1.13 vanilla recipes are JSON in the data pack, so the list is
 * emitted by the data generator (`./gradlew runData`, see [mods.eln.data.ElnRecipeProvider]) and
 * consulted at run time only for the "does anything craft this?" checks the mod makes.
 *
 * An ingredient is what the 1.7.10 code passed: an [ItemStack] (matched by item), an [ItemLike],
 * or an ore-dictionary name, which is the conventional item tag ([OreDict.tagFor]).
 */
sealed class ElnRecipe(val output: ItemStack) {
    class Shaped(output: ItemStack, val rows: List<String>, val keys: Map<Char, Any>) : ElnRecipe(output)
    class Shapeless(output: ItemStack, val inputs: List<Any>) : ElnRecipe(output)

    /** The ingredients as vanilla sees them; a name that is nobody's tag still becomes a tag (and an uncraftable recipe, as in 1.7.10). */
    companion object {
        @JvmStatic
        fun ingredient(spec: Any): Ingredient = when (spec) {
            is ItemStack -> Ingredient.of(spec.item)
            is ItemLike -> Ingredient.of(spec)
            is String -> Ingredient.of(OreDict.tagFor(spec))
            else -> throw IllegalArgumentException("not a recipe ingredient: $spec (${spec.javaClass})")
        }
    }
}

object RecipeBook {
    @JvmStatic
    val recipes = ArrayList<ElnRecipe>()

    /** (input, output, experience) of every furnace recipe the mod declares. */
    @JvmStatic
    val smelting = ArrayList<Triple<ItemStack, ItemStack, Float>>()

    fun clear() {
        recipes.clear()
        smelting.clear()
    }

    /** 1.7.10's `CraftingManager.findMatchingRecipe` output scan: whether some recipe makes this item. */
    @JvmStatic
    fun crafts(stack: ItemStack?): Boolean {
        if (stack == null || stack.isEmpty) return false
        return recipes.any { ItemStack.isSameItem(it.output, stack) }
    }
}

/** The 1.7.10 metadata items the recipes name: wool and dye colours, charcoal, the spruce sapling. */
object LegacyItems {
    private val wools: Array<Block> = arrayOf(
        Blocks.WHITE_WOOL, Blocks.ORANGE_WOOL, Blocks.MAGENTA_WOOL, Blocks.LIGHT_BLUE_WOOL, Blocks.YELLOW_WOOL,
        Blocks.LIME_WOOL, Blocks.PINK_WOOL, Blocks.GRAY_WOOL, Blocks.LIGHT_GRAY_WOOL, Blocks.CYAN_WOOL,
        Blocks.PURPLE_WOOL, Blocks.BLUE_WOOL, Blocks.BROWN_WOOL, Blocks.GREEN_WOOL, Blocks.RED_WOOL, Blocks.BLACK_WOOL
    )

    /** `new ItemStack(Blocks.wool, 1, meta)`: the wool metadata was the [DyeColor] id. */
    @JvmStatic
    fun wool(meta: Int): ItemStack = ItemStack(wools[meta and 15])

    /** `new ItemStack(Items.dye, 1, meta)`: the dye damage counted the colours backwards (0 = black, 15 = white). */
    @JvmStatic
    fun dye(meta: Int): ItemStack = ItemStack(DyeItem.byColor(DyeColor.byId(15 - (meta and 15))))

    @JvmStatic
    fun charcoal(): ItemStack = ItemStack(net.minecraft.world.item.Items.CHARCOAL)

    @JvmStatic
    fun spruceSapling(): ItemStack = ItemStack(Blocks.SPRUCE_SAPLING)
}
