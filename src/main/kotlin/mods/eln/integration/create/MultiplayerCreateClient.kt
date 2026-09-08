package mods.eln.integration.create

import mods.eln.devtest.MultiplayerScene
import net.minecraft.client.Minecraft
import kotlin.math.abs
import kotlin.math.PI

object MultiplayerCreateClient {
    fun menuReady() = Minecraft.getInstance().player?.containerMenu is CreateAdapterMenu
    fun matches(ratio: Int, engaged: Boolean): Boolean {
        val mc = Minecraft.getInstance()
        val a = mc.level?.getBlockEntity(MultiplayerScene.adapter) as? CreateAdapterEntity ?: return false
        if (a.ratio != ratio || a.engaged != engaged || a.fault != 0) return false
        if (engaged && abs(a.outputSpeed - 64.0 * PI / 30 * ratio) >= 1) return false
        val menu = mc.player?.containerMenu as? CreateAdapterMenu
        return menu == null || (menu.values.get(0) == ratio && menu.values.get(1) == if (engaged) 1 else 0)
    }
}
