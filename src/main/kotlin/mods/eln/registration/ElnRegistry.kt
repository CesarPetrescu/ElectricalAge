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
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import net.neoforged.neoforge.network.IContainerFactory
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
    private val blockEntities = LinkedHashMap<ResourceLocation, Staged<BlockEntityType<*>>>()
    private val menus = LinkedHashMap<ResourceLocation, Staged<MenuType<*>>>()
    private val tabs = LinkedHashMap<ResourceLocation, CreativeModeTab>()
    private val ores = ArrayList<Pair<String, Supplier<ItemStack>>>()
    private val afterItems = ArrayList<Runnable>()
    private val armorMaterials = LinkedHashMap<ResourceLocation, Staged<net.minecraft.world.item.ArmorMaterial>>()
    private val entityTypes = LinkedHashMap<ResourceLocation, Staged<net.minecraft.world.entity.EntityType<*>>>()
    private val attributes = ArrayList<Pair<Supplier<net.minecraft.world.entity.EntityType<out net.minecraft.world.entity.LivingEntity>>, Supplier<net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder>>>()
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
     * [registerItem] for a descriptor's item. Two descriptors of one family may share a display
     * name (1.7.10 never needed them distinct); a taken name gets the legacy id suffixed rather
     * than failing mod construction.
     */
    @JvmStatic
    fun registerDescriptorItem(name: String, legacyId: Int, factory: Supplier<Item>, onRegistered: Consumer<Item>?): Supplier<Item> {
        var id = registryName(name)
        if (items.containsKey(id)) {
            id = registryName("${name}_$legacyId")
            Eln.logger.warn("registry name for '$name' is taken; using $id")
        }
        return stage(items, id, factory, onRegistered, "item")
    }

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

    /**
     * Replaces GameRegistry.registerTileEntity. The type is built in its event (after blocks), so
     * the valid block is read through a supplier. [Eln] keeps the type in the block entity class.
     */
    @JvmStatic
    fun <T : BlockEntity> registerBlockEntity(name: String, block: Supplier<Block>, factory: BlockEntityType.BlockEntitySupplier<T>): Supplier<BlockEntityType<T>> {
        val id = registryName(name)
        @Suppress("UNCHECKED_CAST")
        val staged = stage(blockEntities, id, { BlockEntityType.Builder.of(factory, block.get()).build(null) as BlockEntityType<*> }, null, "block entity type")
        return Supplier { @Suppress("UNCHECKED_CAST") (staged.get() as BlockEntityType<T>) }
    }

    /**
     * The ore-dictionary entries of 1.7.10 (`OreDictionary.registerOre(name, stack)`). 1.13+ uses
     * item tags, which are data: the data generator turns this list into tag JSON, and at run time
     * [Eln.dictionnaryOreFromMod] answers `findItemStack` for those names. A null name is skipped:
     * upstream registers a few items under dictionary names it never assigns.
     */
    @JvmStatic
    fun registerOre(name: String?, stack: Supplier<ItemStack>) {
        if (name == null) {
            Eln.LOGGER.warn("Ore dictionary registration skipped: no dictionary name")
            return
        }
        ores.add(name to stack)
        afterItems { Eln.dictionnaryOreFromMod.putIfAbsent(name, stack.get()) }
    }

    @JvmStatic
    val oreEntries: List<Pair<String, Supplier<ItemStack>>> get() = ores

    @JvmStatic
    fun <T : AbstractContainerMenu> registerMenu(name: String, factory: IContainerFactory<T>): Supplier<MenuType<T>> {
        val staged = stage(menus, registryName(name), { IMenuTypeExtension.create(factory) as MenuType<*> }, null, "menu type")
        return Supplier { @Suppress("UNCHECKED_CAST") (staged.get() as MenuType<T>) }
    }

    /** Tabs are plain objects; they are built eagerly (descriptors point at them) and registered in their event. */
    @JvmStatic
    fun registerCreativeTab(name: String, tab: CreativeModeTab): CreativeModeTab {
        val id = registryName(name)
        check(!tabs.containsKey(id)) { "duplicate creative tab registry name $id" }
        tabs[id] = tab
        return tab
    }

    /**
     * Replaces Forge's `EnumHelper.addArmorMaterial`: armor materials are a registry since 1.20.5.
     * The holder resolves lazily, which is all `ArmorItem` needs (its attributes are memoized).
     */
    @JvmStatic
    fun registerArmorMaterial(name: String, factory: Supplier<net.minecraft.world.item.ArmorMaterial>): net.minecraft.core.Holder<net.minecraft.world.item.ArmorMaterial> {
        val id = registryName(name)
        stage(armorMaterials, id, factory, null, "armor material")
        return net.neoforged.neoforge.registries.DeferredHolder.create(Registries.ARMOR_MATERIAL, id)
    }

    /**
     * Replaces Forge's `EntityRegistry.registerModEntity`. The builder is evaluated in the
     * ENTITY_TYPE event; a living entity's attributes go through [registerAttributes].
     */
    @JvmStatic
    fun <T : net.minecraft.world.entity.Entity> registerEntityType(name: String, factory: Supplier<net.minecraft.world.entity.EntityType.Builder<T>>): Supplier<net.minecraft.world.entity.EntityType<T>> {
        val id = registryName(name)
        val staged = stage(entityTypes, id, { factory.get().build(id.toString()) as net.minecraft.world.entity.EntityType<*> }, null, "entity type")
        return Supplier { @Suppress("UNCHECKED_CAST") (staged.get() as net.minecraft.world.entity.EntityType<T>) }
    }

    /** 1.7.10's applyEntityAttributes: the attribute supplier registered for a living entity type. */
    @JvmStatic
    fun registerAttributes(type: Supplier<net.minecraft.world.entity.EntityType<out net.minecraft.world.entity.LivingEntity>>, builder: Supplier<net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder>) {
        attributes.add(type to builder)
    }

    @SubscribeEvent
    fun onEntityAttributes(event: net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent) {
        attributes.forEach { (type, builder) -> event.put(type.get(), builder.get().build()) }
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
            Registries.ARMOR_MATERIAL -> registerAll(event, Registries.ARMOR_MATERIAL, armorMaterials)
            Registries.ENTITY_TYPE -> registerAll(event, Registries.ENTITY_TYPE, entityTypes)
            Registries.BLOCK_ENTITY_TYPE -> registerAll(event, Registries.BLOCK_ENTITY_TYPE, blockEntities)
            Registries.MENU -> registerAll(event, Registries.MENU, menus)
            Registries.CREATIVE_MODE_TAB -> event.register(Registries.CREATIVE_MODE_TAB) { helper -> tabs.forEach { (id, tab) -> helper.register(id, tab) } }
        }
    }

    /** Every registered item, in staging order; data generation walks this. */
    @JvmStatic
    val registeredItems: Map<ResourceLocation, Item> get() = items.mapValues { it.value.get() }

    @JvmStatic
    val registeredBlocks: Map<ResourceLocation, Block> get() = blocks.mapValues { it.value.get() }
}
