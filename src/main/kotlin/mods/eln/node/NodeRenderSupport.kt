package mods.eln.node

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
}
