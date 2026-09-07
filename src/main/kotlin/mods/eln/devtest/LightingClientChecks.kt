package mods.eln.devtest

import mods.eln.client.NodeParticles
import mods.eln.node.NodeBlock
import mods.eln.registration.ElnRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.core.Direction
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions

/** Runs with a real stitched texture atlas, not an existence-only file check. */
object LightingClientChecks {
    fun checkWiredFixture(mc: Minecraft, entry: BlockContracts.Entry) {
        if (entry.kind != "six") return
        val pos = BlockPos(entry.x, entry.y, entry.z)
        val side = mods.eln.misc.Direction.fromInt(entry.side)!!
        val lamp = (mc.level!!.getBlockEntity(pos) as? mods.eln.node.six.SixNodeEntity)
            ?.elementRenderList?.get(side.int) as? mods.eln.sixnode.lampsocket.LampSocketRender
            ?: error("Lamp renderer is not synchronized")
        for (port in mods.eln.misc.LRDU.entries) {
            if (!lamp.descriptor.renderSideCables && port != lamp.front && port != lamp.front!!.inverse()) continue
            check(lamp.connectedSide[port]) { "Lamp port $port is disconnected" }
            val neighbour = pos.relative(side.applyLRDU(port).toFacing())
            val cable = (mc.level!!.getBlockEntity(neighbour) as? mods.eln.node.six.SixNodeEntity)
                ?.elementRenderList?.get(side.int)
            check(cable is mods.eln.sixnode.electricalcable.ElectricalCableRender) {
                "Cable renderer missing at $neighbour: ${mc.level!!.getBlockState(neighbour)}"
            }
            check(cable.glListReady) { "Cable geometry has not rendered at $neighbour" }
        }
    }

    @JvmStatic fun checkAssets(mc: Minecraft): Int {
        val report = ContractReport("lighting-client")
        val level = mc.level!!
        val emptyPos = BlockPos(0, level.maxBuildHeight + 8, 0)
        for ((id, block) in ElnRegistry.registeredBlocks) {
            if (block !is NodeBlock && (block is LiquidBlock || block.defaultBlockState().renderShape != RenderShape.MODEL)) continue
            report.test(id.toString(), "particle-texture") {
                val model = mc.blockRenderer.blockModelShaper.getBlockModel(block.defaultBlockState())
                check(model.particleIcon.contents().name() != MissingTextureAtlasSprite.getLocation()) { "Missing baked particle texture" }
                if (block is NodeBlock) {
                    check(IClientBlockExtensions.of(block) === NodeParticles) { "BER particle hooks are not registered" }
                    check(NodeParticles.sprite(level, emptyPos, null).contents().name() != MissingTextureAtlasSprite.getLocation())
                    // A removed BE is normal during break events, including destruction by other players.
                    val hit = BlockHitResult(Vec3.atCenterOf(emptyPos), Direction.UP, emptyPos, false)
                    check(NodeParticles.addHitEffects(block.defaultBlockState(), level, hit, mc.particleEngine))
                    check(NodeParticles.addDestroyEffects(block.defaultBlockState(), level, emptyPos, mc.particleEngine))
                }
            }
        }
        report.write(true)
        return report.failures
    }
}
