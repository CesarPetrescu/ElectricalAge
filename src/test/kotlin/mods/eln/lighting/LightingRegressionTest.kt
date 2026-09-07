package mods.eln.lighting

import mods.eln.Eln
import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.item.lampitem.LampDescriptor
import mods.eln.item.lampitem.LampItemSlot
import mods.eln.misc.Direction
import mods.eln.misc.HybridNodeDirection
import mods.eln.misc.LRDU
import mods.eln.node.AutoAcceptInventoryProxy
import mods.eln.sixnode.lampsocket.LampConnections
import mods.eln.sixnode.lampsocket.LampSocketDescriptor
import mods.eln.transparentnode.floodlight.FloodlightDescriptor
import mods.eln.transparentnode.floodlight.FloodlightOptics
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.abs
import kotlin.math.cos
import kotlin.test.*

class LightingRegressionTest {
    @Test fun emittedLightIsNotDiscardedAsAirByEmptyChunkSections() {
        val state = Eln.lightBlock.defaultBlockState()
        assertFalse(state.isAir)
        assertTrue(state.canBeReplaced())
        assertEquals(net.minecraft.world.level.block.RenderShape.INVISIBLE, state.renderShape)
    }

    @Test fun rayCoordinatesUseMinecraftFlooringOnNegativeAxes() {
        val coordinate = mods.eln.misc.Coordinate(0, 0, 0, 0)
        coordinate.setPosition(doubleArrayOf(-0.1, -1.1, -16.1))
        assertEquals(-1, coordinate.x)
        assertEquals(-2, coordinate.y)
        assertEquals(-17, coordinate.z)
        coordinate.setPosition(doubleArrayOf(0.1, 1.1, 16.1))
        assertEquals(0, coordinate.x)
        assertEquals(1, coordinate.y)
        assertEquals(16, coordinate.z)
    }
    @Test fun everyLampSocketAndFloodlightAcceptsAllHalogenVoltages() {
        val fixtures = Eln.sixNodeItem.subItemList.values.filterIsInstance<LampSocketDescriptor>().map { it.acceptedLampTypes } +
            Eln.transparentNodeItem.subItemList.values.filterIsInstance<FloodlightDescriptor>().map { it.acceptedLampTypes }
        assertTrue(fixtures.size >= 10)
        for (types in fixtures) {
            val slot = LampItemSlot(SimpleContainer(1), 0, 0, 0, 1, types)
            for (voltage in listOf(12, 120, 240)) {
                assertTrue(slot.mayPlace(Eln.findItemStack("${voltage}V Halogen Light Bulb", 1)))
            }
            assertFalse(slot.mayPlace(ItemStack.EMPTY))
            assertFalse(slot.mayPlace(ItemStack(Items.STICK)))
        }
    }

    @Test fun directInsertionPreservesBulbLifeAndDoesNotDuplicateItems() {
        val inventory = SimpleContainer(1)
        val proxy = AutoAcceptInventoryProxy(inventory).acceptIfEmpty(0, LampDescriptor::class.java)
        val bulb = Eln.findItemStack("120V Halogen Light Bulb", 2)
        val descriptor = GenericItemUsingDamageDescriptor.getDescriptor(bulb) as LampDescriptor
        descriptor.setLifeInTag(bulb, 3.25)
        assertTrue(proxy.take(bulb))
        assertEquals(1, bulb.count)
        assertEquals(1, inventory.getItem(0).count)
        assertEquals(3.25, descriptor.getLifeInTag(inventory.getItem(0)))
        assertFalse(proxy.take(bulb))
        assertEquals(1, bulb.count)
    }

    @Test fun beamIsFiniteAndWithinItsConeForAllMountingsIncludingVerticalAim() {
        for (axis in HybridNodeDirection.entries) for (facing in HybridNodeDirection.entries) {
            if (facing == axis || facing == axis.inverse) continue
            for (yaw in listOf(0.0, 90.0, 180.0, 270.0, 360.0)) for (pitch in listOf(0.0, 89.9, 90.0, 90.1, 180.0)) {
                val centre = FloodlightOptics.direction(yaw, pitch, axis, facing)
                for (width in listOf(0.0, 1.0, 20.0, 45.0)) {
                    val rays = FloodlightOptics.rays(yaw, pitch, width, axis, facing)
                    if (width == 0.0) assertEquals(1, rays.size) else assertTrue(rays.size > 1)
                    for ((ray, lengthFactor) in rays) {
                        assertTrue(ray.x.isFinite() && ray.y.isFinite() && ray.z.isFinite())
                        assertEquals(1.0, ray.length(), 1e-9)
                        assertTrue(ray.dot(centre) >= cos(Math.toRadians(width / 2)) - 1e-9)
                        assertEquals(1.0, lengthFactor * ray.dot(centre), 1e-9)
                    }
                }
            }
        }
    }

    @Test fun cardinalAimingAndPortsFollowTheMountingPlane() {
        for (axis in HybridNodeDirection.entries) {
            var count = 0
            for (side in Direction.entries) for (lrdu in LRDU.entries) {
                if (FloodlightOptics.isMountingPlanePort(axis, side, lrdu)) {
                    count++
                    assertEquals(axis.inverse.toStandardDirection(), side.applyLRDU(lrdu))
                    assertNotEquals(axis.toStandardDirection(), side)
                }
            }
            assertEquals(4, count)
            for (facing in HybridNodeDirection.entries) {
                if (facing == axis || facing == axis.inverse) continue
                val forward = FloodlightOptics.direction(0.0, 0.0, axis, facing)
                val back = FloodlightOptics.direction(0.0, 180.0, axis, facing)
                val up = FloodlightOptics.direction(0.0, 90.0, axis, facing)
                assertEquals(-1.0, forward.dot(back), 1e-9)
                assertTrue(abs(up.dot(forward)) < 1e-9)
            }
        }
    }

    @Test fun cableStopsAtHousingEdgeAndFollowsRotation() {
        val edges = LampConnections.edges(-.2f, .2f, -.35f, .35f, 0.0)
        assertContentEquals(floatArrayOf(.35f,.35f,.2f,.2f), edges)
        assertContentEquals(floatArrayOf(.2f,.2f,.35f,.35f), LampConnections.edges(-.2f,.2f,-.35f,.35f,90.0))
        for (edge in edges) assertTrue(edge + LampConnections.GLAND_LENGTH > edge)
    }
}
