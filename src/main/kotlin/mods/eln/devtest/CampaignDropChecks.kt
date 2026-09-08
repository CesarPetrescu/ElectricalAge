package mods.eln.devtest

import com.google.gson.Gson
import com.google.gson.JsonParser
import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.TransparentNode
import mods.eln.sixnode.electricalcable.UtilityCableElement
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.phys.AABB
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.nio.file.Files

/** Real destroyBlock transactions. Tests declared item/drop and existing fixture inventory, not every possible stored setting. */
@EventBusSubscriber(modid = Eln.MODID)
object CampaignDropChecks {
    private var ticks = 0
    private val report = ContractReport("survival-descriptor-drops")
    @SubscribeEvent fun tick(event: ServerTickEvent.Post) {
        if (System.getProperty("eln.campaign") != "drops") return
        ticks++
        try {
            val world = event.server.overworld()
            if (ticks == 20) {
                report.write(false)
                FakePlayerFactory.getMinecraft(world).setGameMode(GameType.CREATIVE)
                check(BlockContracts.place(world) == 0)
            }
            if (ticks == 60) {
                val file = event.server.getWorldPath(LevelResource.ROOT).resolve("eln-contracts.json")
                val entries = Files.newBufferedReader(file).use { reader -> JsonParser.parseReader(reader).asJsonArray.map { Gson().fromJson(it, BlockContracts.Entry::class.java) } }
                for (entry in entries) {
                    if (entry.kind !in listOf("six", "transparent")) {
                        report.skip(entry.id, "ordinary-survival-drops", "Native non-node block: requires its own loot expectation")
                        continue
                    }
                    report.test(entry.id, "ordinary-survival-drops") { verify(world, entry) }
                }
                report.write(true)
                stop(event)
            }
        } catch (t: Throwable) {
            report.test("campaign", "unexpected-$ticks") { throw t }
            report.write(false)
            stop(event)
        }
    }

    private fun verify(world: ServerLevel, entry: BlockContracts.Entry) {
        val pos = BlockPos(entry.x, entry.y, entry.z)
        val box = AABB(pos).inflate(4.0)
        // Clear obsolete fixture debris before the transaction, never drops produced by it.
        world.getEntitiesOfClass(ItemEntity::class.java, box).forEach { it.discard() }
        val coordinate = Coordinate(pos.x, pos.y, pos.z, world)
        val node = NodeManager.instance!!.getNodeFromCoordonate(coordinate)
        val declared: ItemStack
        val inventory: Container?
        when (node) {
            is SixNode -> {
                val e = checkNotNull(node.getElement(checkNotNull(Direction.fromInt(entry.side))))
                // Utility wires intentionally yield typed scrap, not reusable cable. This is an explicit
                // documented transformation, not a loss of the still-paid wire-machine buffers tested separately.
                declared = if (e is UtilityCableElement) checkNotNull(Eln.instance.wireScrapDescriptor).createScrapStack(e.descriptor) else e.dropItemStack.copy()
                inventory = e.inventory
            }
            is TransparentNode -> {
                val e = checkNotNull(node.element)
                declared = e.dropItemStack.copy(); inventory = e.inventory
            }
            else -> error("Missing initialized fixture ${entry.id}")
        }
        check(!declared.isEmpty)
        val expectedInventory = if (inventory == null) emptyList() else (0 until inventory.containerSize).map { inventory.getItem(it).copy() }.filterNot { it.isEmpty }
        val player = FakePlayerFactory.getMinecraft(world)
        player.setGameMode(GameType.SURVIVAL)
        player.teleportTo(pos.x + 2.0, pos.y + 1.0, pos.z + 2.0)
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.DIAMOND_PICKAXE))
        check(!player.abilities.instabuild && player.gameMode.destroyBlock(pos)) { "Normal survival break failed" }
        val drops = world.getEntitiesOfClass(ItemEntity::class.java, box).map { it.item.copy() }
        fun count(stack: ItemStack, list: List<ItemStack>) = list.filter { ItemStack.isSameItemSameComponents(it, stack) }.sumOf { it.count }
        val expected = expectedInventory + declared
        for (stack in expected) check(count(stack, drops) == count(stack, expected)) {
            "Declared item/fixture-inventory mismatch: expected=$expected dropped=$drops"
        }
        check(drops.sumOf { it.count } == expected.sumOf { it.count }) { "Unexpected extra drops: expected=$expected dropped=$drops" }
        check(world.isEmptyBlock(pos) && NodeManager.instance!!.getNodeFromCoordonate(coordinate) == null) { "Node remained after normal break" }
        check(BlockPos.betweenClosed(pos.offset(-4, -3, -4), pos.offset(4, 4, 4)).none { world.getBlockState(it).block == Eln.ghostBlock }) { "Orphan multiblock parts" }
    }

    private fun stop(event: ServerTickEvent.Post) {
        if (report.failures > 0) {
            val thread = event.server.runningThread
            Thread({ thread.join(); System.exit(1) }, "drop-campaign-exit").start()
        }
        event.server.halt(false)
    }
}
