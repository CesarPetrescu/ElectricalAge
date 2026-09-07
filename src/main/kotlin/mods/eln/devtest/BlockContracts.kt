package mods.eln.devtest

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import mods.eln.Eln
import mods.eln.generic.GenericItemBlockUsingDamageDescriptor
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction as ElnDirection
import mods.eln.node.NodeBlock
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeDescriptor
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeDescriptor
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.util.FakePlayerFactory
import java.nio.file.Files

/** Enumerated placement contracts, independent of handwritten electrical behavior scenarios. */
object BlockContracts {
    data class Entry(val id: String, val kind: String, val descriptor: Int, val x: Int, val y: Int, val z: Int, val side: Int, val yaw: Float = 0f, val pitch: Float = 0f, val front: Int = -1)
    private data class Mount(val face: Direction, val yaw: Float = 0f, val pitch: Float = 0f, val label: String = face.name.lowercase())
    private val entries = mutableListOf<Entry>()
    private fun descriptors(): List<GenericItemBlockUsingDamageDescriptor> =
        (Eln.sixNodeItem.subItemList.values + Eln.transparentNodeItem.subItemList.values).filterNotNull().sortedBy { id(it) }
    private fun id(d: GenericItemBlockUsingDamageDescriptor) = BuiltInRegistries.ITEM.getKey(d.parentItem).toString()
    private fun coordinate(world: ServerLevel, p: BlockPos) = Coordinate(p.x, p.y, p.z, world)
    private fun node(world: ServerLevel, p: BlockPos) = NodeManager.instance?.getNodeFromCoordonate(coordinate(world, p))
    private fun identity(world: ServerLevel, entry: Entry): Boolean {
        val p = BlockPos(entry.x, entry.y, entry.z)
        return when (entry.kind) {
            "six" -> (node(world, p) as? SixNode)?.sideElementIdList?.get(entry.side) == entry.descriptor && world.getBlockEntity(p) != null
            "transparent" -> (node(world, p) as? TransparentNode)?.let {
                // Floodlights keep the aim heading separate from their mounting/swivel axis.
                val element = it.element
                val facing = if (element is mods.eln.transparentnode.floodlight.FloodlightElement)
                    element.blockFacing.toStandardDirection() else element?.front
                it.elementId == entry.descriptor && facing?.int == entry.front && world.getBlockEntity(p) != null
            } == true
            else -> BuiltInRegistries.BLOCK.getKey(world.getBlockState(p).block).toString() == entry.id &&
                (world.getBlockState(p).block !is net.minecraft.world.level.block.EntityBlock || world.getBlockEntity(p) != null)
        }
    }
    private fun manifest(world: ServerLevel) = world.server.getWorldPath(LevelResource.ROOT).resolve("eln-contracts.json")

