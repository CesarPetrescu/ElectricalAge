package mods.eln.item

import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Utils
import mods.eln.wiki.Data
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntTag
import net.minecraft.resources.ResourceLocation

class BrushDescriptor(name: String): GenericItemUsingDamageDescriptor(name) {

    private val icon = ResourceLocation.fromNamespaceAndPath("eln", "textures/items/" + name.lowercase().replace(" ", "") + ".png")

    init {
        this.name = name
    }

    override fun getName(stack: ItemStack): String {
        val creative = Minecraft.getInstance().player.capabilities.isCreativeMode
        val color = getColor(stack)
        val life = getLife(stack)
        return if (!creative && color == 15 && life == 0) "Empty " + (name ?: "Brush") else (name ?: "Brush")
    }

    override fun setParent(item: Item, damage: Int) {
        super.setParent(item, damage)
        Data.addWiring(newItemStack())
    }

    fun getColor(stack: ItemStack) = stack.itemDamage and 0xF

    private fun getLife(stack: ItemStack?): Int {
        val nbt = stack?.tagCompound ?: return 32
        return if (nbt.contains("life")) nbt.getInt("life") else 32
    }

    fun setLife(stack: ItemStack, life: Int) {
        val nbt = stack.tagCompound ?: CompoundTag()
        nbt.putInt("life", life)
        stack.tagCompound = nbt
    }

    override fun getDefaultNBT(): CompoundTag? {
        val nbt = CompoundTag()
        nbt.putInt("life", 32)
        return nbt
    }

    override fun addInformation(itemStack: ItemStack?, entityPlayer: Player?, list: MutableList<Any?>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)

        if (itemStack != null) {
            val creative = Minecraft.getInstance().player.capabilities.isCreativeMode
            list.add(tr("Can paint %s blocks", if (creative) "infinite" else getLife(itemStack)))
        }
    }

    fun use(stack: ItemStack, entityPlayer: Player): Boolean {
        val creative = entityPlayer.capabilities.isCreativeMode
        if (creative) return true
        
        val nbt = stack.tagCompound ?: getDefaultNBT()!!
        var life = if (nbt.contains("life")) nbt.getInt("life") else 32
        
        return if (life != 0) {
            life--
            nbt.putInt("life", life)
            stack.tagCompound = nbt
            true
        } else {
            Utils.sendMessage(entityPlayer, tr("Brush is dry"))
            false
        }
    }

// TODO(1.10): Reimplement brush coloring
//    override fun renderItem(type: IItemRenderer.ItemRenderType, item: ItemStack, vararg data: Any) {
//        if (type == IItemRenderer.ItemRenderType.INVENTORY) {
//            val creative = Minecraft.getMinecraft().player.capabilities.isCreativeMode
//            UtilsClient.drawIcon(type, icon)
//            if (!creative) {
//                GL11.glColor4f(1f, 1f, 1f, 0.75f - 0.75f * getLife(item) / 32f)
//                UtilsClient.drawIcon(type, dryOverlay)
//                GL11.glColor3f(1f, 1f, 1f)
//            }
//        } else {
//            super.renderItem(type, item, *data)
//        }
//    }

    companion object {
        private val dryOverlay = ResourceLocation.fromNamespaceAndPath("eln", "textures/items/brushdryoverlay.png")
    }
}
