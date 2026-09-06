package mods.eln.entity

import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.util.DefaultRandomPos

class ConfigurableAiWander(
    private val entity: PathfinderMob,
    private val speed: Double,
    private val randLimit: Int
) : Goal() {
    private var xPosition = 0.0
    private var yPosition = 0.0
    private var zPosition = 0.0

    init {
        setFlags(java.util.EnumSet.of(Flag.MOVE))
    }

    override fun canUse(): Boolean {
        // EntityLiving.age (time since last player proximity) became getIdleTime() in 1.8.
        if (entity.noActionTime >= 100) return false
        if (entity.random.nextInt(randLimit) != 0) return false

        val vec3 = DefaultRandomPos.getPos(entity, 10, 7) ?: return false
        xPosition = vec3.x
        yPosition = vec3.y
        zPosition = vec3.z
        return true
    }

    override fun canContinueToUse(): Boolean {
        return !entity.navigation.isDone
    }

    override fun start() {
        entity.navigation.moveTo(xPosition, yPosition, zPosition, speed)
    }
}
