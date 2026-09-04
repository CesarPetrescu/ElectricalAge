package mods.eln.generic

import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import mods.eln.misc.RealisticEnum
import mods.eln.misc.UtilsClient
import mods.eln.misc.VoltageLevelColor
import net.minecraft.block.Block
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ResourceLocation
import net.minecraft.block.state.IBlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import mods.eln.client.itemrender.IItemRenderer.ItemRenderType
import mods.eln.client.itemrender.IItemRenderer.ItemRendererHelper
import mods.eln.misc.isNothing

open class GenericItemUsingDamageDescriptor {

    @JvmOverloads
    constructor(name: String, iconName: String = name) {
        setDefaultIcon(iconName)
        this.name = name
        byName[name] = this
    }

    var IconName: String? = null
    @JvmField
    var name: String
    @JvmField
    var voltageLevelColor = VoltageLevelColor.None
    @JvmField
    var parentItem: Item? = null
    @JvmField
    var parentItemDamage = 0
    open var creativeTab: CreativeTabs? = null
    var hidden: Boolean = false
    fun setDefaultIcon(name: String) {
        IconName = "eln:" + name.replace(" ".toRegex(), "").lowercase()
    }

    open fun getDefaultNBT(): NBTTagCompound? = null

    open fun addInformation(itemStack: ItemStack?, entityPlayer: EntityPlayer?, list: MutableList<String>, par4: Boolean) {}

    open fun addRealismContext(list: List<*>?): RealisticEnum? = null

    open fun onItemRightClick(s: ItemStack, w: World, p: EntityPlayer): ItemStack {
        return s
    }

    fun getSubItems(list: MutableList<ItemStack>) = list.add(newItemStack(1))


    open fun getName(stack: ItemStack): String? {
        return name
    }

    open fun setParent(item: Item?, damage: Int) {
        parentItem = item
        parentItemDamage = damage
    }


    open fun newItemStack(size: Int): ItemStack {
        val stack = ItemStack(parentItem, size, parentItemDamage)
        stack.tagCompound = getDefaultNBT()
        return stack
    }

    fun newItemStack(): ItemStack {
        return newItemStack(1)
    }

    open fun hideFromCreative(): GenericItemUsingDamageDescriptor {
        hidden = true
        return this
    }

    fun isHidden(): Boolean = hidden

    fun checkSameItemStack(stack: ItemStack?): Boolean {
        if (stack.isNothing()) return false
        return !(stack.item !== this.parentItem || stack.itemDamage != parentItemDamage)
    }

    /**
     * Callback for item usage. If the item does something special on right clicking, he will have one of those. Return
     * True if something happen and false if it don't. This is for ITEMS, not BLOCKS
     */
    open fun onItemUse(stack: ItemStack?, player: EntityPlayer?, world: World?, x: Int, y: Int, z: Int, side: Int, vx: Float, vy: Float, vz: Float): Boolean {
        return false
    }

    open fun handleRenderType(item: ItemStack?, type: ItemRenderType?): Boolean {
        return voltageLevelColor != VoltageLevelColor.None
    }

    open fun shouldUseRenderHelper(type: ItemRenderType?, item: ItemStack?, helper: ItemRendererHelper?): Boolean {
        return false
    }

    open fun renderItem(type: ItemRenderType?, item: ItemStack?, vararg data: Any?) {
        // 1.12.2 has no IIcon/texture atlas for items; the sprite is addressed by path.
        // IconName is already "eln:<name>", the same string the old atlas was keyed by.
        val iconName = IconName ?: return
        voltageLevelColor.drawIconBackground(type!!)
        UtilsClient.drawIcon(type, ResourceLocation("eln", "textures/items/${iconName.removePrefix("eln:")}.png"))
    }

    open fun onUpdate(stack: ItemStack, world: World, entity: Entity, par4: Int, par5: Boolean) {}
    protected fun getNbt(stack: ItemStack): NBTTagCompound {
        var nbt = stack.tagCompound
        if (nbt == null) {
            stack.tagCompound = getDefaultNBT().also { nbt = it }
        }
        return nbt!!
    }

    /** 1.8's Item.getDestroySpeed: the block is identified by its state now, not the Block. */
    open fun getDestroySpeed(stack: ItemStack, state: IBlockState): Float {
        return 0.2f
    }

    open fun onBlockDestroyed(stack: ItemStack, w: World, state: IBlockState, pos: BlockPos, entity: EntityLivingBase): Boolean {
        return false
    }

    open fun onDroppedByPlayer(item: ItemStack, player: EntityPlayer?): Boolean {
        return true
    }

    open fun onEntitySwing(entityLiving: EntityLivingBase?, stack: ItemStack?): Boolean {
        return false
    }

    open fun onBlockStartBreak(itemstack: ItemStack?, x: Int, y: Int, z: Int, player: EntityPlayer?): Boolean {
        return false
    }

    companion object {
        var byName = HashMap<String, GenericItemUsingDamageDescriptor>()
        @JvmField
        var INVALID_NAME = "\$NO_DESCRIPTOR"
        @JvmStatic
        fun getByName(name: String?): GenericItemUsingDamageDescriptor? {
            return byName[name]
        }

        @JvmStatic
        fun getDescriptor(stack: ItemStack?): GenericItemUsingDamageDescriptor? {
            if (stack.isNothing()) return null
            return if (stack.item !is GenericItemUsingDamage<*>) null else (stack.item as GenericItemUsingDamage<*>).getDescriptor(stack)
        }

        @JvmStatic
        fun getDescriptor(stack: ItemStack?, extendClass: Class<*>): GenericItemUsingDamageDescriptor? {
            val desc = getDescriptor(stack) ?: return null
            return if (!extendClass.isAssignableFrom(desc.javaClass)) null else desc
        }
    }

}
