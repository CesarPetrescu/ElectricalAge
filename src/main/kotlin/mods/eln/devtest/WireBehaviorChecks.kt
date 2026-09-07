package mods.eln.devtest

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.ElectricalConnection
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sixnode.electricalcable.*
import mods.eln.transparentnode.*
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.RecipeType
import kotlin.math.abs
import kotlin.math.ceil

/** Registered inventories and MNA circuits; runs only in opted-in GitHub smoke servers. */
object WireBehaviorChecks {
    private const val DT = .05
    private fun near(a: Double, b: Double, tolerance: Double = 1e-7) {
        check(abs(a - b) <= tolerance) { "Expected $b, got $a" }
    }
    private fun id(d: UtilityCableDescriptor) = BuiltInRegistries.ITEM.getKey(d.parentItem).toString()
    private fun machine(world: ServerLevel, kind: WireMachineKind): WireMachineElement {
        val d = Eln.transparentNodeItem.subItemList.values.filterIsInstance<WireMachineDescriptor>().single { it.kind == kind }
        return WireMachineElement(TransparentNode().apply { coordinate = Coordinate(768, 65, 768, world) }, d)
    }
    private class Bench(val machine: WireMachineElement, volts: Double) {
        val root = RootSystem(DT, 1)
        val source = VoltageSource("wire-test", machine.electricalLoadList.single(), null).setVoltage(volts)
        init {
            machine.electricalLoadList.forEach(root::addState)
            machine.electricalComponentList.forEach(root::addComponent)
            root.addComponent(source); root.generate()
        }
        fun tick() { machine.slowProcessList.forEach { it.process(DT) }; root.step() }
    }
    private fun finish(world: ServerLevel, initial: WireMachineElement, output: Int, restart: Boolean): WireMachineElement {
        var m = initial
        var bench = Bench(m, 200.0)
        repeat(3) { bench.tick() }
        if (restart) {
            val saved = CompoundTag(); m.writeToNBT(saved)
            m = machine(world, m.machineDescriptor.kind).apply { readFromNBT(saved) }
            bench = Bench(m, 200.0)
        }
        repeat(250) { if (m.inventory.getItem(output).isEmpty) bench.tick() }
        check(!m.inventory.getItem(output).isEmpty) { "${m.machineDescriptor.kind} produced no output" }
        val before = m.inventory.getItem(output).copy()
        repeat(5) { bench.tick() }
        check(ItemStack.isSameItemSameComponents(before, m.inventory.getItem(output))) { "Occupied output overwritten" }
        return m
    }

    private fun produce(world: ServerLevel, d: UtilityCableDescriptor, restart: Boolean): ItemStack {
        if (!d.insulated) {
            val m = machine(world, WireMachineKind.ROLLER)
            m.targetLengthMeters = 2
            val ingots = WireProductionRecipes.ingot(d.material).copyWithCount(ceil(WirePhysics.massKg(d.material, d.totalConductorAreaMm2, 2.0)).toInt())
            check(!ingots.isEmpty)
            val paid = ingots.count.toDouble()
            m.inventory.setItem(0, ingots)
            m.inventory.setItem(1, Eln.findItemStack("Iron Roller Wheel", 1))
            m.inventory.setItem(2, Eln.findItemStack("Iron Roller Wheel", 1))
            m.selectedOption = WireProduction.rollerOptions(d.material).indexOf(d)
            val result = finish(world, m, 3, restart)
            near(paid - result.loadedMassKg, WirePhysics.massKg(d.material, d.totalConductorAreaMm2, 2.0))
            check(result.inventory.getItem(1).count == 1 && result.inventory.getItem(2).count == 1)
            return result.inventory.getItem(3).copy()
        }
        val input = if (d.conductorCount == 1) {
            produce(world, checkNotNull(WireProduction.singleFor(d, false)), restart)
        } else {
            val single = checkNotNull(WireProduction.singleFor(d, true))
            val m = machine(world, WireMachineKind.COMBINER)
            val inputs = List(d.conductorCount) { produce(world, single, restart) }
            inputs.forEachIndexed { i, stack -> m.inventory.setItem(WireProduction.combinerInputs[i], stack) }
            m.selectedOption = WireProduction.combinerOptions(inputs).indexOf(d)
            check(m.selectedOption >= 0) { "No combining route for ${d.name}" }
            val result = finish(world, m, 5, restart)
            check(WireProduction.combinerInputs.all { result.inventory.getItem(it).isEmpty }) { "Core material duplicated" }
            result.inventory.getItem(5).copy()
        }
        val m = machine(world, WireMachineKind.INSULATOR)
        m.inventory.setItem(0, input); m.inventory.setItem(1, Eln.findItemStack("Rubber", 1))
        val result = finish(world, m, 2, restart)
        near(result.insulationMetersBuffer, 30.0)
        check(result.inventory.getItem(0).isEmpty && result.inventory.getItem(1).isEmpty)
        return result.inventory.getItem(2).copy()
    }

