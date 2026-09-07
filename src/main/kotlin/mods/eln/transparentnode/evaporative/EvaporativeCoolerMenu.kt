package mods.eln.transparentnode.evaporative

import mods.eln.GuiHandler
import mods.eln.node.NodeManager
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.inventory.SimpleContainerData
import net.minecraft.world.item.ItemStack
import kotlin.math.roundToInt

class EvaporativeCoolerMenu(player: Player, private val cooler: EvaporativeCoolerElement? = null) :
    AbstractContainerMenu(GuiHandler.MENU.get(), GuiHandler.pendingContainerId) {
    companion object { const val FIELD_COUNT = 19 }
    val values: ContainerData = if (cooler == null) SimpleContainerData(FIELD_COUNT) else object : ContainerData {
        override fun getCount() = FIELD_COUNT
        override fun get(index: Int): Int = when (index) {
            0 -> cooler.controls.mode()
            1 -> cooler.controls.targetCelsius()
            2 -> cooler.controls.fanPercent()
            3 -> cooler.controls.redstoneMode()
            4 -> cooler.status
            5 -> safe(cooler.surfaceCelsius * 10)
            6 -> safe(cooler.airCelsius * 10)
            7 -> safe(cooler.humidityPercent * 10)
            8 -> safe(cooler.wetBulbCelsius * 10)
            9 -> safe(cooler.water.availableMb)
            10 -> safe(cooler.electricalWatts)
            11 -> safe(cooler.netCoolingWatts)
            12 -> safe(cooler.waterMbPerSecond * 1000)
            13 -> safe(cooler.fanSpeed * 100)
            14 -> safe(cooler.supply.voltage * 10)
            15 -> safe(cooler.evaporationWatts)
            16 -> safe(cooler.sensibleWatts)
            17 -> if (cooler.outdoor) 1 else 0
            18 -> if (cooler.wetActive) 1 else 0
            else -> 0
        }
        override fun set(index: Int, value: Int) {}
    }
    init {
        addDataSlots(object : ContainerData {
            override fun getCount() = FIELD_COUNT * 2
            override fun get(index: Int) = (values.get(index / 2) ushr ((index % 2) * 16)) and 0xffff
            override fun set(index: Int, value: Int) {
                if (cooler != null) return
                val shift = (index % 2) * 16
                values.set(index / 2, (values.get(index / 2) and (0xffff shl shift).inv()) or ((value and 0xffff) shl shift))
            }
        })
    }
    private fun safe(v: Double) = if (v.isFinite()) v.coerceIn(-1e8, 1e8).roundToInt() else 0
    override fun stillValid(player: Player): Boolean {
        val e = cooler ?: return true
        val c = e.coordinate()
        return !player.isSpectator && e.node?.isDestructing == false && c.worldExist && e.world() === player.level() &&
            NodeManager.instance?.getNodeFromCoordonate(c) === e.node &&
            player.distanceToSqr(c.x + .5, c.y + .5, c.z + .5) <= 64.0
    }
    override fun clickMenuButton(player: Player, id: Int) = stillValid(player) && cooler?.command(id) == true
    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY
}
