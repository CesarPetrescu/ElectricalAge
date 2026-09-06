package mods.eln.item

import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Utils
import mods.eln.misc.UtilsClient
import mods.eln.wiki.Data
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.client.gl.GL11
import mods.eln.misc.isNothing

class BrushDescriptor(name: String): GenericItemUsingDamageDescriptor(name) {

    private val ricon = ResourceLocation("eln", "textures/items/" + name.lowercase().replace(" ", "") + ".png")

    /**
     * 1.12.2 indexes the creative search tree at startup, before a player exists, so every
     * tooltip/name path has to tolerate a null client player.
     */
    private fun isCreative() = Minecraft.getInstance().player?.capabilities?.isCreativeMode == true


    override fun getName(stack: ItemStack): String {
        val creative = isCreative()
        val color = getColor(stack)
        val life = getLife(stack)
        return if (!creative && color == 15 && life == 0) "Empty " + super.getName(stack) else super.getName(stack)?: ""
    }

    override fun setParent(item: Item?, damage: Int) {
        super.setParent(item, damage)
        Data.addWiring(newItemStack())
    }

    fun getColor(stack: ItemStack) = stack.itemDamage and 0xF

    private fun getLife(stack: ItemStack?) = if (stack.isNothing() || stack.tagCompound /* TODO(components) */ == null)
        32
    else
        stack.tagCompound /* TODO(components) */!!.getInt("life")

    fun setLife(stack: ItemStack, life: Int) {
        stack.tagCompound /* TODO(components) */!!.putInt("life", life)
    }

    override fun getDefaultNBT(): CompoundTag? {
        val nbt = CompoundTag()
        nbt.putInt("life", 32)
        return nbt
    }

    override fun addInformation(itemStack: ItemStack?, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)

        if (!itemStack.isNothing()) {
            list.add(tr("Can paint %1$ blocks", if (isCreative()) "infinite" else getLife(itemStack)))
        }
    }

    fun use(stack: ItemStack, entityPlayer: Player): Boolean {

        val creative = entityPlayer.isCreative()
        var life = stack.tagCompound /* TODO(components) */!!.getInt("life")
        return if (creative || life != 0) {
            if (!creative) {
                --life
                stack.tagCompound /* TODO(components) */!!.putInt("life", life)
            }
            true
        } else {
            Utils.sendMessage(entityPlayer, tr("Brush is dry"))
            false
        }
    }

    override fun handleRenderType(item: ItemStack?, type: IItemRenderer.ItemRenderType?) = type == IItemRenderer.ItemRenderType.INVENTORY

    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType?, item: ItemStack?, helper: IItemRenderer.ItemRendererHelper?) =
        type != IItemRenderer.ItemRenderType.INVENTORY

    override fun renderItem(type: IItemRenderer.ItemRenderType?, item: ItemStack?, vararg data: Any?) {
        if (type == IItemRenderer.ItemRenderType.INVENTORY) {
            val creative = isCreative()
            UtilsClient.drawIcon(type, ricon)
            if (!creative) {
                GL11.glColor4f(1f, 1f, 1f, 0.75f - 0.75f * getLife(item) / 32f)
                UtilsClient.drawIcon(type, dryOverlay)
                GL11.glColor3f(1f, 1f, 1f)
            }
        } else {
            super.renderItem(type, item, *data)
        }
    }

    companion object {
        private val dryOverlay = ResourceLocation("eln", "textures/items/brushdryoverlay.png")
    }
}
