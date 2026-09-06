package mods.eln.entity;

import mods.eln.misc.Utils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.Random;

public class ReplicatorEntity extends Monster {

    boolean isSpawnedFromWeather = false;
    double hungerTime = 10 * 60;
    double hungerToEnergy = 10.0 * hungerTime;
    double energyToDuplicate = 10000;
    double hungerToDuplicate = -energyToDuplicate / hungerToEnergy;
    double hungerToCanibal = 0.6;

    public static final ArrayList<ItemStack> dropList = new ArrayList<ItemStack>();

    public ReplicatorEntity(Level par1World) {
        super(par1World);

        enablePersistence();

        this.setSize(0.3F, 0.7F);

        ReplicatorCableAI replicatorIa = new ReplicatorCableAI(this);
        int p = 0;

        this.tasks.addTask(p++, new EntityAISwimming(this));
        this.tasks.addTask(p++, new EntityAIAttackMelee(this, 1.0D, false));
        this.tasks.addTask(p++, replicatorIa);
        this.tasks.addTask(p++, new EntityAIMoveTowardsRestriction(this, 1.0D));
        this.tasks.addTask(p++, new EntityAIMoveThroughVillage(this, 1.0D, false));
        this.tasks.addTask(p++, new ConfigurableAiWander(this, 1.0D, 20));
        this.tasks.addTask(p, new EntityAIWatchClosest(this, Player.class, 8.0F));
        this.tasks.addTask(p++, new EntityAILookIdle(this));
        p = 1;
        this.targetTasks.addTask(p++, new EntityAIHurtByTarget(this, true));
        this.targetTasks.addTask(p, new EntityAINearestAttackableTarget(this, Player.class, true, true));
        this.targetTasks.addTask(p, new EntityAINearestAttackableTarget(this, Villager.class, true, false));
        this.targetTasks.addTask(p++, new ReplicatorHungryAttack(this, ReplicatorEntity.class, 0, false, true, null));
        // this.targetTasks.addTask(p++, new EntityAINearestAttackableTarget(this, ReplicatorEntity.class, 0, false));
        // this.targetTasks.addTask(p++, replicatorIa);
    }

    @Override
    public boolean attackEntityAsMob(Entity e) {
        if (e instanceof ReplicatorEntity) {
            this.hunger -= 0.4;
            ((ReplicatorEntity) e).hunger += 0.4;
        }
        return super.attackEntityAsMob(e);
    }

    @Override
    protected void updateAITasks() {
        super.updateAITasks();
        //setDead();
        hunger += 0.05 / hungerTime;

        if (hunger > 1 && Math.random() < 0.05 / 5) {
            attackEntityFrom(DamageSource.STARVE, 1);
        }
        if (hunger < 0.5 && Math.random() * 10 < 0.05) {
            heal(1f);
        }
        if (hunger < hungerToDuplicate) {
            ReplicatorEntity entityliving = new ReplicatorEntity(this.world);
            entityliving.setLocationAndAngles(this.posX, this.posY, this.posZ, 0f, 0f);
            entityliving.rotationYawHead = entityliving.rotationYaw;
            entityliving.renderYawOffset = entityliving.rotationYaw;
            world.spawnEntity(entityliving);
            entityliving.playLivingSound();
            hunger = 0;
        }
    }

    void eatElectricity(double e) {
        hunger = hunger - Math.min(0.001, e / hungerToEnergy);
    }

    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
        this.getEntityAttribute(Attributes.MAX_HEALTH).setBaseValue(8.0D);
        this.getEntityAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.23000000417232513D);
        this.getEntityAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(3.0D);
        // this.getAttributeMap().func_111150_b(field_110186_bp).setAttribute(this.rand.nextDouble() * ForgeDummyContainer.zombieSummonBaseChance);
    }

    protected SoundEvent getAmbientSound()
    {
        return SoundEvents.ENTITY_SILVERFISH_AMBIENT;
    }

    protected SoundEvent getHurtSound()
    {
        return SoundEvents.ENTITY_SILVERFISH_HURT;
    }

    protected SoundEvent getDeathSound()
    {
        return SoundEvents.ENTITY_SILVERFISH_DEATH;
    }

    protected void playStepSound(BlockPos pos, Block blockIn)
    {
        this.playSound(SoundEvents.ENTITY_SILVERFISH_STEP, 0.15F, 1.0F);
    }

    @Override
    protected void dropFewItems(boolean par1, int par2) {
        if (!dropList.isEmpty()) {
            this.entityDropItem(dropList.get(this.rand.nextInt(dropList.size())).copy(), 0.5f);
        } else {
            // Default drops if list is empty to prevent crash
            this.entityDropItem(new ItemStack(Items.IRON_INGOT), 0.5f);
        }

        if (isSpawnedFromWeather) {
            if (Math.random() < 0.33) {
                int id = EntityList.getID(ReplicatorEntity.class);
                this.entityDropItem(new ItemStack(Item.getByNameOrId("spawn_egg"), 1, id), 0.5f);
            }
        }
    }

    public EnumCreatureAttribute getCreatureAttribute() {
        return EnumCreatureAttribute.UNDEFINED;
    }

    double hunger = (Math.random() - 0.5) * 0.3;

    @Override
    public void writeEntityToNBT(CompoundTag nbt) {
        super.writeEntityToNBT(nbt);
        nbt.putDouble("ElnHunger", hunger);
        nbt.putBoolean("isSpawnedFromWeather", isSpawnedFromWeather);
    }

    @Override
    public void readEntityFromNBT(CompoundTag nbt) {
        super.readEntityFromNBT(nbt);

        hunger = nbt.getDouble("ElnHunger");
        isSpawnedFromWeather = nbt.getBoolean("isSpawnedFromWeather");

        Utils.println("[Replicator] " + posX + " " + posY + " " + posZ + " ");
    }
}
