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
}
