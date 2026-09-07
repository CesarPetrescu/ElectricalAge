package mods.eln.wiki

import mods.eln.i18n.I18N.tr
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import java.util.Locale

/** Deterministic entries, including newly registered ELN items missing from the legacy groups. */
object WikiContent {
    @JvmStatic fun groups(): Map<String, List<ItemStack>> {
        val seen = hashSetOf<String>()
        val groups = sortedMapOf<String, List<ItemStack>>()
        for ((name, stacks) in Data.groupes.toSortedMap()) {
            val entries = stacks.filter { !it.isEmpty && seen.add(id(it)) }.sortedBy { it.hoverName.string }
            if (entries.isNotEmpty()) groups[name] = entries
        }
        val remaining = BuiltInRegistries.ITEM.filter {
            BuiltInRegistries.ITEM.getKey(it).namespace == "eln"
        }.map(::ItemStack).filter { !it.isEmpty && seen.add(id(it)) }.sortedBy { it.hoverName.string }
        if (remaining.isNotEmpty()) groups[tr("Other ELN items")] = remaining
        return groups
    }

    @JvmStatic fun search(query: String): List<ItemStack> {
        val needle = query.trim().lowercase(Locale.ROOT)
        return BuiltInRegistries.ITEM.map(::ItemStack).filter {
            !it.isEmpty && (it.hoverName.string.lowercase(Locale.ROOT).contains(needle) ||
                id(it).contains(needle))
        }.sortedWith(compareBy<ItemStack> { if (id(it).startsWith("eln:")) 0 else 1 }
            .thenBy { it.hoverName.string }.thenBy { id(it) })
    }

    private fun id(stack: ItemStack) = BuiltInRegistries.ITEM.getKey(stack.item).toString()
}
