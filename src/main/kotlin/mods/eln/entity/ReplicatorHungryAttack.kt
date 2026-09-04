package mods.eln.entity

import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.ai.EntityAINearestAttackableTarget

/** Replicators turn on each other only once hungry enough. */
class ReplicatorHungryAttack<T : EntityLivingBase>(
    private val replicator: ReplicatorEntity,
    targetClass: Class<T>,
    shouldCheckSight: Boolean
) : EntityAINearestAttackableTarget<T>(replicator, targetClass, shouldCheckSight) {
    override fun shouldExecute(): Boolean {
        if (replicator.hunger < replicator.hungerToCanibal) return false
        return super.shouldExecute()
    }
}
