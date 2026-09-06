package mods.eln.entity

import mods.eln.misc.Utils
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.SpawnEggItem
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.sounds.SoundEvent
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.Level
import java.util.ArrayList
import java.util.Random

class ReplicatorEntity(world: Level) : Monster(world) {
    var isSpawnedFromWeather = false
    var hungerTime = 10.0 * 60.0
    var hungerToEnergy = 10.0 * hungerTime
    var energyToDuplicate = 10000.0
    var hungerToDuplicate = -energyToDuplicate / hungerToEnergy
    var hungerToCanibal = 0.6
    var hunger = (Math.random() - 0.5) * 0.3

    init {
        enablePersistence()
        setSize(0.3f, 0.7f)

        val replicatorAi = ReplicatorCableAI(this)
        var priority = 0
        tasks.addTask(priority++, FloatGoal(this))
        // 1.8 split target selection out of the melee task: one MeleeAttackGoal attacks
        // whatever the target tasks below have chosen, replacing the three per-class tasks.
        tasks.addTask(priority++, MeleeAttackGoal(this, 1.0, false))
        tasks.addTask(priority++, replicatorAi)
        tasks.addTask(priority++, MoveTowardsRestrictionGoal(this, 1.0))
        tasks.addTask(priority++, MoveThroughVillageGoal(this, 1.0, false))
        tasks.addTask(priority++, ConfigurableAiWander(this, 1.0, 20))
        tasks.addTask(priority, LookAtPlayerGoal(this, Player::class.java, 8.0f))
        tasks.addTask(priority++, RandomLookAroundGoal(this))

        priority = 1
        targetTasks.addTask(priority++, HurtByTargetGoal(this, true))
        // checkSight values are 1.7.10's: players must be visible, villagers and other replicators need not be.
        targetTasks.addTask(priority, NearestAttackableTargetGoal(this, Player::class.java, true))
        targetTasks.addTask(priority, NearestAttackableTargetGoal(this, Villager::class.java, false))
        targetTasks.addTask(priority++, ReplicatorHungryAttack(this, ReplicatorEntity::class.java, false))
    }

    override fun attackEntityAsMob(entity: Entity): Boolean {
        if (entity is ReplicatorEntity) {
            hunger -= 0.4
            entity.hunger += 0.4
        }
        return super.attackEntityAsMob(entity)
    }

    override fun updateAITasks() {
        super.updateAITasks()
        hunger += 0.05 / hungerTime

        if (hunger > 1 && Math.random() < 0.05 / 5) {
            attackEntityFrom(DamageSource.STARVE, 1.0f)
        }
        if (hunger < 0.5 && Math.random() * 10 < 0.05) {
            heal(1.0f)
        }
        if (hunger < hungerToDuplicate) {
            val entityLiving = ReplicatorEntity(world)
            entityLiving.moveTo(posX, posY, posZ, 0.0f, 0.0f)
            entityLiving.rotationYawHead = entityLiving.yRot
            entityLiving.renderYawOffset = entityLiving.yRot
            world.addFreshEntity(entityLiving)
            entityLiving.playLivingSound()
            hunger = 0.0
        }
    }

    fun eatElectricity(energy: Double) {
        hunger -= Math.min(0.001, energy / hungerToEnergy)
    }

    override fun applyEntityAttributes() {
        super.applyEntityAttributes()
        getEntityAttribute(Attributes.FOLLOW_RANGE).baseValue = 8.0
        getEntityAttribute(Attributes.MAX_HEALTH).baseValue = 8.0
        getEntityAttribute(Attributes.MOVEMENT_SPEED).baseValue = 0.23000000417232513
        getEntityAttribute(Attributes.ATTACK_DAMAGE).baseValue = 3.0
    }

    // isAIEnabled() is gone: every EntityLiving runs its AI tasks on 1.8+.

    override fun getAmbientSound(): SoundEvent = SoundEvents.ENTITY_SILVERFISH_AMBIENT

    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.ENTITY_SILVERFISH_HURT

    override fun getDeathSound(): SoundEvent = SoundEvents.ENTITY_SILVERFISH_DEATH

    override fun playStepSound(pos: BlockPos, block: Block) {
        playSound(SoundEvents.ENTITY_SILVERFISH_STEP, 0.15f, 1.0f)
    }

    override fun dropFewItems(wasRecentlyHit: Boolean, lootingLevel: Int) {
        if (dropList.isNotEmpty()) {
            entityDropItem(dropList[Random().nextInt(dropList.size)].copy(), 0.5f)
        }

        if (isSpawnedFromWeather && Math.random() < 0.33) {
            // Spawn eggs stop being damage-keyed in 1.9: the entity id travels in the stack's
            // EntityTag NBT, which SpawnEggItem writes for us.
            val entityId = EntityType.getKey(ReplicatorEntity::class.java)
            if (entityId != null) {
                val egg = ItemStack(Items.SPAWN_EGG)
                SpawnEggItem.applyEntityIdToItemStack(egg, entityId)
                entityDropItem(egg, 0.5f)
            }
        }
    }

    override fun getCreatureAttribute(): MobType {
        return MobType.UNDEFINED
    }

    override fun writeEntityToNBT(nbt: CompoundTag) {
        super.writeEntityToNBT(nbt)
        nbt.putDouble("ElnHunger", hunger)
        nbt.putBoolean("isSpawnedFromWeather", isSpawnedFromWeather)
    }

    override fun readEntityFromNBT(nbt: CompoundTag) {
        super.readEntityFromNBT(nbt)
        hunger = nbt.getDouble("ElnHunger")
        isSpawnedFromWeather = nbt.getBoolean("isSpawnedFromWeather")
        Utils.println("[Replicator] $posX $posY $posZ ")
    }

    companion object {
        @JvmField
        val dropList = ArrayList<ItemStack>()
    }
}
