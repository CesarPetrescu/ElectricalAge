package mods.eln.entity

import mods.eln.Eln
import mods.eln.misc.Utils
import mods.eln.sim.IProcess
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.util.math.BlockPos
import net.minecraft.world.EnumDifficulty
import net.minecraft.world.EnumSkyBlock
import net.minecraftforge.fml.common.FMLCommonHandler

/**
 * Spawns replicators near players during thunderstorms and culls the population above the
 * configured cap.
 */
class ReplicatorPopProcess : IProcess {
    override fun process(time: Double) {
        val world = FMLCommonHandler.instance().minecraftServerInstance.getWorld(0)
        val maxReplicators = Eln.config.getIntOrElse("entities.replicator.maxCount", 100)
        val popPerSecondPerPlayer =
            Eln.config.getDoubleOrElse("entities.replicator.thunderSpawnPerSecondPerPlayer", 1.0 / 120.0)

        var replicatorCount = 0
        for (entity in world.loadedEntityList) {
            if (entity is ReplicatorEntity) {
                replicatorCount++
                if (replicatorCount > maxReplicators) {
                    entity.setDead()
                }
            }
        }

        if (world.difficulty == EnumDifficulty.PEACEFUL) return
        // Weather spawns are still mob spawns; honour the game rule like vanilla does.
        if (!world.gameRules.getBoolean("doMobSpawning")) return

        if (world.worldInfo.isThundering) {
            for (player in world.playerEntities) {
                if (player !is EntityPlayerMP) continue
                if (Math.random() * world.playerEntities.size < time * popPerSecondPerPlayer && player.world == world) {
                    val x = (player.posX + Utils.rand(-100.0, 100.0)).toInt()
                    val z = (player.posZ + Utils.rand(-100.0, 100.0)).toInt()
                    Utils.println("POP")

                    // A spawn 100 blocks out may land in an unloaded chunk; never force one to load
                    // for a mob spawn.
                    if (!world.isBlockLoaded(BlockPos(x, 2, z))) break

                    // Climb to the first dark air pocket with headroom. The column scan is bounded:
                    // an unbounded one would hang the server tick if it never found one.
                    var y = 2
                    var found = false
                    while (y < world.height - 2) {
                        val pos = BlockPos(x, y, z)
                        if (world.isAirBlock(pos) && world.isAirBlock(pos.up()) &&
                            world.getLightFor(EnumSkyBlock.BLOCK, pos) <= 6
                        ) {
                            found = true
                            break
                        }
                        y++
                    }
                    if (!found) continue

                    val entityLiving = ReplicatorEntity(world)
                    entityLiving.setLocationAndAngles(x + 0.5, y.toDouble(), z + 0.5, 0.0f, 0.0f)
                    entityLiving.rotationYawHead = entityLiving.rotationYaw
                    entityLiving.renderYawOffset = entityLiving.rotationYaw
                    world.spawnEntity(entityLiving)
                    entityLiving.playLivingSound()
                    entityLiving.isSpawnedFromWeather = true
                    Utils.println("Spawn Replicator at $x $y $z")
                }
            }
        }
    }
}
