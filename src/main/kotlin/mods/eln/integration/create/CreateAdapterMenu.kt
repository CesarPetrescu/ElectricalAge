package mods.eln.integration.create

import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.inventory.SimpleContainerData
import net.minecraft.world.item.ItemStack
import kotlin.math.roundToInt

class CreateAdapterMenu(id: Int, inventory: Inventory, private val adapter: CreateAdapterEntity? = null) : AbstractContainerMenu(CreateIntegration.menu.get(), id) {
    val values: ContainerData = if (adapter == null) SimpleContainerData(9) else object : ContainerData {
        override fun getCount() = 9
        override fun get(index: Int): Int = when (index) {
            0 -> adapter.ratio
            1 -> if (adapter.engaged) 1 else 0
            2 -> if (adapter.autoRetry) 1 else 0
            3 -> adapter.fault
            4 -> adapter.theoreticalSpeed.roundToInt()
            5 -> (adapter.outputSpeed * 10).roundToInt()
            6 -> adapter.deliveredPower.roundToInt()
            7 -> (adapter.requestedImpact * kotlin.math.abs(adapter.theoreticalSpeed)).roundToInt()
            8 -> if (adapter.industrial) 1 else 0
            else -> 0
        }
        override fun set(index: Int, value: Int) {}
    }
    // Vanilla menu data packets carry signed 16-bit words. Split values so server-configured
    // power ratings and negative input RPM survive synchronization without wrapping.
    init { addDataSlots(object : ContainerData {
        override fun getCount() = 18
        override fun get(index: Int) = (values.get(index / 2) ushr ((index % 2) * 16)) and 0xffff
        override fun set(index: Int, value: Int) {
            if (adapter != null) return
            val shift = (index % 2) * 16
            values.set(index / 2, (values.get(index / 2) and (0xffff shl shift).inv()) or ((value and 0xffff) shl shift))
        }
    }) }
    override fun stillValid(player: Player): Boolean = adapter == null ||
        (!adapter.isRemoved && adapter.level === player.level() && player.distanceToSqr(adapter.blockPos.center) <= 64.0)
    override fun clickMenuButton(player: Player, id: Int): Boolean = stillValid(player) && adapter?.command(id) == true
    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY
}