    @JvmStatic fun run(world: ServerLevel, restart: Boolean): Int {
        val report = ContractReport(if (restart) "wire-behavior-restart" else "wire-behavior")
        report.write(false)
        for (d in UtilityCableDescriptor.allDescriptors()) {
            if (d.melted) { report.skip(id(d), "manufacture", "Damage product, not a survival manufacturing output"); continue }
            report.test(id(d), "manufacture-from-ingots-and-rubber") {
                val output = produce(world, d, restart)
                check(d.checkSameItemStack(output)) { "Wrong wire output" }
                near(d.getRemainingLengthMeters(output), 2.0)
                check(WireProductionRecipes.outputs(output).isNotEmpty()) { "Wire invisible in wiki" }
            }
            report.test(id(d), "per-core-resistance-and-MNA-voltage-drop") {
                val cable = UtilityCableElement(SixNode().apply { coordinate = Coordinate(770, 65, 768, world) }, Direction.YN, d)
                cable.initialize()
                val loads = cable.electricalLoadList.filterIsInstance<ElectricalLoad>()
                check(loads.size == d.conductorCount)
                loads.forEach { near(it.serialResistance * 2, d.resistanceOhms(), 1e-12) }
                val r = RootSystem(DT, 1)
                val sourceLoad = ElectricalLoad().apply { serialResistance = 0.0 }
                val end = ElectricalLoad().apply { serialResistance = 0.0 }
                listOf(sourceLoad, loads[0], end).forEach(r::addState)
                val source = VoltageSource("wire-drop", sourceLoad, null).setVoltage(10.0)
                val sink = Resistor(end, null).apply { resistance = 10.0 }
                r.addComponent(source); r.addComponent(sink)
                r.addComponent(ElectricalConnection(sourceLoad, loads[0])); r.addComponent(ElectricalConnection(loads[0], end))
                r.generate(); repeat(5) { r.step() }
                near(sink.current, 10.0 / (10.0 + d.resistanceOhms()), 1e-6)
                check(end.voltage < 10.0) { "Cable is lossless" }
                val hot = d.resistanceOhms(celsius = 80.0)
                loads[0].serialResistance = hot / 2; repeat(5) { r.step() }
                near(sink.current, 10.0 / (10.0 + hot), 1e-6)
                if (d.poleEligible) {
                    near(mods.eln.gridnode.GridLink.resistanceForCable(d, 32), d.resistanceOhms(32.0), 1e-12)
                    val span = mods.eln.gridnode.WireSpanConnection(sourceLoad, end, d.resistanceOhms(32.0))
                    span.notifyRsChange()
                    near(span.resistance, d.resistanceOhms(32.0), 1e-12)
                    end.serialResistance = .1; span.notifyRsChange()
                    near(span.resistance, d.resistanceOhms(32.0) + .1, 1e-12)
                }
            }
        }
        for (name in listOf("Wire Roller", "Wire Insulator", "Wire Combiner", "Iron Roller Wheel", "Steel Roller Wheel", "Aluminum Roller Wheel")) {
            report.test(name, "packaged-survival-crafting-recipe") {
                val output = Eln.findItemStack(name, 1)
                val recipe = world.recipeManager.getAllRecipesFor(RecipeType.CRAFTING).map { it.value() }
                    .firstOrNull { ItemStack.isSameItem(it.getResultItem(world.registryAccess()), output) }
                checkNotNull(recipe) { "Missing packaged recipe for $name" }
                val inputs = recipe.ingredients.map { ingredient ->
                    if (ingredient.isEmpty) ItemStack.EMPTY else {
                        check(ingredient.items.isNotEmpty()) { "Empty ingredient tag" }; ingredient.items.first().copy()
                    }
                }
                val crafting = CraftingInput.of(3, 3, inputs)
                check(recipe.matches(crafting, world))
                check(ItemStack.isSameItem(recipe.assemble(crafting, world.registryAccess()), output))
                check(inputs.none { WireProduction.cable(it) != null }) { "Wire machine requires its own output to craft" }
            }
        }
        report.test("wire-machines", "no-power-no-output-and-no-invalid-bundles") {
            val d = WireProduction.rollerOptions(UtilityCableMaterial.COPPER).first()
            val m = machine(world, WireMachineKind.ROLLER)
            m.inventory.setItem(0, ItemStack(Items.COPPER_INGOT)); m.targetLengthMeters = 2
            m.inventory.setItem(1, Eln.findItemStack("Iron Roller Wheel", 1)); m.inventory.setItem(2, Eln.findItemStack("Iron Roller Wheel", 1))
            val bench = Bench(m, 0.0); repeat(50) { bench.tick() }
            check(m.inventory.getItem(3).isEmpty && m.progressMeters == 0.0)
            check(WireProduction.combinerOptions(listOf(WireProductionRecipes.spool(d), WireProductionRecipes.spool(d))).isEmpty())
        }
        report.test("eln:wire_combiner", "unequal-lengths-preserve-remainders-and-legacy-slots") {
            val d = UtilityCableDescriptor.allDescriptors().first { !it.melted && it.insulated && it.conductorCount == 2 }
            val single = checkNotNull(WireProduction.singleFor(d, true))
            val m = machine(world, WireMachineKind.COMBINER)
            val a = WireProductionRecipes.spool(single, 2.0)
            val b = WireProductionRecipes.spool(single, 5.0)
            m.inventory.setItem(0, a); m.inventory.setItem(4, b)
            m.selectedOption = WireProduction.combinerOptions(listOf(a, b)).indexOf(d)
            val result = finish(world, m, 5, restart)
            check(result.inventory.getItem(0).isEmpty)
            near(single.getRemainingLengthMeters(result.inventory.getItem(4)), 3.0)
            near(Eln.instance.woundWireBundleDescriptor!!.getLengthMeters(result.inventory.getItem(5)), 2.0)
            check(result.inventory.containerSize == 9)
        }
        report.write(true)
        return report.failures
    }
}
