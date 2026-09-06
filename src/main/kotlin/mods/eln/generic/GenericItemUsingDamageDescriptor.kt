package mods.eln.generic

import mods.eln.client.itemrender.IItemRenderer.ItemRenderType
import mods.eln.client.itemrender.IItemRenderer.ItemRendererHelper
import mods.eln.misc.RealisticEnum
import mods.eln.misc.VoltageLevelColor
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

/**
 * One entry of a [GenericItemUsingDamage] family. Since the 1.21 port every descriptor is its own
 * registered [Item] ([parentItem]); [parentItemDamage] is the legacy sub-id that the registration
 * code still hands out (it only orders the creative tab now).
 *
 * The hooks keep their 1.7.10 shape (stack first, int coordinates, `List<String>` tooltips);
 * [DescriptorItem] adapts the vanilla signatures once instead of touching ~100 overrides.
 */
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
    open var creativeTab: CreativeModeTab? = null
    var hidden: Boolean = false
    fun setDefaultIcon(name: String) {
        IconName = "eln:" + name.replace(" ".toRegex(), "").lowercase()
    }

    /** The sprite under assets/eln/textures/items/, without the "eln:" prefix. */
    val iconPath: String? get() = IconName?.removePrefix("eln:")

    open fun getDefaultNBT(): CompoundTag? = null

    open fun addInformation(itemStack: ItemStack?, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {}

    open fun addRealismContext(list: List<*>?): RealisticEnum? = null

    open fun onItemRightClick(s: ItemStack, w: Level, p: Player): ItemStack {
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
        val stack = ItemStack(parentItem ?: throw IllegalStateException("descriptor $name has no item yet"), size)
        getDefaultNBT()?.let { stack.set(DataComponents.CUSTOM_DATA, CustomData.of(it)) }
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
        if (stack == null || stack.isEmpty) return false
        return stack.item === this.parentItem
    }

    /**
     * Callback for item usage. If the item does something special on right clicking, he will have one of those. Return
     * True if something happen and false if it don't. This is for ITEMS, not BLOCKS
     */
    open fun onItemUse(stack: ItemStack?, player: Player?, world: Level?, x: Int, y: Int, z: Int, side: Int, vx: Float, vy: Float, vz: Float): Boolean {
        return false
    }

    /**
     * True when the descriptor draws itself (an OBJ model) instead of its JSON item model. The
     * voltage-level background of plain icons is a model layer since 1.21, so the default is false.
     */
    open fun handleRenderType(item: ItemStack?, type: ItemRenderType?): Boolean {
        return false
    }

    open fun shouldUseRenderHelper(type: ItemRenderType?, item: ItemStack?, helper: ItemRendererHelper?): Boolean {
        return false
    }

    open fun renderItem(type: ItemRenderType?, item: ItemStack?, vararg data: Any?) {}

    open fun onUpdate(stack: ItemStack, world: Level, entity: Entity, par4: Int, par5: Boolean) {}

    /**
     * 1.20.5+: item NBT lives in the CUSTOM_DATA component and is immutable in place. This returns a
     * copy (seeded from [getDefaultNBT] when the stack has none); write it back with [setNbt], or use
     * [updateNbt] for a read-modify-write.
     */
    protected fun getNbt(stack: ItemStack): CompoundTag {
        val existing = stack.get(DataComponents.CUSTOM_DATA)
        if (existing != null) return existing.copyTag()
        val nbt = getDefaultNBT() ?: CompoundTag()
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt))
        return nbt.copy()
    }

    protected fun setNbt(stack: ItemStack, nbt: CompoundTag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt))
    }

    protected inline fun updateNbt(stack: ItemStack, edit: (CompoundTag) -> Unit) {
        val nbt = getNbt(stack)
        edit(nbt)
        setNbt(stack, nbt)
    }

    /** 1.8's Item.getDestroySpeed: the block is identified by its state now, not the Block. */
    open fun getDestroySpeed(stack: ItemStack, state: BlockState): Float {
        return 0.2f
    }

    open fun onBlockDestroyed(stack: ItemStack, w: Level, state: BlockState, pos: BlockPos, entity: LivingEntity): Boolean {
        return false
    }

    open fun onDroppedByPlayer(item: ItemStack, player: Player?): Boolean {
        return true
    }

    open fun onEntitySwing(entityLiving: LivingEntity?, stack: ItemStack?): Boolean {
        return false
    }

    open fun onBlockStartBreak(itemstack: ItemStack?, x: Int, y: Int, z: Int, player: Player?): Boolean {
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
            if (stack == null || stack.isEmpty) return null
            return (stack.item as? DescriptorItem<*>)?.descriptor
        }

        @JvmStatic
        fun getDescriptor(stack: ItemStack?, extendClass: Class<*>): GenericItemUsingDamageDescriptor? {
            val desc = getDescriptor(stack) ?: return null
            return if (!extendClass.isAssignableFrom(desc.javaClass)) null else desc
        }
    }

}
