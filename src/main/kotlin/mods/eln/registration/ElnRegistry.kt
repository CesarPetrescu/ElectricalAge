package mods.eln.registration

import mods.eln.Eln
import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import com.google.gson.JsonParser
import net.minecraft.util.ResourceLocation
import net.minecraft.util.SoundEvent
import java.io.InputStreamReader
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.registry.GameRegistry
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.oredict.OreDictionary
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

    private val itemBlocks = HashMap<Block, ItemBlock>()

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
        itemBlocks[block] = itemBlock
        return block
    }

    /**
     * The ItemBlock created by [registerBlock]. 1.7.10's GameRegistry.registerBlock registered the
     * item on the spot, so `Item.getItemFromBlock` worked inside preInit; on 1.12 the item only
     * reaches the registry in RegistryEvent.Register<Item>, which fires after preInit.
     */
    @JvmStatic
    fun itemBlockOf(block: Block): ItemBlock =
        itemBlocks[block] ?: throw IllegalStateException("no ItemBlock registered for ${block.registryName}")

    /**
     * Replaces TileEntity.addMapping: tile entity ids are ResourceLocations on 1.12 and the
     * registry is the same one GameRegistry.registerTileEntity feeds. Safe to call in preInit.
     */
    @JvmStatic
    fun registerTileEntity(tileEntityClass: Class<out TileEntity>, name: String) {
        GameRegistry.registerTileEntity(tileEntityClass, registryName(name))
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
        // Ore dictionary entries need registered items ("broken ore dictionary registration"
        // otherwise); everything queued during preInit lands here, right after the items.
        pendingOres.forEach { (name, stack) -> OreDictionary.registerOre(name, stack) }
        pendingOres.clear()
        pendingOreBlocks.forEach { (name, block) -> OreDictionary.registerOre(name, ItemStack(itemBlockOf(block))) }
        pendingOreBlocks.clear()
    }

    private val pendingOres = ArrayList<Pair<String, ItemStack>>()

    /**
     * Deferred OreDictionary.registerOre: 1.12 wants the item in the registry first. A null name
     * is skipped: upstream registers a few items under dictionary names it never assigns
     * (Eln.dictSiliconWafer and friends), which 1.7.10's HashMap silently accepted.
     */
    @JvmStatic
    fun registerOre(name: String?, stack: ItemStack) {
        if (name == null) {
            Eln.logger.warn("Ore dictionary registration of {} skipped: no dictionary name", stack.displayName)
            return
        }
        pendingOres.add(name to stack)
    }

    /**
     * 1.9+: sounds are registry objects. Every key of assets/eln/sounds.json becomes an
     * eln:<key> SoundEvent, so server-side World.playSound and the client sound commands both
     * resolve to a registered event (an unregistered one is sent to clients as id -1 and NPEs).
     */
    @JvmStatic
    @SubscribeEvent
    fun onRegisterSounds(event: RegistryEvent.Register<SoundEvent>) {
        val stream = ElnRegistry::class.java.getResourceAsStream("/assets/eln/sounds.json") ?: return
        // entrySet, not keySet: Minecraft 1.12.2 ships Gson 2.8.0, which predates JsonObject.keySet.
        val keys = stream.use { JsonParser().parse(InputStreamReader(it, Charsets.UTF_8)).asJsonObject.entrySet().map { e -> e.key } }
        keys.forEach { key ->
            val id = ResourceLocation(Eln.MODID, key)
            event.registry.register(SoundEvent(id).setRegistryName(id))
        }
    }

    /** Block form: the ItemStack is only built once the ItemBlock exists, i.e. at flush time. */
    @JvmStatic
    fun registerOre(name: String, block: Block) {
        pendingOreBlocks.add(name to block)
    }

    private val pendingOreBlocks = ArrayList<Pair<String, Block>>()
}
