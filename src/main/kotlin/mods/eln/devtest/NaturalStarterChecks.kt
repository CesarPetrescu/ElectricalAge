package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.sixnode.TreeResinCollector.TreeResinCollectorElement
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.CraftingMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.item.crafting.*
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.nio.file.Files
import java.nio.file.Path

/** Teleport-assisted resource audit; no mining-delay/travel/hunger simulation and no created inventory inputs.
 * Ingredients originate in natural blocks, normal crafting/furnace output, or real timed resin collection.
 * This is not a human-equivalent survival playthrough or proof of powered factory operation. */
@EventBusSubscriber(modid = Eln.MODID)
object NaturalStarterChecks {
    private var tick = 0
    private var stage = 0
    private var finished = false
    private var startNanos = 0L
    private lateinit var world: ServerLevel
    private lateinit var player: ServerPlayer
    private var table: BlockPos? = null
    private var miningTool: String? = null
    private val found = linkedMapOf<String, MutableList<BlockPos>>()
    private val furnaces = mutableListOf<BlockPos>()
    private val felled = mutableSetOf<Pair<Int,Int>>()
    private val collectors = mutableListOf<Pair<BlockPos, mods.eln.misc.Direction>>()
    private val journal = mutableListOf<Map<String, Any>>()
    private val report = ContractReport("natural-survival-starter")
    private val out = Path.of("../../build/smoke-artifacts/contracts")
    private fun id(stack: ItemStack) = BuiltInRegistries.ITEM.getKey(stack.item).toString()
    private fun count(name: String) = (0 until player.inventory.containerSize).sumOf { i -> player.inventory.getItem(i).let { if (id(it) == name) it.count else 0 } }
    private fun inventory() = (0 until player.inventory.containerSize).map { player.inventory.getItem(it) }.filterNot { it.isEmpty }.groupBy(::id).mapValues { (_, stacks) -> stacks.sumOf { it.count } }
    private fun record(action: String, data: Map<String, Any> = emptyMap()) {
        journal.add(mapOf("serverTick" to tick, "elapsedSeconds" to (System.nanoTime() - startNanos) / 1e9, "action" to action, "inventory" to inventory()) + data)
        Files.createDirectories(out)
        Files.writeString(out.resolve("natural-starter-journal.json"), GsonBuilder().setPrettyPrinting().create().toJson(journal))
        println("NATURAL_STARTER $action inventory=${inventory()}")
    }
    private fun checkStep(name: String, block: () -> Unit) {
        var failure: Throwable? = null
        report.test("starter", name) { try { block() } catch (t: Throwable) { failure = t; throw t } }
        if (failure != null) throw IllegalStateException("Starter stopped at $name", failure)
    }
    private fun remove(name: String, amount: Int): ItemStack {
        check(count(name) >= amount) { "Missing actual acquired ingredient: $name x$amount" }
        var left = amount
        var result = ItemStack.EMPTY
        for (i in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(i)
            if (left > 0 && id(stack) == name) {
                val take = player.inventory.removeItem(i, minOf(left, stack.count))
                if (result.isEmpty) result = take else result.grow(take.count)
                left -= take.count
            }
        }
        check(left == 0)
        return result
    }
    private fun receive(stack: ItemStack) { if (!stack.isEmpty) check(player.inventory.add(stack)) { "Inventory full, no silent loss" } }
    private fun hold(name: String?) {
        val inv = player.inventory
        val selected = inv.selected
        if (name == null) {
            if (!inv.getItem(selected).isEmpty) {
                val empty = (0 until 36).firstOrNull { inv.getItem(it).isEmpty } ?: error("No empty slot to put down tool")
                inv.setItem(empty, inv.getItem(selected)); inv.setItem(selected, ItemStack.EMPTY)
            }
            miningTool = null
        } else {
            val index = (0 until 36).firstOrNull { !inv.getItem(it).isEmpty && id(inv.getItem(it)) == name } ?: error("No acquired item to hold: $name")
            if (index != selected) {
                val prior = inv.getItem(selected)
                inv.setItem(selected, inv.getItem(index)); inv.setItem(index, prior)
            }
            miningTool = if (name.endsWith("_pickaxe")) name else null
        }
    }
    private fun collect(pos: BlockPos): List<Map<String, Any>> {
        val collected = mutableListOf<Map<String, Any>>()
        for (drop in world.getEntitiesOfClass(ItemEntity::class.java, AABB(pos).inflate(2.0))) {
            val before = drop.item.copy()
            drop.setNoPickUpDelay()
            drop.playerTouch(player)
            check(!drop.isAlive || drop.item.isEmpty) { "Normal pickup rejected: $before" }
            collected.add(mapOf("id" to id(before), "count" to before.count))
        }
        return collected
    }
    private fun mine(group: String, desiredItem: String?, minimum: Int, blocks: Int = 0) {
        var taken = 0
        for (pos in found[group].orEmpty()) {
            if ((desiredItem != null && count(desiredItem) >= minimum) || (desiredItem == null && taken >= blocks)) break
            if (world.isEmptyBlock(pos)) continue
            val state = world.getBlockState(pos)
            if (player.mainHandItem.isEmpty && miningTool != null) hold(miningTool)
            player.teleportTo(pos.x + 1.0, pos.y + 1.0, pos.z + 1.0)
            check(!player.abilities.instabuild)
            check(player.gameMode.destroyBlock(pos)) { "Survival mining rejected: $state at $pos" }
            if (group == "logs") felled.add(pos.x to pos.z)
            val drops = collect(pos)
            check(drops.isNotEmpty()) { "No harvest with ${player.mainHandItem} from $state" }
            record("mine-natural-block", mapOf("block" to BuiltInRegistries.BLOCK.getKey(state.block).toString(), "position" to listOf(pos.x,pos.y,pos.z), "drops" to drops))
            taken++
        }
        check(if (desiredItem == null) taken >= blocks else count(desiredItem) >= minimum) { "Natural acquisition exhausted $group; this is an acquisition/fixture limit, not automatically a mod defect" }
    }
    private fun selections(recipe: CraftingRecipe, grid: Int): List<ItemStack>? {
        if (recipe !is ShapedRecipe && recipe !is ShapelessRecipe) return null
        val width = if (recipe is ShapedRecipe) recipe.width else grid
        val height = if (recipe is ShapedRecipe) recipe.height else (recipe.ingredients.size + grid - 1) / grid
        if (width > grid || height > grid) return null
        val budget = inventory().toMutableMap()
        val result = mutableListOf<ItemStack>()
        for (ingredient in recipe.ingredients) {
            if (ingredient === Ingredient.EMPTY) { result.add(ItemStack.EMPTY); continue }
            val match = ingredient.items.firstOrNull { (budget[id(it)] ?: 0) > 0 } ?: return null
            val key = id(match); budget[key] = budget.getValue(key) - 1
            // Selection identity only: actual grid inputs are removed from the acquired inventory.
            result.add(match.copyWithCount(1))
        }
        return result
    }
    private fun craft(name: String, minimum: Int) {
        var attempts = 0
        while (count(name) < minimum) {
            check(++attempts <= 128)
            val grid = if (table == null) 2 else 3
            val match = world.recipeManager.getAllRecipesFor(RecipeType.CRAFTING).asSequence().mapNotNull { holder ->
                val r = holder.value(); val output = r.getResultItem(world.registryAccess())
                if (id(output) != name) null else selections(r, grid)?.let { Triple(holder, it, output) }
            }.firstOrNull() ?: error("No payable loaded recipe for $name in ${inventory()}")
            val r = match.first.value()
            val width = if (r is ShapedRecipe) r.width else grid
            table?.let { player.teleportTo(it.x + 1.0, it.y + 1.0, it.z + 1.0) }
            val menu: AbstractContainerMenu = if (table == null) player.inventoryMenu else CraftingMenu(73, player.inventory, ContainerLevelAccess.create(world, table!!))
            player.containerMenu = menu
            for (i in match.second.indices) {
                val selected = match.second[i]
                if (!selected.isEmpty) menu.getSlot(1 + (i / width) * grid + (i % width)).set(remove(id(selected), 1))
            }
            menu.slotsChanged(menu.getSlot(1).container)
            check(ItemStack.isSameItem(menu.getSlot(0).item, match.third)) { "No native craft result for $name" }
            menu.clicked(0, 0, ClickType.PICKUP, player)
            val output = menu.carried.copy()
            check(id(output) == name && output.count == match.third.count)
            menu.carried = ItemStack.EMPTY
            val made = output.count
            receive(output)
            for (i in 1..grid*grid) receive(menu.getSlot(i).remove(Int.MAX_VALUE))
            player.closeContainer()
            record("native-survival-craft", mapOf("recipe" to match.first.id().toString(), "output" to name, "count" to made))
        }
    }
    private fun planks(minimum: Int) {
        fun total() = (0 until player.inventory.containerSize).sumOf { player.inventory.getItem(it).let { s -> if (s.`is`(ItemTags.PLANKS)) s.count else 0 } }
        while (total() < minimum) {
            val holder = world.recipeManager.getAllRecipesFor(RecipeType.CRAFTING).firstOrNull { h ->
                h.value().getResultItem(world.registryAccess()).`is`(ItemTags.PLANKS) && selections(h.value(), 2) != null
            } ?: error("No acquired logs left for planks")
            val name = id(holder.value().getResultItem(world.registryAccess()))
            craft(name, count(name) + 1)
        }
    }
    private fun placeOn(name: String, support: BlockPos, face: Direction): BlockPos {
        hold(name)
        player.teleportTo(support.x + 2.0, support.y + 1.0, support.z + 2.0)
        val target = support.relative(face)
        val held = player.mainHandItem
        val before = held.count
        val hit = BlockHitResult(Vec3.atCenterOf(support).add(face.stepX * .5, face.stepY * .5, face.stepZ * .5), face, support, false)
        val result = held.useOn(UseOnContext(player, InteractionHand.MAIN_HAND, hit))
        check(result.consumesAction() && !world.isEmptyBlock(target) && held.count == before - 1) { "Placement did not consume acquired $name at $target" }
        world.setChunkForced(target.x shr 4, target.z shr 4, true)
        record("place-acquired-item", mapOf("id" to name, "position" to listOf(target.x,target.y,target.z)))
        return target
    }
    private fun floorNear(center: BlockPos, index: Int): BlockPos {
        for (offset in index*3 until index*3+80) {
            val x = center.x + 8 + offset % 16; val z = center.z + 8 + offset / 16 * 3
            val pos = BlockPos(x, world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1, z)
            if (world.getBlockState(pos).isSolidRender(world,pos) && world.getBlockState(pos.above()).canBeReplaced()) return pos
        }
        error("No natural campsite floor")
    }
    private fun scan() {
        val start = world.sharedSpawnPos
        val selected = mapOf<Block,String>(Blocks.STONE to "stone", Blocks.COAL_ORE to "coal", Blocks.DEEPSLATE_COAL_ORE to "coal", Blocks.IRON_ORE to "iron", Blocks.DEEPSLATE_IRON_ORE to "iron", Blocks.COPPER_ORE to "copper", Blocks.DEEPSLATE_COPPER_ORE to "copper", Blocks.REDSTONE_ORE to "redstone", Blocks.DEEPSLATE_REDSTONE_ORE to "redstone", Blocks.SAND to "sand")
        val mutable = BlockPos.MutableBlockPos()
        for (dx in -4..4) for (dz in -4..4) {
            val chunk = world.getChunk((start.x shr 4)+dx,(start.z shr 4)+dz)
            for (x in 0..15) for (z in 0..15) for (y in world.minBuildHeight until minOf(world.maxBuildHeight,180)) {
                mutable.set(chunk.pos.minBlockX+x,y,chunk.pos.minBlockZ+z)
                val state = chunk.getBlockState(mutable)
                val registry = BuiltInRegistries.BLOCK.getKey(state.block)
                val key = selected[state.block] ?: if (state.`is`(BlockTags.LOGS)) "logs" else if (registry.namespace=="eln" && registry.path.endsWith("_ore")) registry.toString() else null
                if (key != null) {
                    val list = found.getOrPut(key) { mutableListOf() }
                    if (list.size < (if(key=="logs")2048 else 256)) list.add(mutable.immutable())
                }
            }
        }
        record("scan-natural-terrain", mapOf("seed" to world.seed,"chunks" to 81,"found" to found.mapValues { it.value.size }))
    }
    private fun queueSmelt(index: Int, input: String, amount: Int, coal: Int) {
        val f = world.getBlockEntity(furnaces[index]) as AbstractFurnaceBlockEntity
        check(f.getItem(0).isEmpty && f.getItem(2).isEmpty)
        f.setItem(0, remove(input, amount)); if (coal > 0) f.setItem(1,remove("minecraft:coal",coal))
        f.setChanged()
        record("queue-real-furnace", mapOf("input" to input,"amount" to amount,"furnace" to index))
    }
    private fun harvestFurnaces(): Boolean {
        for (pos in furnaces) {
            val f = world.getBlockEntity(pos) as AbstractFurnaceBlockEntity
            if (!f.getItem(2).isEmpty) receive(f.removeItem(2,Int.MAX_VALUE))
        }
        return furnaces.all { (world.getBlockEntity(it) as AbstractFurnaceBlockEntity).getItem(0).isEmpty }
    }
    private fun setupCollectors() {
        craft("eln:tree_resin_collector",16)
        val used = mutableSetOf<Pair<Int,Int>>()
        for (log in found["logs"].orEmpty()) {
            if (collectors.size==16) break
            if ((log.x to log.z) in used || (log.x to log.z) in felled || !world.getBlockState(log).`is`(BlockTags.LOGS) || world.getBlockState(log.below()).`is`(BlockTags.LOGS)) continue
            var height = 1
            while (height < 40 && world.getBlockState(log.above(height)).`is`(BlockTags.LOGS)) height++
            if (height < 4) continue
            for (face in Direction.Plane.HORIZONTAL) {
                if (!world.getBlockState(log.relative(face)).canBeReplaced()) continue
                if (!(1 until height).any { world.getBlockState(log.above(it).relative(face)).`is`(BlockTags.LEAVES) }) continue
                val pos = placeOn("eln:tree_resin_collector",log,face)
                val side = mods.eln.misc.Direction.fromFacing(face).inverse
                check((NodeManager.instance!!.getNodeFromCoordonate(Coordinate(pos.x,pos.y,pos.z,world)) as SixNode).getElement(side) is TreeResinCollectorElement)
                collectors.add(pos to side); used.add(log.x to log.z); break
            }
        }
        check(collectors.size>=8) { "Natural forest provided only ${collectors.size} suitable collector sites" }
        record("timed-natural-resin-start",mapOf("collectors" to collectors.size))
    }
    private fun harvestResin() {
        for ((pos, side) in collectors) {
            val e = (NodeManager.instance!!.getNodeFromCoordonate(Coordinate(pos.x,pos.y,pos.z,world)) as SixNode).getElement(side) as TreeResinCollectorElement
            player.teleportTo(pos.x+1.0,pos.y+1.0,pos.z+1.0)
            e.onBlockActivated(player,side,.5f,.5f,.5f)
            collect(pos)
        }
    }
    @SubscribeEvent fun tick(event: ServerTickEvent.Post) {
        if (System.getProperty("eln.campaign")!="nature" || finished) return
        tick++
        try {
            if (tick==20) {
                startNanos=System.nanoTime(); world=event.server.overworld(); player=FakePlayerFactory.getMinecraft(world)
                report.write(false)
                checkStep("empty-survival-inventory") { player.setGameMode(GameType.SURVIVAL);check(inventory().isEmpty());check(!player.abilities.instabuild) }
                checkStep("natural-terrain-acquisition-and-tool-progression") {
                    scan();mine("logs",null,0,blocks=48);planks(144)
                    craft("minecraft:crafting_table",1)
                    val camp=found.getValue("logs").first()
                    table=placeOn("minecraft:crafting_table",floorNear(camp,0),Direction.UP)
                    craft("minecraft:stick",16);craft("minecraft:wooden_pickaxe",1);hold("minecraft:wooden_pickaxe")
                    mine("stone","minecraft:cobblestone",12)
                    craft("minecraft:stone_pickaxe",3);hold("minecraft:stone_pickaxe")
                    mine("stone","minecraft:cobblestone",160);mine("coal","minecraft:coal",48);mine("iron","minecraft:raw_iron",40);mine("copper","minecraft:raw_copper",30)
                    craft("minecraft:furnace",8)
                    repeat(8) { furnaces.add(placeOn("minecraft:furnace",floorNear(camp,it+1),Direction.UP)) }
                    queueSmelt(0,"minecraft:raw_iron",20,4);queueSmelt(1,"minecraft:raw_iron",20,4)
                    queueSmelt(2,"minecraft:raw_copper",30,5);queueSmelt(3,"minecraft:cobblestone",24,4)
                    hold(null);mine("sand","minecraft:sand",12);queueSmelt(4,"minecraft:sand",12,2)
                    setupCollectors()
                }
                stage=1
            }
            if(tick%200==0 && stage==1 && harvestFurnaces()) {
                checkStep("native-furnace-output-and-natural-eln-ores") {
                    check(count("minecraft:iron_ingot")>=40 && count("minecraft:copper_ingot")>=30 && count("minecraft:glass")>=12 && count("minecraft:stone")>=24)
                    craft("minecraft:iron_pickaxe",1);hold("minecraft:iron_pickaxe")
                    for(ore in listOf("eln:copper_ore","eln:lead_ore","eln:tungsten_ore","eln:cinnabar_ore")) mine(ore,ore,if(ore=="eln:lead_ore")6 else 1)
                    mine("redstone","minecraft:redstone",8)
                    queueSmelt(5,"eln:lead_ore",6,1)
                    craft("eln:copper_cable",48);craft("eln:iron_cable",36);craft("eln:copper_thermal_cable",12)
                    craft("minecraft:glass_pane",16);craft("eln:simple_lamp_socket",3)
                    record("starter-cables-and-lamp-crafted")
                }
                stage=2
            }
            if(tick%200==0 && stage==2) {
                harvestFurnaces();harvestResin()
                if(count("eln:tree_resin")>=24) {
                    checkStep("real-time-tree-resin") { check(!player.abilities.instabuild);record("timed-resin-harvest-complete") }
                    queueSmelt(6,"eln:tree_resin",12,2)
                    stage=3
                }
            }
            if(tick%200==0 && stage==3 && harvestFurnaces()) {
                checkStep("payable-eln-power-and-wire-machinery") {
                    craft("eln:low_voltage_cable",16);craft("eln:machine_block",3);craft("eln:electrical_motor",2)
                    craft("eln:combustion_chamber",1);craft("eln:stone_heat_furnace",1);craft("eln:48v_turbine",1)
                    craft("eln:cost_oriented_battery",1);craft("eln:iron_roller_wheel",2);craft("minecraft:bucket",2)
                    craft("eln:wire_roller",1);craft("eln:wire_insulator",1)
                    record("natural-resource-crafting-chain-complete",mapOf("qualification" to "Items crafted from gathered resources; powered installation, travel, hunger and mining delay are outside this test"))
                }
                report.write(true);finished=true;event.server.halt(false)
            }
            if(tick>48000) error("Forty-minute starter deadline at stage $stage, inventory=${inventory()}")
        } catch(t:Throwable) {
            report.test("starter","fatal-stage-$stage") { throw t }; report.write(false)
            if(::player.isInitialized) record("failed",mapOf("error" to t.toString()))
            finished=true
            val running=event.server.runningThread;Thread({running.join();System.exit(1)},"natural-starter-exit").start();event.server.halt(false)
        }
    }
}
