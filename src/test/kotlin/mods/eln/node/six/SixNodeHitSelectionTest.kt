package mods.eln.node.six

import mods.eln.misc.Direction
import org.junit.Assert.*
import org.junit.Test

class SixNodeHitSelectionTest {
    @Test fun `a floor monitor can be selected from all six faces`() {
        for (hit in Direction.values()) assertEquals(Direction.YN, SixNodeHitSelection.bodySide(hit) { it == Direction.YN })
    }
    @Test fun `a body on any mounting face owns its whole collision cube`() {
        for (mount in Direction.values()) for (hit in Direction.values()) {
            assertEquals(mount, SixNodeHitSelection.bodySide(hit) { it == mount })
        }
    }
    @Test fun `surface parts keep their slab selection path`() {
        for (hit in Direction.values()) assertNull(SixNodeHitSelection.bodySide(hit) { false })
    }
    @Test fun `a body does not steal hits from another populated face`() {
        for (mount in Direction.values()) for (surface in Direction.values().filter { it != mount }) {
            for (hit in Direction.values()) {
                assertEquals(if (hit == surface) surface else mount,
                    SixNodeHitSelection.bodySide(hit, { it == mount }, { it == mount || it == surface }))
            }
        }
    }
    @Test fun `populated faces without a body still use slab selection`() {
        for (hit in Direction.values()) assertNull(SixNodeHitSelection.bodySide(hit, { false }, { true }))
    }
}
