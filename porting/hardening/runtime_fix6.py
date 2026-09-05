#!/usr/bin/env python3
"""Apply the sixth audit pass to the pinned, five-pass Re-Wired candidate."""
from pathlib import Path
import re
import sys
h = Path(sys.argv[1]).resolve()
b = h / 'src/main/java/mods/eln'
p = b / 'CommonProxy.java'
s = p.read_text()
assert 'handleClientNodePacket' not in s, 'Runtime pass already applied'
ix = s.rfind('}')
s = s[:ix] + '''    /** Receives a server-to-client node update without linking client classes on a server. */
    public void handleClientNodePacket(byte[] payload, net.minecraft.network.NetworkManager network) {
        // Dedicated servers never consume client-bound tile-entity updates.
    }
''' + s[ix:]
p.write_text(s)
p = b / 'client/ClientProxy.java'
s = p.read_text()
ix = s.rfind('}')
s = s[:ix] + '''    @Override
    public void handleClientNodePacket(byte[] payload, net.minecraft.network.NetworkManager network) {
        if (payload.length == 0) return;
        final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getMinecraft();
        // NBT-backed arrays can be replaced after the callback; own the scheduled payload.
        final byte[] copy = payload.clone();
        minecraft.addScheduledTask(() -> {
            if (minecraft.player == null || minecraft.world == null) return;
            java.io.DataInputStream stream = new java.io.DataInputStream(new java.io.ByteArrayInputStream(copy));
            Eln.packetHandler.packetRx(stream, network, minecraft.player);
        });
    }
''' + s[ix:]
p.write_text(s)
p = b / 'node/NodeBlockEntity.java'
s = p.read_text()
start = s.index('    @Override\n    public void handleUpdateTag(')
end = s.index('    public void preparePacketForServer(', start)
s = s[:start] + '''    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        if (world != null && world.isRemote && tag.hasKey("eln", 7)) {
            Eln.proxy.handleClientNodePacket(tag.getByteArray("eln"), null);
        }
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        if (world == null || !world.isRemote) return;
        NBTTagCompound tag = pkt.getNbtCompound();
        if (tag.hasKey("eln", 7)) {
            Eln.proxy.handleClientNodePacket(tag.getByteArray("eln"), net);
        }
    }

''' + s[end:]
s = s.replace('    public GuiScreen newGuiDraw(', '    @SideOnly(Side.CLIENT)\n    public GuiScreen newGuiDraw(')
p.write_text(s)
for name in ['node/six/SixNodeEntity.java', 'node/transparent/TransparentNodeEntity.java']:
    p = b / name
    s = p.read_text().replace('    public GuiScreen newGuiDraw(', '    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)\n    public GuiScreen newGuiDraw(')
    p.write_text(s)
# The vanilla search tree requests tooltips before a player or a world exists.
changed = []
for p in b.rglob('*.kt'):
    s = p.read_text()
    updated = re.sub(r'(override\s+fun\s+addInformation\([^)]*?\b(?:entityPlayer|player):\s*EntityPlayer)(?!\?)(?=[,\s])', r'\1?', s, flags=re.S)
    if updated != s:
        p.write_text(updated)
        changed.append(str(p.relative_to(h)))
assert len(changed) == 7, changed
print('Nullable tooltip overrides:', changed)
p = h / 'src/validation/java/eln/validation/ClientProbe.java'
s = p.read_text().replace('        tested++;', '''        try {
            List<String> tooltip = stack.getTooltip(null, ITooltipFlag.TooltipFlags.NORMAL);
            ValidationMod.require(tooltip != null && !tooltip.isEmpty(), "empty tooltip " + stack.getItem().getRegistryName());
        } catch (RuntimeException error) {
            throw new IllegalStateException("ELN_VALIDATION FAIL: tooltip " + stack.getItem().getRegistryName() + ":" + stack.getItemDamage(), error);
        }
        tested++;''')
p.write_text(s)
p = h / 'src/test/java/mods/eln/generic/CallbackRegressionTest.java'
s = p.read_text()
ix = s.rfind('}')
s = s[:ix] + '''    @Test public void commonProxyIgnoresClientNodeUpdates() {
        assertDoesNotThrow(() -> new mods.eln.CommonProxy().handleClientNodePacket(new byte[]{1,2}, null));
    }
    @Test public void detachedTileEntityIgnoresClientUpdatePackets() {
        assertDoesNotThrow(() -> new SixNodeEntity().onDataPacket(null,
                new net.minecraft.network.play.server.SPacketUpdateTileEntity(BlockPos.ORIGIN, 0, new net.minecraft.nbt.NBTTagCompound())));
    }
''' + s[ix:]
p.write_text(s)
