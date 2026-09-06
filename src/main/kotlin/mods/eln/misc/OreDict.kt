package mods.eln.misc

import mods.eln.Eln
import mods.eln.registration.ElnRegistry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.ItemTags
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * The 1.7.10 ore dictionary, on 1.21 item tags. A dictionary name maps onto the conventional
 * `c:` tag ("ingotCopper" -> `c:ingots/copper`); the mod's own registrations
 * ([ElnRegistry.registerOre]) are matched by item as well, so a name only Electrical Age uses
 * ("dustAlloy") resolves before the tag JSON exists. The data generator writes the same mapping.
 */
object OreDict {
    private val prefixes = linkedMapOf(
        "ingot" to "ingots", "dust" to "dusts", "ore" to "ores", "plate" to "plates", "nugget" to "nuggets",
        "gem" to "gems", "crystal" to "gems", "block" to "storage_blocks", "stick" to "rods", "rod" to "rods",
        "wire" to "wires", "circuit" to "circuits", "casing" to "casings", "item" to "", "material" to ""
    )

    /** Names whose conventional tag is not the prefix rule: vanilla's own tags, and the names AE2 uses. */
    private val special = mapOf(
        "plankWood" to "minecraft:planks", "logWood" to "minecraft:logs", "blockWool" to "minecraft:wool",
        "stickWood" to "c:rods/wooden", "materialString" to "c:strings", "blockGlass" to "c:glass_blocks",
        "paneGlass" to "c:glass_panes", "cobblestone" to "c:cobblestones", "stone" to "c:stones", "sand" to "c:sands",
        "gravel" to "c:gravels", "dustRedstone" to "c:dusts/redstone", "dustGlowstone" to "c:dusts/glowstone",
        "gemDiamond" to "c:gems/diamond", "gemEmerald" to "c:gems/emerald", "gemLapis" to "c:gems/lapis",
        "crystalNetherQuartz" to "c:gems/quartz", "gemQuartz" to "c:gems/quartz", "dustNetherQuartz" to "c:dusts/quartz",
        "oreNetherQuartz" to "c:ores/quartz", "crystalCertusQuartz" to "c:gems/certus_quartz",
        "dustCertusQuartz" to "c:dusts/certus_quartz", "oreCertusQuartz" to "c:ores/certus_quartz",
        "crystalFluix" to "c:gems/fluix", "dustFluix" to "c:dusts/fluix"
    )

    /** The conventional tag for a 1.7.10 dictionary name; names without a known prefix keep their spelling. */
    @JvmStatic
    fun tagFor(name: String): TagKey<Item> {
        special[name]?.let { return ItemTags.create(ResourceLocation.parse(it)) }
        for ((prefix, folder) in prefixes) {
            if (name.length > prefix.length && name.startsWith(prefix) && name[prefix.length].isUpperCase()) {
                val rest = name.substring(prefix.length)
                val path = rest.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()
                return ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", if (folder.isEmpty()) path else "$folder/$path"))
            }
        }
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", name.lowercase()))
    }

    /** 1.7.10's `OreDictionary.getOreIDs(stack)` membership test for one name. */
    @JvmStatic
    fun matches(stack: ItemStack?, name: String): Boolean {
        if (stack == null || stack.isEmpty) return false
        if (stack.`is`(tagFor(name))) return true
        for ((oreName, supplier) in ElnRegistry.oreEntries) {
            if (oreName == name && ItemStack.isSameItem(supplier.get(), stack)) return true
        }
        return false
    }

    /** Every dictionary name a stack answers to (the mod's own names plus the `c:` tags it carries). */
    @JvmStatic
    fun namesOf(stack: ItemStack?): List<String> {
        if (stack == null || stack.isEmpty) return emptyList()
        val out = LinkedHashSet<String>()
        for ((oreName, supplier) in ElnRegistry.oreEntries) {
            if (ItemStack.isSameItem(supplier.get(), stack)) out.add(oreName)
        }
        stack.tags.forEach { tag ->
            val loc = tag.location()
            if (loc.namespace == "c") {
                val parts = loc.path.split('/')
                if (parts.size == 2) {
                    val prefix = prefixes.entries.firstOrNull { it.value == parts[0] && it.value.isNotEmpty() }?.key
                    if (prefix != null) out.add(prefix + parts[1].split('_').joinToString("") { it.replaceFirstChar(Char::uppercase) })
                }
            }
        }
        return out.toList()
    }

    /** 1.7.10's `OreDictionary.getOres(name)`: the mod's stacks under that name, then every item in the tag. */
    @JvmStatic
    fun getOres(name: String): List<ItemStack> {
        val out = ArrayList<ItemStack>()
        for ((oreName, supplier) in ElnRegistry.oreEntries) if (oreName == name) out.add(supplier.get())
        BuiltInRegistries.ITEM.getTagOrEmpty(tagFor(name)).forEach { holder ->
            if (out.none { it.item === holder.value() }) out.add(ItemStack(holder.value()))
        }
        return out
    }

    @JvmStatic
    fun getOreName(stack: ItemStack?): String? = namesOf(stack).firstOrNull() ?: Eln.dictionnaryOreFromMod.entries.firstOrNull { ItemStack.isSameItem(it.value, stack) }?.key
}
