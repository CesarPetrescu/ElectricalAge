package mods.eln.client

import mods.eln.misc.Direction
import mods.eln.node.six.SixNodeBlock
import mods.eln.node.six.SixNodeEntity
import mods.eln.node.transparent.TransparentNodeEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.ParticleEngine
import net.minecraft.client.particle.TerrainParticle
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions

/** BER-only blocks are INVISIBLE to vanilla's particle engine, but are not invisible machines. */
object NodeParticles : IClientBlockExtensions {
    val fallbackTexture: ResourceLocation = ResourceLocation.withDefaultNamespace("block/iron_block")

    private class Debris(level: ClientLevel, x: Double, y: Double, z: Double,
                         dx: Double, dy: Double, dz: Double, state: BlockState, pos: BlockPos,
                         texture: TextureAtlasSprite) : TerrainParticle(level, x, y, z, dx, dy, dz, state, pos) {
        init { setSprite(texture) }
    }

    internal fun sprite(level: Level, pos: BlockPos, hit: BlockHitResult?) = run {
        val entity = level.getBlockEntity(pos)
        val icon = when (entity) {
            is TransparentNodeEntity -> entity.elementRender?.transparentNodeDescriptor?.iconName
            is SixNodeEntity -> {
                val side = hit?.let {
                    (entity.blockState.block as? SixNodeBlock)?.elementSide(Direction.fromFacing(it.direction),
                        (it.location.x - pos.x).toFloat(), (it.location.y - pos.y).toFloat(),
                        (it.location.z - pos.z).toFloat(), entity::getSyncronizedSideEnable)
                }
                (side?.let { entity.elementRenderList[it.int] }
                    ?: entity.elementRenderList.firstOrNull { it != null })?.sixNodeDescriptor?.iconName
            }
            else -> null
        }
        val atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
        val candidate = icon?.let { atlas.apply(ResourceLocation.fromNamespaceAndPath("eln", "blocks/$it")) }
        if (candidate == null || candidate.contents().name() == MissingTextureAtlasSprite.getLocation())
            atlas.apply(fallbackTexture) else candidate
    }

    override fun addHitEffects(state: BlockState, level: Level, target: HitResult, manager: ParticleEngine): Boolean {
        val hit = target as? BlockHitResult ?: return true
        val clientLevel = level as? ClientLevel ?: return true
        val location = hit.location.relative(hit.direction, 0.02)
        val particle = Debris(clientLevel, location.x, location.y, location.z, 0.0, 0.0, 0.0, state, hit.blockPos,
            sprite(level, hit.blockPos, hit))
        manager.add(particle.setPower(0.2f).scale(0.6f))
        return true
    }

    override fun addDestroyEffects(state: BlockState, level: Level, pos: BlockPos, manager: ParticleEngine): Boolean {
        val clientLevel = level as? ClientLevel ?: return true
        val hit = (Minecraft.getInstance().hitResult as? BlockHitResult)?.takeIf { it.blockPos == pos }
        val texture = sprite(level, pos, hit)
        // The BE may already be removed by the time the server's break event arrives.
        // Never query its dynamic shape here; always provide a real fallback sprite.
        for (x in 0..3) for (y in 0..3) for (z in 0..3) {
            val dx = (x + 0.5) / 4
            val dy = (y + 0.5) / 4
            val dz = (z + 0.5) / 4
            val particle = Debris(clientLevel, pos.x + dx, pos.y + dy, pos.z + dz,
                dx - 0.5, dy - 0.5, dz - 0.5, state, pos, texture)
            manager.add(particle)
        }
        return true
    }

    override fun areBreakingParticlesTinted(state: BlockState, level: ClientLevel, pos: BlockPos) = false
}
