package mods.eln.entity

import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.util.DefaultRandomPos
import mods.eln.misc.xCoord
import mods.eln.misc.yCoord
import mods.eln.misc.zCoord

class ConfigurableAiWander(
    private val entity: PathfinderMob,
    private val speed: Double,
    private val randLimit: Int
) : Goal() {
    private var xPosition = 0.0
    private var yPosition = 0.0
    private var zPosition = 0.0

    init {
        mutexBits = 1
    }

    override fun shouldExecute(): Boolean {
        // EntityLiving.age (time since last player proximity) became getIdleTime() in 1.8.
        if (entity.idleTime >= 100) return false
        if (entity.rng.nextInt(randLimit) != 0) return false

        val vec3 = DefaultRandomPos.findRandomTarget(entity, 10, 7) ?: return false
        xPosition = vec3.x
        yPosition = vec3.y
        zPosition = vec3.z
        return true
    }

    override fun shouldContinueExecuting(): Boolean {
        return !entity.navigator.noPath()
    }

    override fun startExecuting() {
        entity.navigator.tryMoveToXYZ(xPosition, yPosition, zPosition, speed)
    }
}
