#!/usr/bin/env python3
"""Supply the language service in a standalone unit test; no production changes."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
p=root/'src/test/java/mods/eln/generic/CallbackRegressionTest.java'
s=p.read_text()
old='''    @Test public void brushTooltipsDoNotRequireMinecraftOrAPlayer() {
        mods.eln.item.BrushDescriptor descriptor = new mods.eln.item.BrushDescriptor("White Brush");
        ItemStack stack = new ItemStack(net.minecraft.init.Items.STICK, 1, 15);
        java.util.List<Object> lines = new java.util.ArrayList<>();
        assertDoesNotThrow(() -> descriptor.addInformation(stack, null, lines, false));
        assertFalse(lines.isEmpty());
        descriptor.setLife(stack, 0);
        assertEquals("Empty White Brush", descriptor.getName(stack));
    }
'''
new='''    @Test public void brushTooltipsAllowNullPlayersWithLanguageServiceReady() throws Exception {
        // A real client initializes its language service before building the search tree.
        // Standalone JUnit has no client bootstrap; set up just that service, not a player.
        java.lang.reflect.Field locale = java.util.Arrays.stream(net.minecraft.client.resources.I18n.class.getDeclaredFields())
                .filter(field -> field.getType() == net.minecraft.client.resources.Locale.class)
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing Minecraft locale field"));
        locale.setAccessible(true);
        Object previous = locale.get(null);
        try {
            if (previous == null) locale.set(null, new net.minecraft.client.resources.Locale());
            mods.eln.item.BrushDescriptor descriptor = new mods.eln.item.BrushDescriptor("White Brush");
            ItemStack stack = new ItemStack(net.minecraft.init.Items.STICK, 1, 15);
            java.util.List<Object> lines = new java.util.ArrayList<>();
            assertDoesNotThrow(() -> descriptor.addInformation(stack, null, lines, false));
            assertFalse(lines.isEmpty());
            descriptor.setLife(stack, 0);
            assertEquals("Empty White Brush", descriptor.getName(stack));
        } finally {
            locale.set(null, previous);
        }
    }
'''
assert s.count(old)==1
p.write_text(s.replace(old,new))
