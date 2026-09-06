package mods.eln.sixnode.electricalsource;

import mods.eln.TestLogConfigKt;
import mods.eln.misc.Direction;
import mods.eln.node.six.SixNode;
import net.minecraft.nbt.CompoundTag;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ElectricalSourceElementTest {
    @Test
    public void configuredVoltageDrivesRedHotWithInverseVoltage() {
        TestLogConfigKt.bootstrapMinecraft();
        ElectricalSourceElement source = new ElectricalSourceElement(
            new SixNode(),
            Direction.ZP,
            new ElectricalSourceDescriptor("Test Voltage Source", null, false)
        );
        CompoundTag nbt = new CompoundTag();
        nbt.putDouble("voltage", 240.0);

        source.readFromNBT(nbt);

        assertEquals(240.0, source.voltageSource.getVoltage(), 0.0);
        assertEquals(-240.0, source.voltageSourceRed.getVoltage(), 0.0);
        assertEquals(0.0, source.voltageSourceWhite.getVoltage(), 0.0);
        assertEquals(0.0, source.voltageSourceGround.getVoltage(), 0.0);
    }
}
