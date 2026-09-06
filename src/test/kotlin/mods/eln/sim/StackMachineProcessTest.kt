package mods.eln.sim

import mods.eln.bootstrapMinecraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import mods.eln.misc.Recipe
import mods.eln.misc.RecipesList
import net.minecraft.world.Container
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/** Minimal Container: slots are never null, an empty slot holds ItemStack.EMPTY. */
private class SimpleInventory(size: Int) : Container {
    private val stacks = Array(size) { ItemStack.EMPTY }
    override fun getContainerSize(): Int = stacks.size
    override fun isEmpty(): Boolean = stacks.all { it.isEmpty }
    override fun getItem(slot: Int): ItemStack = stacks[slot]
    override fun removeItem(slot: Int, amount: Int): ItemStack {
        val stack = stacks[slot]
        if (stack.isEmpty) return ItemStack.EMPTY
        val removed = stack.copy()
        removed.count = amount.coerceAtMost(stack.count)
        stack.count -= removed.count
        if (stack.count <= 0) stacks[slot] = ItemStack.EMPTY
        return removed
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack {
        val stack = stacks[slot]
        stacks[slot] = ItemStack.EMPTY
        return stack
    }

    override fun setItem(slot: Int, stack: ItemStack) {
        stacks[slot] = stack
    }

    override fun getMaxStackSize(): Int = 64
    override fun setChanged() {}
    override fun stillValid(player: net.minecraft.world.entity.player.Player): Boolean = true
    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = true
    override fun clearContent() = stacks.fill(ItemStack.EMPTY)
}

class StackMachineProcessTest {
    init {
        bootstrapMinecraft()
    }

    @Test
    fun processSmeltsWhenEnergyAvailable() {
        val inventory = SimpleInventory(3)
        // Registries are frozen after Bootstrap: use vanilla items rather than constructing new ones.
        val inputItem = net.minecraft.world.item.Items.STONE
        val outputItem = net.minecraft.world.item.Items.DIRT
        val input = ItemStack(inputItem, 1)
        inventory.setItem(0, input)

        val recipes = RecipesList()
        val output = ItemStack(outputItem, 1)
        recipes.addRecipe(Recipe(input.copy(), output.copy(), 5.0))

        val process = StackMachineProcess(
            inventory,
            inputSlotId = 0,
            outputSlotId = 1,
            outputSlotNbr = 2,
            recipesList = recipes,
            energyProvidedFunction = { 10.0 },
            energyConsumerFunction = {}
        )

        process.process(1.0)

        assertTrue(inventory.getItem(0).isEmpty)
        assertEquals(1, inventory.getItem(1).count)
    }

    @Test
    fun getProcessStateReflectsProgress() {
        val inventory = SimpleInventory(2)
        // Registries are frozen after Bootstrap: use vanilla items rather than constructing new ones.
        val inputItem = net.minecraft.world.item.Items.STONE
        val outputItem = net.minecraft.world.item.Items.DIRT
        val input = ItemStack(inputItem, 1)
        inventory.setItem(0, input)

        val recipes = RecipesList()
        val output = ItemStack(outputItem, 1)
        recipes.addRecipe(Recipe(input.copy(), output.copy(), 10.0))

        val process = StackMachineProcess(
            inventory,
            inputSlotId = 0,
            outputSlotId = 1,
            outputSlotNbr = 1,
            recipesList = recipes,
            energyProvidedFunction = { 2.0 },
            energyConsumerFunction = {}
        )

        process.process(1.0)

        val state = process.getProcessState()
        assertTrue(state > 0.0 && state < 1.0)
    }
}
