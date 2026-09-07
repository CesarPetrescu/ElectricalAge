package mods.eln.transparentnode

import mods.eln.Eln
import mods.eln.misc.OreDict
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import mods.eln.sixnode.electricalcable.UtilityCableMaterial
import mods.eln.sixnode.electricalcable.WirePhysics
import net.minecraft.world.item.ItemStack
import kotlin.math.ceil

/** Displayable examples of the same variable-length operations used by the machines (not table recipes). */
object WireProductionRecipes {
    const val EXAMPLE_METERS = 32.0
    data class Step(val kind: WireMachineKind, val inputs: List<ItemStack>, val output: ItemStack,
                    val catalysts: List<ItemStack> = emptyList(), val metalKg: Double = 0.0) {
        val machine: ItemStack get() = Eln.findItemStack(kind.displayName, 1)
        val energyJoules: Double get() = EXAMPLE_METERS / kind.metersPerSecond * kind.nominalPowerWatts
    }

    fun spool(d: UtilityCableDescriptor, meters: Double = EXAMPLE_METERS) = d.newItemStack().also {
        d.setRemainingLengthMeters(it, meters)
    }

    fun ingot(material: UtilityCableMaterial): ItemStack = OreDict.getOres(
        if (material == UtilityCableMaterial.COPPER) "ingotCopper" else "ingotAluminum"
    ).firstOrNull()?.copyWithCount(1) ?: ItemStack.EMPTY

    fun steps(): List<Step> = buildList {
        for (d in UtilityCableDescriptor.allDescriptors().filterNot { it.melted }) {
            if (!d.insulated) {
                val kg = WirePhysics.massKg(d.material, d.totalConductorAreaMm2, EXAMPLE_METERS)
                add(Step(WireMachineKind.ROLLER, listOf(ingot(d.material).copyWithCount(ceil(kg).toInt())), spool(d),
                    List(2) { Eln.findItemStack("Iron Roller Wheel", 1) }, kg))
            } else if (d.conductorCount == 1) {
                val bare = WireProduction.singleFor(d, false) ?: continue
                add(Step(WireMachineKind.INSULATOR, listOf(spool(bare), Eln.findItemStack("Rubber", 1)), spool(d)))
            } else {
                val single = WireProduction.singleFor(d, true) ?: continue
                val bundle = Eln.instance.woundWireBundleDescriptor!!.createBundleStack(
                    d.sizeLabel, d.metricSizeLabel, d.material, d.conductorCount, d.conductorAreaMm2, EXAMPLE_METERS)
                add(Step(WireMachineKind.COMBINER, List(d.conductorCount) { spool(single) }, bundle))
                add(Step(WireMachineKind.INSULATOR, listOf(bundle.copy(), Eln.findItemStack("Rubber", 1)), spool(d)))
            }
        }
    }

    fun matches(example: ItemStack, query: ItemStack): Boolean {
        if (!ItemStack.isSameItem(example, query)) return false
        val bundle = Eln.instance.woundWireBundleDescriptor ?: return true
        if (!bundle.checkSameItemStack(query) || bundle.getTargetLabel(query) == null) return true
        return bundle.getTargetLabel(example) == bundle.getTargetLabel(query) && bundle.getMaterial(example) == bundle.getMaterial(query)
    }
    fun outputs(stack: ItemStack) = steps().filter { matches(it.output, stack) }
    fun uses(stack: ItemStack) = steps().filter { step -> step.inputs.any { matches(it, stack) } ||
        step.catalysts.any { matches(it, stack) } || ItemStack.isSameItem(step.machine, stack) }
}
