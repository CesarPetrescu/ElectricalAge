package mods.eln.node

import net.minecraft.core.Direction
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3

/**
 * What the two node renderers share. 1.7.10 drew node tile entities out to 4096 * 16 blocks
 * (`getMaxRenderDistanceSquared`); 1.21 asks the renderer for a view distance and a per-entity
 * check, so the node's own render box (the multiblock machines reach outside their block) is
 * what decides, out to the client's render distance.
 */
object NodeRenderSupport {
    @JvmStatic
    fun viewDistance(): Int = 256

    @JvmStatic
    fun shouldRender(entity: BlockEntity, cameraPos: Vec3, viewDistance: Int): Boolean =
        Vec3.atCenterOf(entity.blockPos).closerThan(cameraPos, viewDistance.toDouble())

    /**
     * The light to draw a node in. 1.7.10's NodeBlock set `useNeighborBrightness`, so a tile
     * entity was lit by the brightest of its six neighbours; the dispatcher's light is the block's
     * own, which inside a machine that blocks light is next to none. Sky and block light are taken
     * separately, and the node's own emission counts, as the dispatcher's lookup does.
     */
    @JvmStatic
    fun neighbourLight(entity: BlockEntity): Int {
        val level = entity.level ?: return 15 shl 4 or (15 shl 20)
        val pos = entity.blockPos
        var sky = 0
        var block = level.getBlockState(pos).getLightEmission(level, pos)
        for (direction in Direction.entries) {
            val neighbour = pos.relative(direction)
            sky = maxOf(sky, level.getBrightness(LightLayer.SKY, neighbour))
            block = maxOf(block, level.getBrightness(LightLayer.BLOCK, neighbour))
        }
        return block shl 4 or (sky shl 20)   // LightTexture.pack, without touching a client class from here
    }
}
