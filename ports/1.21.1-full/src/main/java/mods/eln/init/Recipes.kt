package mods.eln.init

import mods.eln.Eln
import mods.eln.misc.Recipe
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.Items
import net.minecraft.world.item.ItemStack
import java.util.ArrayList

object Recipes {
    @JvmField
    var furnaceList = ArrayList<ItemStack>()

    @JvmStatic
    fun init() {
        // Macerator recipes
        Descriptors.maceratorRecipes.addRecipe(Recipe(ItemStack(Blocks.COBBLESTONE), ItemStack(Blocks.GRAVEL), 1000.0))
        Descriptors.maceratorRecipes.addRecipe(Recipe(ItemStack(Blocks.GRAVEL), ItemStack(Blocks.SAND), 1000.0))
        Descriptors.maceratorRecipes.addRecipe(Recipe(ItemStack(Items.COAL), ItemStack(Eln.sharedItem, 1, 9), 1000.0)) // Assuming 9 is coal dust

        // Compressor recipes
        Descriptors.compressorRecipes.addRecipe(Recipe(ItemStack(Items.COAL, 8), ItemStack(Items.DIAMOND), 100000.0))
    }
}
