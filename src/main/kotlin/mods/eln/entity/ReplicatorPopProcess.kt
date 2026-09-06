package mods.eln.entity

import mods.eln.Eln
import mods.eln.misc.Utils
import mods.eln.sim.IProcess
import net.minecraft.core.BlockPos
import net.minecraft.world.Difficulty
import net.minecraft.world.level.LightLayer
import mods.eln.misc.isBlockLoaded
import mods.eln.misc.rand

/**
 * Spawns replicators near players during thunderstorms and culls the population above the
 * configured cap.
 */
class ReplicatorPopProcess : IProcess {
    override fun process(time: Double) {
        val world = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()?.overworld() ?: return
        val maxReplicators = Eln.config.getIntOrElse("entities.replicator.maxCount", 100)
        val popPerSecondPerPlayer =
            Eln.config.getDoubleOrElse("entities.replicator.thunderSpawnPerSecondPerPlayer", 1.0 / 120.0)

        var replicatorCount = 0
        for (entity in world.getEntities(ReplicatorEntity.TYPE.get()) { true }) {
            replicatorCount++
            if (replicatorCount > maxReplicators) {
                entity.discard()
            }
        }

        if (world.difficulty == Difficulty.PEACEFUL) return
        // Weather spawns are still mob spawns; honour the game rule like vanilla does.
        if (!world.gameRules.getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)) return

        if (world.isThundering) {
            for (player in world.players()) {
                if (Math.random() * world.players().size < time * popPerSecondPerPlayer && player.level() == world) {
                    val x = (player.x + Utils.rand(-100.0, 100.0)).toInt()
                    val z = (player.z + Utils.rand(-100.0, 100.0)).toInt()
                    Utils.println("POP")

                    // A spawn 100 blocks out may land in an unloaded chunk; never force one to load
                    // for a mob spawn.
                    if (!world.isBlockLoaded(BlockPos(x, 2, z))) break

                    // Climb to the first dark air pocket with headroom. The column scan is bounded:
                    // an unbounded one would hang the server tick if it never found one.
                    var y = 2
                    var found = false
                    while (y < world.maxBuildHeight - 2) {
                        val pos = BlockPos(x, y, z)
                        if (world.isEmptyBlock(pos) && world.isEmptyBlock(pos.above()) &&
                            world.getBrightness(LightLayer.BLOCK, pos) <= 6
                        ) {
                            found = true
                            break
                        }
                        y++
                    }
                    if (!found) continue

                    val entityLiving = ReplicatorEntity(ReplicatorEntity.TYPE.get(), world)
                    entityLiving.moveTo(x + 0.5, y.toDouble(), z + 0.5, 0.0f, 0.0f)
                    entityLiving.yHeadRot = entityLiving.yRot
                    entityLiving.yBodyRot = entityLiving.yRot
                    world.addFreshEntity(entityLiving)
                    entityLiving.playAmbientSound()
                    entityLiving.isSpawnedFromWeather = true
                    Utils.println("Spawn Replicator at $x $y $z")
                }
            }
        }
    }
}
