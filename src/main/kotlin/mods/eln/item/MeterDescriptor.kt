package mods.eln.item

import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.i18n.I18N.tr
import mods.eln.misc.tagCompound
import mods.eln.misc.Utils
import mods.eln.node.CircuitDiagnostics
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class MeterDescriptor(name: String) : GenericItemUsingDamageDescriptor(name) {
    override fun consumesRightClick(s: ItemStack, w: Level, p: Player) = p.isShiftKeyDown

    // Sneaking bypasses vanilla block interaction; route the item's fallback through the same
    // block hit resolver so surface-mounted six-node terminals still use the selected element.
    override fun onItemUse(stack: ItemStack?, player: Player?, world: Level?, x: Int, y: Int, z: Int,
                           side: Int, vx: Float, vy: Float, vz: Float): Boolean {
        if (player == null || world == null || !player.isShiftKeyDown) return false
        val pos = net.minecraft.core.BlockPos(x, y, z)
        val state = world.getBlockState(pos)
        val block = state.block as? mods.eln.node.NodeBlock ?: return false
        return block.onBlockActivated(world, pos, state, player, net.minecraft.world.InteractionHand.MAIN_HAND,
            net.minecraft.core.Direction.from3DDataValue(side), vx, vy, vz)
    }
    override fun addInformation(itemStack: ItemStack?, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        list.add(tr("Right-click a device for its normal readings."))
        list.add(tr("Sneak-click terminal A, then terminal B, to measure V(B) - V(A)."))
        list.add(tr("Aim near the cable connector. Sneak-use in air clears the probes."))
        if (itemStack?.tagCompound?.contains(CircuitDiagnostics.PROBE_KEY) == true)
            list.add(tr("Reference probe A is selected."))
    }

    override fun onItemRightClick(s: ItemStack, w: Level, p: Player): ItemStack {
        if (!w.isClientSide && p.isShiftKeyDown) {
            CircuitDiagnostics.clear(s)
            Utils.sendMessage(p, tr("Meter probes cleared."))
        }
        return s
    }
}
