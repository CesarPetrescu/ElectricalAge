package mods.eln.transparentnode

import mods.eln.Eln
import mods.eln.misc.OreDict
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import mods.eln.sixnode.electricalcable.UtilityCableMaterial
import net.minecraft.world.item.ItemStack
import kotlin.math.abs

/** Shared by machines, wiki and coverage checks; no parallel, hand-maintained recipe table. */
object WireProduction {
    // Preserve legacy output slot 5 and inputs 0..4 when loading existing machines.
    val combinerInputs = intArrayOf(0, 1, 2, 3, 4, 6, 7, 8)
    const val RUBBER_METERS = 32.0
    const val MAX_LENGTH = 65536

    fun cable(stack: ItemStack) = if (stack.isEmpty) null else Eln.sixNodeItem.getDescriptor(stack) as? UtilityCableDescriptor
    fun material(stack: ItemStack): UtilityCableMaterial? = when {
        stack.isEmpty -> null
        OreDict.matches(stack, "ingotCopper") -> UtilityCableMaterial.COPPER
        OreDict.matches(stack, "ingotAluminum") || OreDict.matches(stack, "ingotAluminium") -> UtilityCableMaterial.ALUMINUM
        else -> null
    }

    fun rollerOptions(material: UtilityCableMaterial?) = UtilityCableDescriptor.allDescriptors()
        .filter { !it.insulated && !it.melted && it.conductorCount == 1 && it.material == (material ?: UtilityCableMaterial.COPPER) }
        .sortedBy { it.totalConductorAreaMm2 }

    fun singleFor(target: UtilityCableDescriptor, insulated: Boolean) = UtilityCableDescriptor.allDescriptors().firstOrNull {
        !it.melted && it.insulated == insulated && it.material == target.material && it.conductorCount == 1 &&
            abs(it.conductorAreaMm2 - target.conductorAreaMm2) < 0.001
    }

    fun combinerOptions(inputs: List<ItemStack>): List<UtilityCableDescriptor> {
        if (inputs.size !in 2..8) return emptyList()
        val wires = inputs.map { cable(it) ?: return emptyList() }
        val first = wires.first()
        if (wires.any { !it.insulated || it.melted || it.conductorCount != 1 || it.material != first.material ||
                abs(it.conductorAreaMm2 - first.conductorAreaMm2) > 0.001 }) return emptyList()
        return UtilityCableDescriptor.allDescriptors().filter {
            it.insulated && !it.melted && it.conductorCount == wires.size && it.material == first.material &&
                abs(it.conductorAreaMm2 - first.conductorAreaMm2) < 0.001
        }.sortedBy { it.name }
    }
}
