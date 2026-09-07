package mods.eln.wiki

import mods.eln.i18n.I18N.tr
import mods.eln.misc.McRecipes
import mods.eln.misc.McRegistries
import mods.eln.misc.Recipe
import mods.eln.misc.RecipesList
import mods.eln.misc.Utils
import mods.eln.transparentnode.WireProductionRecipes
import mods.eln.transparentnode.WireMachineKind
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
        y = text(view, y, if (outputs.isNotEmpty()) tr("Recipe:") else if (WireProductionRecipes.outputs(stack).isNotEmpty())
            tr("Made in wire processing machines (see below).") else tr("No crafting-table recipe."))
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
        for ((label, steps) in listOf(tr("Created by:") to WireProductionRecipes.outputs(stack),
                tr("Wire processing uses:") to WireProductionRecipes.uses(stack))) {
            if (steps.isEmpty()) continue
            y = text(view, y + 6, label)
            for (step in steps) y = wireProcessing(view, y, step)
        }
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

    private fun wireProcessing(view: GuiVerticalExtender, start: Int, step: WireProductionRecipes.Step): Int {
        var y = text(view, start, tr("%1$: example output 32 m", step.kind.displayName))
        view.add(GuiItemStack(8, y, step.machine, view.helper))
        y += 24
        y = text(view, y, tr("Inputs:"))
        fun items(stacks: List<ItemStack>) {
            var x = 8
            for (item in stacks) {
                if (x + 18 > view.contentWidth() - 8) { x = 8; y += 22 }
                view.add(GuiItemStack(x, y, item, view.helper)); x += 22
            }
            y += 24
        }
        items(step.inputs)
        if (step.catalysts.isNotEmpty()) {
            y = text(view, y, tr("Reusable roller wheels (not consumed):")); items(step.catalysts)
        }
        y = text(view, y, tr("Output:")); items(listOf(step.output))
        y = text(view, y, tr("Power: %1$ W; example energy: %2$ J", Utils.plotValue(step.kind.nominalPowerWatts), Utils.plotValue(step.energyJoules)))
        y = text(view, y, when (step.kind) {
            WireMachineKind.ROLLER -> tr("Choose gauge and length. 1 ingot supplies 1 kg; this output uses %1$ kg. Unused metal stays buffered.", Utils.plotValue(step.metalKg))
            WireMachineKind.INSULATOR -> tr("1 rubber insulates 32 m. The whole input spool is coated; unused rubber stays buffered.")
            WireMachineKind.COMBINER -> tr("Use equal-gauge, same-metal insulated cores. Choose the bundle; the shortest input sets its length. Longer inputs keep the remainder. Insulate the bundle afterward.")
        })
        return y + 8
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
