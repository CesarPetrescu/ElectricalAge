package mods.eln.item.electricalitem

import mods.eln.i18n.I18N.tr
import mods.eln.item.electricalitem.TreeCapitation.removeBlockWithDrops
import mods.eln.misc.Utils
import mods.eln.wiki.Data
import net.minecraft.world.level.block.Block
import net.minecraft.block.material.Material
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

class ElectricalPickaxe(name: String, strengthOn: Float, strengthOff: Float,
                        energyStorage: Double, energyPerBlock: Double, chargePower: Double) : ElectricalTool(name, strengthOn, strengthOff, energyStorage, energyPerBlock, chargePower) {

    override fun addInformation(itemStack: ItemStack?, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)
        list.add(tr("Opens holes. Right-click to open smaller holes."))
    }

    override fun setParent(item: Item?, damage: Int) {
        super.setParent(item, damage)
        Data.addPortable(newItemStack())
    }

    override fun getDestroySpeed(stack: ItemStack, state: BlockState): Float {
        val material = state.material
        var value = when {
            material === Material.IRON || material === Material.GLASS || material === Material.ANVIL || material === Material.ROCK -> getStrength(stack)
            else -> super.getDestroySpeed(stack, state)
        }
        if (blocksEffectiveAgainst.any { it == state.block }) {
            value = getStrength(stack)
        }
        return value
    }

    override fun onItemRightClick(s: ItemStack, w: Level, p: Player): ItemStack {
        if (!w.isClientSide) {
            setConservative(p, s, !getConservative(s))
        }
        return s
    }

    private fun getConservative(s: ItemStack) =
        getNbt(s).getBoolean("conservative")

    private fun setConservative(p: Player?, s: ItemStack, state: Boolean) {
        updateNbt(s) { it.putBoolean("conservative", state) }
        if (p != null) {
            Utils.sendMessage(p, "Set land conservation to $state")
        }
    }

    override fun onBlockDestroyed(stack: ItemStack, w: Level, state: BlockState, pos: BlockPos, entity: LivingEntity): Boolean {
        val ok = super.onBlockDestroyed(stack, w, state, pos, entity)
        if (entity !is Player) return ok
        if (!ok) return ok
        if (!getConservative(stack)) {
            for (a in (-1..1)) {
                for (b in (-1..0)) {
                    for (c in (-1..1)) {
                        if (a == 0 && b == 0 && c == 0) continue
                        removeBlockWithDrops(entity, this, stack, w, pos.add(a, b, c))
                    }
                }
            }
        }
        return ok
    }
}
