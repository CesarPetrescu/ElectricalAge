package mods.eln.item

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TurbineBladeLookupTest {
    @Test fun registeredBladesResolveThroughFlattenedItems() {
        assertTrue(TurbineBladeLists.registeredBlades.isNotEmpty())
        for (blade in TurbineBladeLists.registeredBlades) {
            val stack = blade.newItemStack(1)
            assertSame(blade, TurbineBladeDescriptor.getDescriptor(stack))
            assertEquals(1.0, blade.getCondition(stack))
            blade.setCondition(stack, .5)
            assertSame(blade, TurbineBladeDescriptor.getDescriptor(stack.copy()))
            assertEquals(.5, blade.getCondition(stack.copy()))
        }
    }

    @Test fun otherItemsAreNotTurbineBlades() {
        assertNull(TurbineBladeDescriptor.getDescriptor(null))
        assertNull(TurbineBladeDescriptor.getDescriptor(ItemStack.EMPTY))
        assertNull(TurbineBladeDescriptor.getDescriptor(ItemStack(Items.IRON_INGOT)))
    }
}
