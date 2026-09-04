package mods.eln.entity

import mods.eln.misc.Utils
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityList
import net.minecraft.entity.EnumCreatureAttribute
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.ai.EntityAIAttackMelee
import net.minecraft.entity.ai.EntityAIHurtByTarget
import net.minecraft.entity.ai.EntityAILookIdle
import net.minecraft.entity.ai.EntityAIMoveThroughVillage
import net.minecraft.entity.ai.EntityAIMoveTowardsRestriction
import net.minecraft.entity.ai.EntityAINearestAttackableTarget
import net.minecraft.entity.ai.EntityAISwimming
import net.minecraft.entity.ai.EntityAIWatchClosest
import net.minecraft.entity.monster.EntityMob
import net.minecraft.entity.passive.EntityVillager
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Items
import net.minecraft.init.SoundEvents
import net.minecraft.item.ItemMonsterPlacer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.DamageSource
import net.minecraft.util.SoundEvent
import net.minecraft.util.math.BlockPos
import net.minecraft.block.Block
import net.minecraft.world.World
import java.util.ArrayList
import java.util.Random

class ReplicatorEntity(world: World) : EntityMob(world) {
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
        tasks.addTask(priority++, EntityAISwimming(this))
        // 1.8 split target selection out of the melee task: one EntityAIAttackMelee attacks
        // whatever the target tasks below have chosen, replacing the three per-class tasks.
        tasks.addTask(priority++, EntityAIAttackMelee(this, 1.0, false))
        tasks.addTask(priority++, replicatorAi)
        tasks.addTask(priority++, EntityAIMoveTowardsRestriction(this, 1.0))
        tasks.addTask(priority++, EntityAIMoveThroughVillage(this, 1.0, false))
        tasks.addTask(priority++, ConfigurableAiWander(this, 1.0, 20))
        tasks.addTask(priority, EntityAIWatchClosest(this, EntityPlayer::class.java, 8.0f))
        tasks.addTask(priority++, EntityAILookIdle(this))

        priority = 1
        targetTasks.addTask(priority++, EntityAIHurtByTarget(this, true))
        // checkSight values are 1.7.10's: players must be visible, villagers and other replicators need not be.
        targetTasks.addTask(priority, EntityAINearestAttackableTarget(this, EntityPlayer::class.java, true))
        targetTasks.addTask(priority, EntityAINearestAttackableTarget(this, EntityVillager::class.java, false))
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
            entityLiving.setLocationAndAngles(posX, posY, posZ, 0.0f, 0.0f)
            entityLiving.rotationYawHead = entityLiving.rotationYaw
            entityLiving.renderYawOffset = entityLiving.rotationYaw
            world.spawnEntity(entityLiving)
            entityLiving.playLivingSound()
            hunger = 0.0
        }
    }

    fun eatElectricity(energy: Double) {
        hunger -= Math.min(0.001, energy / hungerToEnergy)
    }

    override fun applyEntityAttributes() {
        super.applyEntityAttributes()
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).baseValue = 8.0
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).baseValue = 8.0
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).baseValue = 0.23000000417232513
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).baseValue = 3.0
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
            // EntityTag NBT, which ItemMonsterPlacer writes for us.
            val entityId = EntityList.getKey(ReplicatorEntity::class.java)
            if (entityId != null) {
                val egg = ItemStack(Items.SPAWN_EGG)
                ItemMonsterPlacer.applyEntityIdToItemStack(egg, entityId)
                entityDropItem(egg, 0.5f)
            }
        }
    }

    override fun getCreatureAttribute(): EnumCreatureAttribute {
        return EnumCreatureAttribute.UNDEFINED
    }

    override fun writeEntityToNBT(nbt: NBTTagCompound) {
        super.writeEntityToNBT(nbt)
        nbt.setDouble("ElnHunger", hunger)
        nbt.setBoolean("isSpawnedFromWeather", isSpawnedFromWeather)
    }

    override fun readEntityFromNBT(nbt: NBTTagCompound) {
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
