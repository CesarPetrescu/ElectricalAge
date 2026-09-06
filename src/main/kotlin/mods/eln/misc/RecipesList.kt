@file:Suppress("NAME_SHADOWING")
package mods.eln.misc

import mods.eln.Eln
import mods.eln.transparentnode.electricalfurnace.ElectricalFurnaceProcess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeType
import java.util.*
import kotlin.collections.ArrayList

class RecipesList {
    val recipes = ArrayList<Recipe>()
    val machines = ArrayList<ItemStack>()
    fun addRecipe(recipe: Recipe) {
        recipes.add(recipe)
        recipe.setMachineList(machines)
    }

    fun addMachine(machine: ItemStack) {
        machines.add(machine)
    }

    fun getRecipe(input: ItemStack?): Recipe? {
        for (r in recipes) {
            if (r.canBeCraftedBy(input)) return r
        }
        return null
    }

    fun getRecipeFromOutput(output: ItemStack?): ArrayList<Recipe> {
        if (output == null) return ArrayList()
        val list = ArrayList<Recipe>()
        for (r in recipes) {
            for (stack in r.outputCopy) {
                if (!stack.isNothing()) {
                    if (Utils.areSame(stack, output)) {
                        list.add(r)
                        break
                    }
                }
            }
        }
        return list
    }

    companion object {
        val listOfList = ArrayList<RecipesList>()
        @JvmStatic
        fun getGlobalRecipeWithOutput(output: ItemStack): ArrayList<Recipe> {
            var output = output
            output = output.copy()
            output.count = 1
            val list = ArrayList<Recipe>()
            for (recipesList in listOfList) {
                list.addAll(recipesList.getRecipeFromOutput(output))
            }
            // 1.13+: smelting recipes are data, held by the recipe manager; each has one ingredient.
            val manager = McRecipes.manager() ?: return list
            val access = McRegistries.access()
            for (holder in manager.getAllRecipesFor(RecipeType.SMELTING)) {
                try {
                    val stack = holder.value().getResultItem(access)
                    val inputs = holder.value().ingredients.firstOrNull()?.items ?: continue
                    if (Utils.areSame(output, stack)) {
                        for (li in inputs) {
                            val recipe = Recipe(li.copy(), output, ElectricalFurnaceProcess.energyNeededPerSmelt)
                            recipe.setMachineList(Eln.instance.furnaceList)
                            list.add(recipe)
                        }
                    }
                } catch (e: Exception) {
                    // TODO: handle exception
                }
            }
            return list
        }

        @JvmStatic
        fun getGlobalRecipeWithInput(input: ItemStack): ArrayList<Recipe> {
            var input = input
            input = input.copy()
            input.count = 64
            val list = ArrayList<Recipe>()
            for (recipesList in listOfList) {
                val r = recipesList.getRecipe(input)
                if (r != null) list.add(r)
            }
            val manager = McRecipes.manager() ?: return list
            // Matched by ingredient (no level needed, so the wiki can ask on the client too).
            val smeltResult = manager.getAllRecipesFor(RecipeType.SMELTING)
                .firstOrNull { it.value().ingredients.firstOrNull()?.test(input) == true }
                ?.value()?.getResultItem(McRegistries.access()) ?: ItemStack.EMPTY
            var smeltRecipe: Recipe
            if (!smeltResult.isEmpty) {
                try {
                    val input1 = input.copy()
                    input1.count = 1
                    list.add(Recipe(input1, smeltResult, ElectricalFurnaceProcess.energyNeededPerSmelt).also { smeltRecipe = it })
                    smeltRecipe.machineList.addAll(Eln.instance.furnaceList)
                } catch (e: Exception) {
                    // TODO: handle exception
                }
            }
            return list
        }
    }

    init {
        listOfList.add(this)
    }
}
