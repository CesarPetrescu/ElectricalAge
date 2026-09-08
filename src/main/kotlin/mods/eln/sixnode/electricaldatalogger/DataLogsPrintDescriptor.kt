package mods.eln.sixnode.electricaldatalogger

import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.i18n.I18N.tr
import mods.eln.misc.tagCompound
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/** Saved chart, not a live link to the monitor. The registered item/legacy ID are unchanged. */
class DataLogsPrintDescriptor(name: String) : GenericItemUsingDamageDescriptor(name) {
    override fun consumesRightClick(s: ItemStack, w: Level, p: Player) = true
    fun initializeStack(stack: ItemStack, logs: DataLogs) {
        val tag = CompoundTag()
        logs.writeToNBT(tag, "")
        stack.tagCompound = tag
    }

    override fun onItemRightClick(s: ItemStack, w: Level, p: Player): ItemStack {
        if (w.isClientSide) PrintedLogScreen.open(s)
        return s
    }

    override fun onItemUse(stack: ItemStack?, player: Player?, world: Level?, x: Int, y: Int, z: Int,
                           side: Int, vx: Float, vy: Float, vz: Float): Boolean {
        if (stack == null || world == null) return false
        if (world.isClientSide) PrintedLogScreen.open(stack)
        return true
    }

    override fun addInformation(itemStack: ItemStack?, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        list.add(tr("Right-click to read the printed chart."))
        val chart = PrintedLogData(itemStack?.tagCompound)
        list.add(tr("Recorded samples: %1$", chart.size))
        list.add(tr("Printed values are a snapshot; they do not update."))
    }
}
