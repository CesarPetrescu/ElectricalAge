package mods.eln.registration

import mods.eln.Eln
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.registries.RegisterEvent
import java.util.Locale
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

/**
 * The mod's registration front door.
 *
 * NeoForge keeps the registries frozen except while it fires [RegisterEvent] for each of them, and
 * an [Item] or [Block] cannot even be *constructed* outside that window (its constructor creates an
 * intrusive holder in the frozen registry). Electrical Age's content code, on the other hand, is a
 * long ordered sequence of descriptor constructions that used to create their items on the spot.
 * The split is: descriptors and families are still built eagerly, in order, in the mod
 * constructor; every registry object is staged here as a factory and created inside its event;
 * and anything that needs an [ItemStack] at construction time (wiki data, tag entries, tab icons,
 * the `findItemStack` table) is queued with [afterItems] and runs as soon as the items exist.
 * The order of events is the vanilla registry order: blocks, then items, then block entity types,
 * then creative tabs.
 */
@EventBusSubscriber(modid = Eln.MODID, bus = EventBusSubscriber.Bus.MOD)
object ElnRegistry {

    /** A registry object that exists once its registry event has run; [get] throws before that. */
    class Staged<T : Any>(val id: ResourceLocation, private val factory: Supplier<T>, private val onRegistered: Consumer<T>?) : Supplier<T> {
        var value: T? = null
            private set

        override fun get(): T = value ?: throw IllegalStateException("$id is not registered yet - defer with ElnRegistry.afterItems")

        fun create(): T = factory.get().also { value = it }

        fun registered() = onRegistered?.accept(get())
    }

    private val blocks = LinkedHashMap<ResourceLocation, Staged<Block>>()
    private val items = LinkedHashMap<ResourceLocation, Staged<Item>>()
    private val tabs = LinkedHashMap<ResourceLocation, CreativeModeTab>()
    private val afterItems = ArrayList<Runnable>()
    private val namedStacks = HashMap<String, Supplier<ItemStack>>()
    private val itemBlocks = HashMap<ResourceLocation, Staged<Item>>()
    private var itemsRegistered = false

    /**
     * Registry names must match `[a-z0-9_.-]`. The mod names things for humans ("Copper Helmet",
     * "Eln.SixNode"), so they are folded down deterministically: any character outside the allowed
     * set becomes an underscore. Same rule as the 1.12.2 branch, so ids stay stable across the ports.
     */
    @JvmStatic
    fun registryName(name: String): ResourceLocation {
        val path = name.lowercase(Locale.ROOT)
            .removePrefix("eln.")
            .map { if (it.isLetterOrDigit() || it == '_' || it == '.' || it == '-') it else '_' }
            .joinToString("")
        return ResourceLocation.fromNamespaceAndPath(Eln.MODID, path)
    }

    private fun <T : Any> stage(into: LinkedHashMap<ResourceLocation, Staged<T>>, id: ResourceLocation, factory: Supplier<T>, onRegistered: Consumer<T>?, kind: String): Staged<T> {
        check(!into.containsKey(id)) { "duplicate $kind registry name $id" }
        return Staged(id, factory, onRegistered).also { into[id] = it }
    }

    @JvmStatic
    @JvmOverloads
    fun registerItem(name: String, factory: Supplier<Item>, onRegistered: Consumer<Item>? = null): Supplier<Item> =
        stage(items, registryName(name), factory, onRegistered, "item")

    @JvmStatic
    @JvmOverloads
    fun registerItem(id: ResourceLocation, factory: Supplier<Item>, onRegistered: Consumer<Item>? = null): Supplier<Item> =
        stage(items, id, factory, onRegistered, "item")

    /**
     * Stages a block and, unless [item] is null, an item for it under the same name (a plain
     * [BlockItem] by default). The item factory receives the block, which exists by then.
     */
    @JvmStatic
    @JvmOverloads
    fun registerBlock(name: String, factory: Supplier<Block>, item: Function<Block, Item>? = Function { BlockItem(it, Item.Properties()) }): Supplier<Block> {
        val id = registryName(name)
        val block = stage(blocks, id, factory, null, "block")
        if (item != null) {
            itemBlocks[id] = stage(items, id, { item.apply(block.get()) }, null, "item")
        }
        return block
    }

    /** The item registered by [registerBlock] for that block. Valid once the items exist. */
    @JvmStatic
    fun itemBlockOf(block: Block): Item {
        val id = blocks.entries.firstOrNull { it.value.value === block }?.key
            ?: throw IllegalStateException("$block was not registered through ElnRegistry")
        return itemBlocks[id]?.get() ?: throw IllegalStateException("no BlockItem registered for $id")
    }

    /** Tabs are plain objects; they are built eagerly (descriptors point at them) and registered in their event. */
    @JvmStatic
    fun registerCreativeTab(name: String, tab: CreativeModeTab): CreativeModeTab {
        val id = registryName(name)
        check(!tabs.containsKey(id)) { "duplicate creative tab registry name $id" }
        tabs[id] = tab
        return tab
    }

    /** Runs [action] right after the items are registered (or immediately if they already are). */
    @JvmStatic
    fun afterItems(action: Runnable) {
        if (itemsRegistered) action.run() else afterItems.add(action)
    }

    /** Replaces GameRegistry.registerCustomItemStack: name -> stack, read by [Eln.findItemStack]. */
    @JvmStatic
    fun registerCustomItemStack(name: String, stack: Supplier<ItemStack>) {
        namedStacks[name] = stack
    }

    @JvmStatic
    fun findItemStack(name: String, stackSize: Int): ItemStack? {
        val stack = namedStacks[name]?.get() ?: return null
        return stack.copyWithCount(stackSize)
    }

    private fun <T : Any> registerAll(event: RegisterEvent, key: ResourceKey<out net.minecraft.core.Registry<T>>, staged: Map<ResourceLocation, Staged<T>>) {
        event.register(key) { helper ->
            staged.values.forEach { helper.register(it.id, it.create()) }
        }
        staged.values.forEach { it.registered() }
    }

    // KFF registers the object instance, so the handler is an instance method (no @JvmStatic).
    @SubscribeEvent
    fun onRegister(event: RegisterEvent) {
        when (event.registryKey) {
            Registries.BLOCK -> registerAll(event, Registries.BLOCK, blocks)
            Registries.ITEM -> {
                registerAll(event, Registries.ITEM, items)
                itemsRegistered = true
                afterItems.forEach { it.run() }
                afterItems.clear()
                if (System.getProperty("eln.dumpRegistry") != null) {
                    blocks.keys.forEach { Eln.LOGGER.info("REGDUMP block {}", it) }
                    items.keys.forEach { Eln.LOGGER.info("REGDUMP item {}", it) }
                }
            }
            Registries.CREATIVE_MODE_TAB -> event.register(Registries.CREATIVE_MODE_TAB) { helper -> tabs.forEach { (id, tab) -> helper.register(id, tab) } }
        }
    }

    /** Every registered item, in staging order; data generation walks this. */
    @JvmStatic
    val registeredItems: Map<ResourceLocation, Item> get() = items.mapValues { it.value.get() }

    @JvmStatic
    val registeredBlocks: Map<ResourceLocation, Block> get() = blocks.mapValues { it.value.get() }
}
