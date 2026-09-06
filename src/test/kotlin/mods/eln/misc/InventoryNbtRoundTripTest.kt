package mods.eln.misc

import mods.eln.bootstrapMinecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Every element inventory is saved through [Utils.writeToNBT]. 1.20.5's `ItemStack.save(provider,
 * prefix)` returns a copy of the prefix instead of writing into it, which the 1.7.10-shaped bridge
 * once missed: the saved slots carried only their index and every machine came back empty.
 */
class InventoryNbtRoundTripTest {
    @Before
    fun setUp() = bootstrapMinecraft()

    @Test
    fun slotsSurviveTheRoundTrip() {
        val inventory = SimpleContainer(4)
        inventory.setItem(1, ItemStack(Items.STONE, 5))
        inventory.setItem(3, ItemStack(Items.DIAMOND, 1))

        val nbt = CompoundTag()
        Utils.writeToNBT(nbt, "inv", inventory)
        val slots = nbt.getList("inv", 10)
        assertEquals("only the occupied slots are written", 2, slots.size)
        assertTrue("a slot tag carries the item, not just its index", slots.getCompound(0).contains("id"))

        val loaded = SimpleContainer(4)
        Utils.readFromNBT(nbt, "inv", loaded)
        assertTrue(loaded.getItem(0).isEmpty)
        assertEquals(Items.STONE, loaded.getItem(1).item)
        assertEquals(5, loaded.getItem(1).count)
        assertTrue(loaded.getItem(2).isEmpty)
        assertEquals(Items.DIAMOND, loaded.getItem(3).item)
    }

    @Test
    fun emptyStackWritesNothing() {
        val tag = CompoundTag()
        ItemStack.EMPTY.writeToNBT(tag)
        assertTrue(tag.isEmpty)
    }
}
