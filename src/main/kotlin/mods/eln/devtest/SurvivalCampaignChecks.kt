package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.RecipesList
import mods.eln.node.NodeManager
import mods.eln.node.transparent.TransparentNode
import mods.eln.transparentnode.*
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import mods.eln.sixnode.electricalcable.UtilityCableMaterial
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.inventory.CraftingMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.*
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

/** Opt-in QA only. Uses live recipe tags, survival menu transactions and normal player block breaking. */
@EventBusSubscriber(modid = Eln.MODID)
object SurvivalCampaignChecks {
    private var ticks = 0
    private val directory = Path.of("../../build/smoke-artifacts/contracts")
    private val report by lazy { ContractReport("survival-campaign", directory) }
    private data class Rule(val output: String, val inputs: List<List<String>>, val via: String)
    private val fixtures = linkedMapOf<String, BlockPos>()
    private fun key(stack: ItemStack): String {
        val base = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        val bundle = Eln.instance.woundWireBundleDescriptor
        return if (bundle != null && bundle.checkSameItemStack(stack))
            "$base[${bundle.getMaterial(stack)}:${bundle.getTargetLabel(stack)}]" else base
    }
    private fun write(name: String, value: Any) {
        Files.createDirectories(directory)
        Files.writeString(directory.resolve(name), GsonBuilder().setPrettyPrinting().create().toJson(value))
    }
    private fun machine(world: ServerLevel, pos: BlockPos) =
        (NodeManager.instance!!.getNodeFromCoordonate(Coordinate(pos.x, pos.y, pos.z, world)) as TransparentNode).element as WireMachineElement

    @SubscribeEvent fun tick(event: ServerTickEvent.Post) {
        if (System.getProperty("eln.campaign") != "survival") return
        ticks++
        try {
            if (ticks == 20) {
                report.write(false)
                recipes(event.server.overworld())
                mods.eln.transparentnode.heatfurnace.HeatFurnaceFuelChecks.run(event.server.overworld(), report)
                fixtures(event.server.overworld())
            }
            if (ticks == 100) {
                breakAndReinstall(event.server.overworld())
                report.write(true)
                shutdown(event.server)
            }
        } catch (t: Throwable) {
            report.test("campaign", "uncaught-$ticks") { throw t }
            report.write(false)
            shutdown(event.server)
        }
    }

