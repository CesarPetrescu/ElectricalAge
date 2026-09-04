package mods.eln.registration

import mods.eln.Eln
import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.Locale

/**
 * Collects the mod's blocks and items during preInit and hands them to Forge when it fires the
 * registry events.
 *
 * On 1.7.10 `GameRegistry.registerItem` took effect immediately, so Electrical Age builds all of
 * its content inline in preInit, in an order that matters: descriptors are wired to their items
 * as they are constructed. 1.9 moved registration into [RegistryEvent.Register], which fires
 * *before* preInit. Rather than reorder ~250 descriptor constructions, the calls now stage their
 * objects here and the events drain the staging list. Construction order is preserved.
 *
 * `GameRegistry.registerCustomItemStack` is gone with no replacement; it backed
 * [Eln.findItemStack], which the crafting recipes use to name a stack by its display name. That
 * lookup table lives here now.
 */
@Mod.EventBusSubscriber(modid = Eln.MODID)
object ElnRegistry {

    private val pendingBlocks = ArrayList<Block>()
    private val pendingItems = ArrayList<Item>()
    private val namedStacks = HashMap<String, ItemStack>()

    /**
     * Registry names must match `[a-z0-9_.-]` since 1.11. The mod names things for humans
     * ("Copper Helmet", "Eln.SixNode"), so they are folded down deterministically: any character
     * outside the allowed set becomes an underscore.
     */
    fun registryName(name: String): ResourceLocation {
        val path = name.lowercase(Locale.ROOT)
            .removePrefix("eln.")
            .map { if (it.isLetterOrDigit() || it == '_' || it == '.' || it == '-') it else '_' }
            .joinToString("")
        return ResourceLocation(Eln.MODID, path)
    }

    @JvmStatic
    fun registerItem(item: Item, name: String): Item {
        item.registryName ?: item.setRegistryName(registryName(name))
        pendingItems.add(item)
        return item
    }

    @JvmStatic
    @JvmOverloads
    fun registerBlock(block: Block, name: String, itemBlockClass: Class<out ItemBlock>? = null): Block {
        block.registryName ?: block.setRegistryName(registryName(name))
        pendingBlocks.add(block)
        val itemBlock = when (itemBlockClass) {
            null -> ItemBlock(block)
            else -> itemBlockClass.getConstructor(Block::class.java).newInstance(block)
        }
        itemBlock.registryName = block.registryName
        pendingItems.add(itemBlock)
        return block
    }

    /** Registers an item that was constructed and named elsewhere (fluid buckets, ore items). */
    @JvmStatic
    fun registerItem(item: Item): Item {
        pendingItems.add(item)
        return item
    }

    /** Replaces GameRegistry.registerCustomItemStack: name -> stack, read by [Eln.findItemStack]. */
    @JvmStatic
    fun registerCustomItemStack(name: String, stack: ItemStack) {
        namedStacks[name] = stack
    }

    @JvmStatic
    fun findItemStack(name: String, stackSize: Int): ItemStack? {
        val stack = namedStacks[name] ?: return null
        return stack.copy().apply { count = stackSize }
    }

    @JvmStatic
    @SubscribeEvent
    fun onRegisterBlocks(event: RegistryEvent.Register<Block>) {
        pendingBlocks.forEach(event.registry::register)
    }

    @JvmStatic
    @SubscribeEvent
    fun onRegisterItems(event: RegistryEvent.Register<Item>) {
        pendingItems.forEach(event.registry::register)
    }
}
