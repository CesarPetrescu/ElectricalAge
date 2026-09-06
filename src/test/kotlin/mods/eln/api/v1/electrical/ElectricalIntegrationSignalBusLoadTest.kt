package mods.eln.api.v1.electrical

import net.minecraft.nbt.CompoundTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ElectricalIntegrationSignalBusLoadTest {
    @Test
    fun channelAccessMatchesOperatorAndGetter() {
        val bus = ElectricalIntegration.SignalBusLoad("test.bus") {}
        assertEquals(
            bus.getChannel(ElectricalIntegration.SignalBusChannel.BLUE).name,
            bus[ElectricalIntegration.SignalBusChannel.BLUE].name
        )
    }

    @Test
    fun writeReadNbtUsesStablePerChannelPrefixes() {
        val bus = ElectricalIntegration.SignalBusLoad("test.bus.persist") {}
        val tag = CompoundTag()

        bus.writeToNbt(tag, "bus")

        assertTrue(tag.contains("bus.redtest.bus.persist.redUc"))
        assertTrue(tag.contains("bus.bluetest.bus.persist.blueUc"))
    }
}