    private fun recipes(world: ServerLevel) {
        val rules = mutableListOf<Rule>()
        val crafting = world.recipeManager.getAllRecipesFor(RecipeType.CRAFTING)
        val outputs = linkedMapOf<String, MutableList<String>>()
        val transactions = mutableListOf<Map<String, Any>>()
        val player = FakePlayerFactory.getMinecraft(world)
        player.setGameMode(GameType.SURVIVAL)
        val table = BlockPos(144, 64, 144)
        world.setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState())
        player.teleportTo(table.x + .5, table.y + 1.0, table.z + 1.5)
        val transactionNames = setOf("eln:wire_snips", "eln:polarized_shaft_generator", "eln:polarized_shaft_motor", "eln:tree_resin_collector", "eln:machine_block", "eln:copper_cable", "eln:iron_cable", "eln:low_voltage_cable", "eln:electrical_motor", "eln:iron_roller_wheel", "eln:wire_roller", "eln:wire_insulator", "eln:wire_combiner", "eln:stone_heat_furnace", "eln:48v_turbine", "eln:multimeter", "eln:thermometer", "eln:cost_oriented_battery", "eln:simple_lamp_socket")
        val transacted = mutableSetOf<String>()
        for (holder in crafting) {
            val recipe = holder.value()
            val output = recipe.getResultItem(world.registryAccess())
            if (output.isEmpty) continue
            val out = key(output)
            val inputs = recipe.ingredients.filter { it !== Ingredient.EMPTY }.map { i -> i.items.map { key(it) }.distinct() }
            rules.add(Rule(out, inputs, holder.id().toString()))
            if (!out.startsWith("eln:")) continue
            outputs.getOrPut(out) { mutableListOf() }.add(holder.id().toString())
            if (recipe !is ShapedRecipe && recipe !is ShapelessRecipe) {
                report.skip(holder.id().toString(), "crafting", "Custom serializer: no generic representative grid")
                continue
            }
            report.test(holder.id().toString(), "resolved-ingredients-and-assembly") {
                check(inputs.all { it.isNotEmpty() }) { "Empty resolved ingredient tag: $inputs" }
                val stacks = recipe.ingredients.map { if (it === Ingredient.EMPTY) ItemStack.EMPTY else it.items.first().copyWithCount(1) }
                val width = if (recipe is ShapedRecipe) recipe.width else 3
                val height = if (recipe is ShapedRecipe) recipe.height else (stacks.size + 2) / 3
                val padded = MutableList(width * height) { index -> stacks.getOrNull(index) ?: ItemStack.EMPTY }
                val input = CraftingInput.of(width, height, padded)
                check(recipe.matches(input, world)) { "Its own displayed grid does not match" }
                val result = recipe.assemble(input, world.registryAccess())
                check(ItemStack.isSameItemSameComponents(output, result) && output.count == result.count)
            }
            if (out in transactionNames && out !in transacted) {
                report.test(out, "survival-crafting-menu-consumes-exact-grid") {
                    check(!player.abilities.instabuild)
                    player.inventory.clearContent()
                    val menu = CraftingMenu(31, player.inventory, ContainerLevelAccess.create(world, table))
                    player.containerMenu = menu
                    val width = if (recipe is ShapedRecipe) recipe.width else 3
                    val height = if (recipe is ShapedRecipe) recipe.height else (recipe.ingredients.size + 2) / 3
                    val paid = mutableListOf<String>()
                    for (y in 0 until height) for (x in 0 until width) {
                        val ingredient = recipe.ingredients.getOrNull(y * width + x) ?: Ingredient.EMPTY
                        if (ingredient !== Ingredient.EMPTY) {
                            check(ingredient.items.isNotEmpty())
                            val stack = ingredient.items.first().copyWithCount(1)
                            paid.add(key(stack)); menu.getSlot(1 + y * 3 + x).set(stack)
                        }
                    }
                    menu.slotsChanged(menu.getSlot(1).container)
                    check(ItemStack.isSameItem(menu.getSlot(0).item, output)) { "No real menu output for $out" }
                    menu.clicked(0, 0, ClickType.PICKUP, player)
                    check(ItemStack.isSameItem(menu.carried, output) && menu.carried.count == output.count)
                    check((1..9).all { menu.getSlot(it).item.isEmpty || menu.getSlot(it).item.item == Items.BUCKET }) { "Inputs were not consumed" }
                    transactions.add(mapOf("output" to out, "count" to output.count, "consumed" to paid, "gameMode" to "SURVIVAL", "fixtureInputs" to true))
                    menu.carried = ItemStack.EMPTY
                    player.closeContainer()
                    transacted.add(out)
                }
            }
        }
        for (holder in world.recipeManager.getAllRecipesFor(RecipeType.SMELTING)) {
            val r = holder.value(); val output = r.getResultItem(world.registryAccess())
            if (!output.isEmpty) rules.add(Rule(key(output), r.ingredients.map { it.items.map(::key) } + listOf(listOf("minecraft:furnace")), holder.id().toString()))
        }
        for (list in RecipesList.listOfList) for (recipe in list.recipes) {
            if (recipe.input.isEmpty || recipe.machineList.isEmpty()) continue
            for (out in recipe.output.filterNot { it.isEmpty }) rules.add(Rule(key(out), listOf(listOf(key(recipe.input)), recipe.machineList.map(::key)), "machine:${recipe.energy}J"))
        }
        for (step in WireProductionRecipes.steps()) rules.add(Rule(key(step.output), (step.inputs + step.catalysts + step.machine).map { listOf(key(it)) }, "wire:${step.kind}"))
        rules.add(Rule("eln:tree_resin", listOf(listOf("eln:tree_resin_collector"), listOf("minecraft:spruce_log"), listOf("minecraft:spruce_leaves")), "tree collector: requires tree and elapsed time"))
        val technical = setOf("air", "bedrock", "barrier", "command_block", "chain_command_block", "repeating_command_block", "structure_block", "structure_void", "jigsaw", "spawner", "debug_stick", "light", "knowledge_book", "command_block_minecart", "end_portal_frame", "reinforced_deepslate", "budding_amethyst")
        val reached = BuiltInRegistries.ITEM.keySet().filter { it.namespace == "minecraft" && it.path !in technical && !it.path.endsWith("_spawn_egg") }.map { it.toString() }.toMutableSet()
        reached.addAll(listOf("eln:copper_ore", "eln:lead_ore", "eln:tungsten_ore", "eln:cinnabar_ore"))
        val witnesses = linkedMapOf<String, Rule>()
        var changed = true
        while (changed) {
            changed = false
            for (r in rules) if (r.output !in reached && r.inputs.all { alternatives -> alternatives.any { it in reached } }) {
                reached.add(r.output); witnesses[r.output] = r; changed = true
            }
        }
        val milestones = transactionNames + setOf("eln:rubber", "eln:48v_macerator", "eln:48v_compressor", "eln:copper_thermal_cable")
        milestones.sorted().forEach { id -> report.test(id, "recipe-prerequisite-reachability") {
            check(id in reached) { "No reachable crafting/smelting/processing path. Rules: ${rules.filter { it.output == id }}" }
        } }
        val registered = BuiltInRegistries.ITEM.keySet().filter { it.namespace == "eln" }.map { it.toString() }.sorted()
        write("survival-recipe-graph.json", mapOf("assumptions" to listOf("Vanilla survival resources treated as available; their mining/time costs are not proven here", "Four naturally generated ELN ores are acquisition roots", "Machine recipes require a reachable machine but network power compatibility is tested separately", "Tree resin requires collector, spruce tree and time", "Wire bundle material/gauge identities stay distinct"), "registeredItems" to registered, "reachable" to registered.filter { it in reached }, "unresolved" to registered.filterNot { it in reached }, "witnesses" to witnesses, "rules" to rules))
        write("survival-crafting-transactions.json", mapOf("scope" to "Real survival-mode menu consumption from explicitly seeded fixture inputs; NOT a no-cheats mining playthrough", "transactions" to transactions))
    }

    private fun fixtures(world: ServerLevel) {
        val player = FakePlayerFactory.getMinecraft(world)
        for ((index, name) in listOf("Wire Roller", "Wire Insulator").withIndex()) {
            val pos = BlockPos(160 + index * 16, 65, 160)
            world.setChunkForced(pos.x shr 4, pos.z shr 4, true)
            world.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState())
            val stack = Eln.findItemStack(name, 1)
            player.setGameMode(GameType.SURVIVAL); player.yRot = 0f; player.xRot = 0f
            player.setItemInHand(InteractionHand.MAIN_HAND, stack)
            check(Eln.transparentNodeItem.placeBlockAt(stack, player, world, pos, net.minecraft.core.Direction.UP))
            val m = machine(world, pos)
            if (name == "Wire Roller") {
                m.targetLengthMeters = 2
                m.inventory.setItem(0, ItemStack(Items.COPPER_INGOT, 2))
                m.inventory.setItem(1, Eln.findItemStack("Iron Roller Wheel", 1))
                m.inventory.setItem(2, Eln.findItemStack("Iron Roller Wheel", 1))
            } else {
                val d = UtilityCableDescriptor.allDescriptors().first { !it.melted && !it.insulated && it.conductorCount == 1 && it.material == UtilityCableMaterial.COPPER }
                m.inventory.setItem(0, WireProductionRecipes.spool(d, 2.0))
                m.inventory.setItem(1, Eln.findItemStack("Rubber", 1))
            }
            fixtures[name] = pos
        }
    }

    private fun breakAndReinstall(world: ServerLevel) {
        val player = FakePlayerFactory.getMinecraft(world)
        for ((name, pos) in fixtures) {
            val m = machine(world, pos)
            val before = if (name == "Wire Roller") m.loadedMassKg else m.insulationMetersBuffer
            val expected = if (name == "Wire Roller") 2.0 else 32.0
            report.test(name, "unpowered-buffer-precondition") {
                check(abs(before - expected) < 1e-8) { "Buffer did not absorb input: $before" }
                check(m.progressMeters == 0.0 && m.inventory.getItem(if (name == "Wire Roller") 3 else 2).isEmpty)
            }
            player.teleportTo(pos.x + 2.0, pos.y.toDouble(), pos.z + 2.0)
            player.setGameMode(GameType.SURVIVAL)
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.DIAMOND_PICKAXE))
            check(player.gameMode.destroyBlock(pos)) { "Survival block break failed" }
            val drops = world.getEntitiesOfClass(ItemEntity::class.java, AABB(pos).inflate(4.0)).map { it.item.copy() }
            val block = Eln.findItemStack(name, 1)
            val blockDrops = drops.filter { ItemStack.isSameItem(it, block) }
            val refunded = drops.filter { ItemStack.isSameItem(it, if (name == "Wire Roller") ItemStack(Items.COPPER_INGOT) else Eln.findItemStack("Rubber", 1)) }.sumOf { it.count }
            report.test(name, "normal-break-one-machine-no-node") {
                check(blockDrops.sumOf { it.count } == 1) { "Machine drops: $drops" }
                check(NodeManager.instance!!.getNodeFromCoordonate(Coordinate(pos.x, pos.y, pos.z, world)) == null)
                if (name == "Wire Roller") {
                    check(drops.filter { ItemStack.isSameItem(it, Eln.findItemStack("Iron Roller Wheel", 1)) }.sumOf { it.count } == 2)
                } else {
                    val wire = drops.single { Eln.sixNodeItem.getDescriptor(it) is UtilityCableDescriptor }
                    check((Eln.sixNodeItem.getDescriptor(wire) as UtilityCableDescriptor).getRemainingLengthMeters(wire) == 2.0)
                }
            }
            var restored = Double.NaN
            if (blockDrops.size == 1) {
                val stack = blockDrops.single().copy()
                player.setItemInHand(InteractionHand.MAIN_HAND, stack)
                check(Eln.transparentNodeItem.placeBlockAt(stack, player, world, pos, net.minecraft.core.Direction.UP))
                val installed = machine(world, pos)
                restored = if (name == "Wire Roller") installed.loadedMassKg else installed.insulationMetersBuffer
                check((0 until installed.inventory.containerSize).all { installed.inventory.getItem(it).isEmpty }) {
                    "Dropped inventory was also copied into the reinstalled machine"
                }
            }
            write("relocation-${if (name == "Wire Roller") "roller" else "insulator"}.json", mapOf("beforeBuffer" to before, "afterBuffer" to restored, "refundedItems" to refunded, "drops" to drops.map { mapOf("id" to key(it), "count" to it.count, "components" to it.componentsPatch.toString()) }))
            report.test(name, "relocation-preserves-paid-buffer-or-refunds-input") {
                check(abs(restored + refunded * (if (name == "Wire Roller") 1.0 else 32.0) - before) < 1e-8) {
                    "Paid buffer lost during ordinary survival break/reinstall: before=$before restored=$restored refunded=$refunded"
                }
            }
            report.test(name, "fractional-buffer-item-state-roundtrip-without-inventory-or-work") {
                val installed = machine(world, pos)
                installed.loadedMaterial = UtilityCableMaterial.COPPER
                installed.loadedMassKg = .875
                installed.insulationMetersBuffer = 29.5
                installed.progressMeters = .75
                installed.selectedOption = 2
                installed.targetLengthMeters = 7
                repeat(3) {
                    val tag = installed.getItemStackNBT()
                    check(!tag.contains("inv") && !tag.contains("progressMeters"))
                    installed.loadedMassKg = 0.0; installed.insulationMetersBuffer = 0.0
                    installed.readItemStackNBT(tag)
                    check(installed.loadedMassKg == .875 && installed.loadedMaterial == UtilityCableMaterial.COPPER)
                    check(installed.insulationMetersBuffer == 29.5 && installed.progressMeters == 0.0)
                    check(installed.selectedOption == 2 && installed.targetLengthMeters == 7)
                }
            }
            report.test(name, "repeated-survival-relocation-preserves-fractional-buffer-once") {
                // Previous dropped inventories are accounted above. Clear that fixture's loose
                // entities so subsequent counts refer only to the next actual survival break.
                world.getEntitiesOfClass(ItemEntity::class.java, AABB(pos).inflate(4.0)).forEach { it.discard() }
                repeat(3) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.DIAMOND_PICKAXE))
                    check(player.gameMode.destroyBlock(pos))
                    val entities = world.getEntitiesOfClass(ItemEntity::class.java, AABB(pos).inflate(4.0))
                    check(entities.size == 1 && entities.single().item.count == 1)
                    val carried = entities.single().item.copy()
                    check(ItemStack.isSameItem(carried, block))
                    entities.single().discard()
                    player.setItemInHand(InteractionHand.MAIN_HAND, carried)
                    check(Eln.transparentNodeItem.placeBlockAt(carried, player, world, pos, net.minecraft.core.Direction.UP))
                    carried.shrink(1) // placeBlockAt is the primitive; ordinary item use consumes one.
                    val installed = machine(world, pos)
                    check(installed.loadedMassKg == .875 && installed.loadedMaterial == UtilityCableMaterial.COPPER)
                    check(installed.insulationMetersBuffer == 29.5 && installed.progressMeters == 0.0)
                    check(installed.selectedOption == 2 && installed.targetLengthMeters == 7)
                }
            }
        }
    }

    private fun shutdown(server: MinecraftServer) {
        if (report.failures > 0) {
            val running = server.runningThread
            Thread({ running.join(); System.exit(1) }, "survival-campaign-exit").start()
        }
        server.halt(false)
    }
}
