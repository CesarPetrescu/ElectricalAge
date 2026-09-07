package mods.eln.simplenode.computerprobe

import mods.eln.misc.Direction
import net.minecraft.nbt.CompoundTag
import kotlin.test.Test
import kotlin.test.assertEquals

class ComputerProbePersistenceTest {
    @Test fun sixSideSettingsRestoreBeforeConnectingToTheWorld() {
        val original = ComputerProbeNode().apply { front = Direction.XN }
        Direction.values().forEachIndexed { index, side ->
            original.signalSetDir(side, index % 2 == 0)
            original.signalSetOut(side, index / 5.0)
        }
        val saved = CompoundTag().also { original.writeToNBT(it) }
        val restored = ComputerProbeNode()
        restored.readFromNBT(saved)
        for (side in Direction.values()) {
            assertEquals(original.signalGetDir(side), restored.signalGetDir(side), side.name)
            assertEquals(original.signalGetOut(side), restored.signalGetOut(side), side.name)
        }
    }
}
