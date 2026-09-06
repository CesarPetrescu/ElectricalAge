package mods.eln.entity

import mods.eln.misc.Utils
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import java.util.ArrayList
import java.util.Random
import java.util.function.Supplier

/**
 * The cable-eating replicator. 1.21: the entity type, its attributes and its spawn egg are
 * registry objects (see EntityRegistration); the size lives on the type builder.
 */
class ReplicatorEntity(type: EntityType<out ReplicatorEntity>, world: Level) : Monster(type, world) {
    var isSpawnedFromWeather = false
    var hungerTime = 10.0 * 60.0
    var hungerToEnergy = 10.0 * hungerTime
    var energyToDuplicate = 10000.0
    var hungerToDuplicate = -energyToDuplicate / hungerToEnergy
    var hungerToCanibal = 0.6
    var hunger = (Math.random() - 0.5) * 0.3

    init {
        setPersistenceRequired()
    }

    override fun registerGoals() {
        val replicatorAi = ReplicatorCableAI(this)
        var priority = 0
        goalSelector.addGoal(priority++, FloatGoal(this))
        // 1.8 split target selection out of the melee task: one MeleeAttackGoal attacks
        // whatever the target tasks below have chosen, replacing the three per-class tasks.
        goalSelector.addGoal(priority++, MeleeAttackGoal(this, 1.0, false))
        goalSelector.addGoal(priority++, replicatorAi)
        goalSelector.addGoal(priority++, MoveTowardsRestrictionGoal(this, 1.0))
        goalSelector.addGoal(priority++, MoveThroughVillageGoal(this, 1.0, false, 4) { false })
        goalSelector.addGoal(priority++, ConfigurableAiWander(this, 1.0, 20))
        goalSelector.addGoal(priority, LookAtPlayerGoal(this, Player::class.java, 8.0f))
        goalSelector.addGoal(priority++, RandomLookAroundGoal(this))

        priority = 1
        targetSelector.addGoal(priority++, HurtByTargetGoal(this))
        // checkSight values are 1.7.10's: players must be visible, villagers and other replicators need not be.
        targetSelector.addGoal(priority, NearestAttackableTargetGoal(this, Player::class.java, true))
        targetSelector.addGoal(priority, NearestAttackableTargetGoal(this, Villager::class.java, false))
        targetSelector.addGoal(priority++, ReplicatorHungryAttack(this, ReplicatorEntity::class.java, false))
    }

    override fun doHurtTarget(entity: Entity): Boolean {
        if (entity is ReplicatorEntity) {
            hunger -= 0.4
            entity.hunger += 0.4
        }
        return super.doHurtTarget(entity)
    }

    override fun customServerAiStep() {
        super.customServerAiStep()
        hunger += 0.05 / hungerTime

        if (hunger > 1 && Math.random() < 0.05 / 5) {
            hurt(damageSources().starve(), 1.0f)
        }
        if (hunger < 0.5 && Math.random() * 10 < 0.05) {
            heal(1.0f)
        }
        if (hunger < hungerToDuplicate) {
            val entityLiving = ReplicatorEntity(TYPE.get(), level())
            entityLiving.moveTo(x, y, z, 0.0f, 0.0f)
            entityLiving.yHeadRot = entityLiving.yRot
            entityLiving.yBodyRot = entityLiving.yRot
            level().addFreshEntity(entityLiving)
            entityLiving.playAmbientSound()
            hunger = 0.0
        }
    }

    fun eatElectricity(energy: Double) {
        hunger -= Math.min(0.001, energy / hungerToEnergy)
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.SILVERFISH_AMBIENT

    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.SILVERFISH_HURT

    override fun getDeathSound(): SoundEvent = SoundEvents.SILVERFISH_DEATH

    override fun playStepSound(pos: BlockPos, state: BlockState) {
        playSound(SoundEvents.SILVERFISH_STEP, 0.15f, 1.0f)
    }

    override fun dropCustomDeathLoot(level: ServerLevel, source: DamageSource, recentlyHit: Boolean) {
        super.dropCustomDeathLoot(level, source, recentlyHit)
        if (dropList.isNotEmpty()) {
            spawnAtLocation(dropList[Random().nextInt(dropList.size)].copy(), 0.5f)
        }

        if (isSpawnedFromWeather && Math.random() < 0.33) {
            spawnAtLocation(ItemStack(SPAWN_EGG.get()), 0.5f)
        }
    }

    override fun addAdditionalSaveData(nbt: CompoundTag) {
        super.addAdditionalSaveData(nbt)
        nbt.putDouble("ElnHunger", hunger)
        nbt.putBoolean("isSpawnedFromWeather", isSpawnedFromWeather)
    }

    override fun readAdditionalSaveData(nbt: CompoundTag) {
        super.readAdditionalSaveData(nbt)
        hunger = nbt.getDouble("ElnHunger")
        isSpawnedFromWeather = nbt.getBoolean("isSpawnedFromWeather")
        Utils.println("[Replicator] $x $y $z ")
    }

    companion object {
        @JvmField
        val dropList = ArrayList<ItemStack>()

        /** Registered by EntityRegistration through ElnRegistry. */
        @JvmField
        var TYPE: Supplier<EntityType<ReplicatorEntity>> = Supplier { throw IllegalStateException("ReplicatorEntity type not registered") }

        @JvmField
        var SPAWN_EGG: Supplier<Item> = Supplier { throw IllegalStateException("replicator spawn egg not registered") }

        /** 1.7.10's applyEntityAttributes, as the attribute supplier the entity type registers. */
        @JvmStatic
        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.FOLLOW_RANGE, 8.0)
            .add(Attributes.MAX_HEALTH, 8.0)
            .add(Attributes.MOVEMENT_SPEED, 0.23000000417232513)
            .add(Attributes.ATTACK_DAMAGE, 3.0)
    }
}
