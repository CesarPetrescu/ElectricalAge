package mods.eln.wiki

import mods.eln.i18n.I18N.tr
import mods.eln.misc.McRecipes
import mods.eln.misc.McRegistries
import mods.eln.misc.Recipe
import mods.eln.misc.RecipesList
import mods.eln.misc.Utils
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CraftingRecipe
import net.minecraft.world.item.crafting.RecipeType

/** One recipe per row; output counts and every processing output remain visible. */
object WikiRecipes {
    @JvmStatic fun text(view: GuiVerticalExtender, y: Int, text: String): Int {
        val label = WikiText(8, y, text, view.contentWidth() - 16)
        view.add(label)
        return label.yMax + 5
    }

    @JvmStatic fun addCrafting(view: GuiVerticalExtender, start: Int, stack: ItemStack): Int {
        var y = start
        val recipes = McRecipes.manager()?.getAllRecipesFor(RecipeType.CRAFTING).orEmpty()
            .map { it.value() }.filter { !it.getResultItem(McRegistries.access()).isEmpty }
        val outputs = recipes.filter { ItemStack.isSameItem(it.getResultItem(McRegistries.access()), stack) }
        y = text(view, y, if (outputs.isEmpty()) tr("No crafting-table recipe.") else tr("Recipe:"))
        for (recipe in outputs) y = crafting(view, y, recipe)
        val uses = recipes.filter { recipe -> recipe.ingredients.any { it.test(stack) } }
        if (uses.isNotEmpty()) {
            y = text(view, y + 6, tr("Can be used to craft:"))
            for (recipe in uses) y = crafting(view, y, recipe)
        }
        return y
    }

    private fun crafting(view: GuiVerticalExtender, start: Int, recipe: CraftingRecipe): Int {
        val output = recipe.getResultItem(McRegistries.access())
        val y = text(view, start, output.hoverName.string)
        val grid = Utils.getItemStackGrid(recipe) ?: error("Cannot display crafting grid for ${output.hoverName.string}")
        for (row in 0..2) for (col in 0..2)
            view.add(GuiItemStack(8 + col * 20, y + row * 20, grid[row][col], view.helper))
        view.add(WikiText(78, y + 24, "→", 20))
        view.add(GuiItemStack(108, y + 20, output, view.helper))
        return y + 68
    }

    @JvmStatic fun addProcessing(view: GuiVerticalExtender, start: Int, stack: ItemStack): Int {
        var y = start
        for ((label, recipes) in listOf(
            tr("Created by:") to RecipesList.getGlobalRecipeWithOutput(stack),
            tr("Can create:") to RecipesList.getGlobalRecipeWithInput(stack)
        )) {
            if (recipes.isEmpty()) continue
            y = text(view, y + 6, label)
            for (recipe in recipes) y = processing(view, y, recipe)
        }
        return y
    }

    @JvmStatic fun processing(view: GuiVerticalExtender, start: Int, recipe: Recipe): Int {
        var y = text(view, start, Utils.plotEnergy(tr("Cost"), recipe.energy))
        view.add(GuiItemStack(8, y, recipe.input, view.helper))
        view.add(WikiText(34, y + 4, "→", 20))
        var x = 58
        for (output in recipe.output) {
            if (x + 18 > view.contentWidth() - 8) { x = 8; y += 22 }
            view.add(GuiItemStack(x, y, output, view.helper))
            x += 22
        }
        y += 24
        if (recipe.machineList.isNotEmpty()) {
            y = text(view, y, tr("Machines:"))
            x = 8
            for (machine in recipe.machineList) {
                if (x + 18 > view.contentWidth() - 8) { x = 8; y += 22 }
                view.add(GuiItemStack(x, y, machine, view.helper))
                x += 22
            }
            y += 24
        }
        return y + 10
    }
}
