package mods.eln.generic

import mods.eln.Eln
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import kotlin.test.*

class CreativeCategoriesTest {
    @Test fun nineTabsAndGroundInWiring() {
        val tabs = listOf(Eln.creativeTabCables, Eln.creativeTabSignalProcessing, Eln.creativeTabPowerElectronics,
            Eln.creativeTabMechanics, Eln.creativeTabMachines, Eln.creativeTabLighting,
            Eln.creativeTabOresMaterials, Eln.creativeTabToolsArmor, Eln.creativeTabCreative)
        assertEquals(9, tabs.toSet().size)
        val ground = Eln.findItemStack("Ground Cable", 1)
        val all = mutableListOf<ItemStack>()
        for (tab in tabs) {
            val entries = mutableListOf<ItemStack>()
            CreativeTabPopulator.addEntries(tab, entries::add)
            assertTrue(entries.isNotEmpty(), "Empty tab: ${BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab)}")
            assertTrue(entries.none { GenericCreativeTab.isTabIcon(it) }, "Display metadata leaked into creative items")
            assertEquals(tab === Eln.creativeTabCables, entries.any { it.item === ground.item })
            for (stack in entries) assertFalse(all.any { ItemStack.isSameItemSameComponents(it, stack) }, "Duplicate item ${stack.item}")
            all.addAll(entries)
        }
        assertTrue(all.size > 300)
    }
    @Test fun mechanicalItemsInMechanics() {
        val entries = mutableListOf<ItemStack>()
        CreativeTabPopulator.addEntries(Eln.creativeTabMechanics, entries::add)
        for (name in listOf("Joint", "Flywheel", "Shaft Motor", "Generator")) {
            val item = Eln.findItemStack(name, 1).item
            assertTrue(entries.any { it.item === item }, "$name missing from Mechanics")
        }
    }
}