    @JvmStatic fun place(world: ServerLevel): Int {
        entries.clear()
        val report = ContractReport("blocks-place")
        report.write(false)
        val player = FakePlayerFactory.getMinecraft(world)
        fun clear(center: BlockPos) {
            // Dedicated fixture cell only; neighboring test cells are ten blocks apart.
            for (p in BlockPos.betweenClosed(center.offset(-4, -3, -4), center.offset(4, 4, 4))) {
                if (!world.isEmptyBlock(p)) world.removeBlock(p, false)
            }
        }
        var index = 0
        fun cell(): BlockPos {
            val p = BlockPos(768 + (index % 16) * 10, 80, 512 - (index / 16) * 10)
            index++
            for (x in (p.x - 4 shr 4)..(p.x + 4 shr 4)) for (z in (p.z - 4 shr 4)..(p.z + 4 shr 4)) world.setChunkForced(x, z, true)
            return p
        }
        for (d in descriptors()) {
            val key = id(d)
            val origin = cell()
            var retained: Entry? = null
            // Each supported mounting face is exercised, then one representative is retained for restart.
            val mounts = when {
                d is SixNodeDescriptor -> Direction.values().map { Mount(it) }
                d is mods.eln.transparentnode.floodlight.FloodlightDescriptor -> Direction.values().map { Mount(it) }
                (d as TransparentNodeDescriptor).frontType == TransparentNode.FrontType.PlayerViewHorizontal ->
                    listOf(0f, 90f, 180f, 270f).map { Mount(Direction.UP, it, label = "yaw-$it") }
                d.frontType == TransparentNode.FrontType.PlayerView ->
                    listOf(0f, 90f, 180f, 270f).map { Mount(Direction.UP, it, label = "yaw-$it") } +
                        listOf(Mount(Direction.UP, pitch = 90f, label = "look-down"), Mount(Direction.UP, pitch = -90f, label = "look-up"))
                else -> Direction.values().map { Mount(it) }
            }
            for (mount in mounts) {
                val face = mount.face
                clear(origin)
                player.yRot = mount.yaw; player.yHeadRot = mount.yaw; player.xRot = mount.pitch
                player.isShiftKeyDown = false
                var p = origin
                var front = ElnDirection.XN
                if (d is TransparentNodeDescriptor) {
                    front = d.getFrontFromPlace(ElnDirection.fromFacing(face).inverse, player)!!
                    val delta = intArrayOf(d.spawnDeltaX, d.spawnDeltaY, d.spawnDeltaZ)
                    front.rotateFromXN(delta)
                    p = origin.offset(delta[0], delta[1], delta[2])
                    if (d.mustHaveFloor()) world.setBlockAndUpdate(p.below(), Blocks.STONE.defaultBlockState())
                    if (d.mustHaveCeiling()) world.setBlockAndUpdate(p.above(), Blocks.STONE.defaultBlockState())
                    if (d.mustHaveWall() || d.mustHaveWallFrontInverse()) world.setBlockAndUpdate(p.relative(front.inverse.toFacing()), Blocks.STONE.defaultBlockState())
                } else {
                    val support = origin.relative(face.opposite)
                    world.setBlockAndUpdate(support, Blocks.OAK_LOG.defaultBlockState())
                    if (!(d as SixNodeDescriptor).canBePlacedOnSide(player, coordinate(world, support), ElnDirection.fromFacing(face).inverse)) {
                        report.skip(key, "place/${mount.label}", "Descriptor declares this mounting face unsupported")
                        continue
                    }
                }
                val entry = Entry(key, if (d is SixNodeDescriptor) "six" else "transparent", d.parentItemDamage, p.x, p.y, p.z, ElnDirection.fromFacing(face).inverse.int, mount.yaw, mount.pitch, front.int)
                val ok = report.test(key, "place/${mount.label}") {
                    val stack = d.newItemStack(1)
                    check(!stack.isEmpty) { "No item stack for descriptor" }
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack)
                    if (d is SixNodeDescriptor) Eln.sixNodeItem.onItemUse(stack, player, world, origin, InteractionHand.MAIN_HAND, face, .5f, .5f, .5f)
                    else check(Eln.transparentNodeItem.placeBlockAt(stack, player, world, origin, face)) { "Placement rejected for ${d.name}" }
                    check(identity(world, entry)) { "Wrong or missing descriptor/entity at $p (${d.name})" }
                }
                if (ok) {
                    if (d is mods.eln.transparentnode.battery.BatteryDescriptor) {
                        report.test(key, "battery-initial-state/${mount.label}") {
                            PowerBehaviorChecks.checkPlaced(node(world, p))
                        }
                    }
                    if (d is mods.eln.sixnode.lampsocket.LampSocketDescriptor || d is mods.eln.transparentnode.floodlight.FloodlightDescriptor) {
                        report.test(key, "lighting/${mount.label}") {
                            LightingChecks.check(node(world, p), player, ElnDirection.fromFacing(face).inverse)
                        }
                    }
                    report.test(key, "remove/${mount.label}") {
                        world.removeBlock(p, false)
                        check(node(world, p) == null && world.isEmptyBlock(p)) { "Node or block left behind after removal" }
                        check(BlockPos.betweenClosed(origin.offset(-4, -3, -4), origin.offset(4, 4, 4)).none {
                            world.getBlockState(it).block == Eln.ghostBlock
                        }) { "Multiblock ghost cell left behind after removing its root" }
                    }
                    retained = entry
                }
            }
            // Place the last successful supported face again and retain its exact identity.
            if (retained != null) {
                val face = ElnDirection.fromInt(retained.side)!!.inverse.toFacing()
                player.yRot = retained.yaw; player.yHeadRot = retained.yaw; player.xRot = retained.pitch
                clear(origin)
                val p = BlockPos(retained.x, retained.y, retained.z)
                if (d is SixNodeDescriptor) world.setBlockAndUpdate(origin.relative(face.opposite), Blocks.OAK_LOG.defaultBlockState())
                else if (d is TransparentNodeDescriptor) {
                    if (d.mustHaveFloor()) world.setBlockAndUpdate(p.below(), Blocks.STONE.defaultBlockState())
                    if (d.mustHaveCeiling()) world.setBlockAndUpdate(p.above(), Blocks.STONE.defaultBlockState())
                    val front = d.getFrontFromPlace(ElnDirection.fromFacing(face).inverse, player)!!
                    if (d.mustHaveWall() || d.mustHaveWallFrontInverse()) world.setBlockAndUpdate(p.relative(front.inverse.toFacing()), Blocks.STONE.defaultBlockState())
                }
                report.test(key, "retain-for-restart") {
                    val stack = d.newItemStack(1); player.setItemInHand(InteractionHand.MAIN_HAND, stack)
                    if (d is SixNodeDescriptor) Eln.sixNodeItem.onItemUse(stack, player, world, origin, InteractionHand.MAIN_HAND, face, .5f, .5f, .5f)
                    else check(Eln.transparentNodeItem.placeBlockAt(stack, player, world, origin, face))
                    check(identity(world, retained))
                }
                entries.add(retained)
            } else report.test(key, "coverage") { error("No supported placement succeeded") }
            report.skip(key, "family-behavior", "Generic contract only; see named behavior scenarios in smoke logs")
            report.skip(key, "survival-drops-and-inventory", "Removal is tested; exact survival drops and stored inventory contents are not yet asserted")
            report.skip(key, "settings-and-chunk-reload", "Restart identity is tested; per-descriptor settings and chunk-only unload/reload are not yet asserted")
        }
        for (block in BuiltInRegistries.BLOCK.filter { BuiltInRegistries.BLOCK.getKey(it).namespace == "eln" }) {
            val key = BuiltInRegistries.BLOCK.getKey(block).toString()
            if (key == "eln:lightblock") {
                report.skip(key, "block-item", "Transient lamp-created light; checked by the powered lamp scenario, not direct item placement")
                continue
            }
            if (block is NodeBlock) { report.skip(key, "block-item", "Node host is exercised through its individual descriptor items"); continue }
            val item = block.asItem()
            if (item !is BlockItem) {
                if (key in setOf("eln:hot_water", "eln:cold_water")) report.skip(key, "block-item", "Internal fluid has no player-placeable BlockItem; fluid lifecycle coverage pending")
                else report.test(key, "block-item") { error("New block has no placement contract or explicit exemption") }
                continue
            }
            val p = cell()
            clear(p); world.setBlockAndUpdate(p.below(), Blocks.STONE.defaultBlockState())
            val entry = Entry(key, "block", 0, p.x, p.y, p.z, 0)
            report.test(key, "place") {
                val stack = ItemStack(item); player.setItemInHand(InteractionHand.MAIN_HAND, stack)
                check(item.place(BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, BlockHitResult(Vec3.atCenterOf(p.below()), Direction.UP, p.below(), false))).consumesAction())
                check(identity(world, entry))
            }
            entries.add(entry)
            report.skip(key, "full-lifecycle", "Native BlockItem placement and restart identity only; orientations, drops, settings and behavior require dedicated fixtures")
        }
        Files.writeString(manifest(world), GsonBuilder().setPrettyPrinting().create().toJson(entries))
        report.write(true)
        return report.failures
    }

    @JvmStatic fun verify(world: ServerLevel, restart: Boolean): Int {
        val report = ContractReport(if (restart) "blocks-restart" else "blocks-settled")
        report.write(false)
        report.test("registry", "manifest-present") { check(Files.isRegularFile(manifest(world))) }
        if (Files.isRegularFile(manifest(world))) {
            val array = Files.newBufferedReader(manifest(world)).use { JsonParser.parseReader(it).asJsonArray }
            val saved = array.map { GsonBuilder().create().fromJson(it, Entry::class.java) }
            val ids = saved.map { it.id }.toSet()
            for (d in descriptors()) report.test(id(d), "coverage") { check(id(d) in ids) { "Registered descriptor lacks a retained test fixture" } }
            for (entry in saved) report.test(entry.id, if (restart) "restart-identity" else "settled-identity") {
                check(identity(world, entry)) { "Descriptor or block identity missing after ticking/restart at ${entry.x},${entry.y},${entry.z}" }
            }
        }
        report.write(true)
        return report.failures
    }

    @JvmStatic fun hasManifest(world: ServerLevel) = Files.isRegularFile(manifest(world))
}
