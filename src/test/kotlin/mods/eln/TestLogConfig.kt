package mods.eln

import net.minecraft.server.Bootstrap

internal fun disableLog4jJmx() {
    System.setProperty("log4j2.disable.jmx", "true")
}

/**
 * 1.12.2 guards Blocks/Items/SoundEvent behind Bootstrap: the first touch of any of them throws
 * "Accessed Blocks before Bootstrap!" instead of the null 1.7.10 handed out. Tests that build an
 * ItemStack, a SixNode (which caches Blocks.AIR) or an Eln instance call this first. Idempotent.
 */
fun bootstrapMinecraft() {
    disableLog4jJmx()
    if (!Bootstrap.isRegistered()) {
        Bootstrap.register()
    }
}
