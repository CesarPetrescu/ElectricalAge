package mods.eln.entity

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal

/** Replicators turn on each other only once hungry enough. */
class ReplicatorHungryAttack<T : LivingEntity>(
    private val replicator: ReplicatorEntity,
    targetClass: Class<T>,
    shouldCheckSight: Boolean
) : NearestAttackableTargetGoal<T>(replicator, targetClass, shouldCheckSight) {
    override fun canUse(): Boolean {
        if (replicator.hunger < replicator.hungerToCanibal) return false
        return super.canUse()
    }
}
