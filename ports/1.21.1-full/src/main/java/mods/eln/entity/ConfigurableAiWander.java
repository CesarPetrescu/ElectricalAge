package mods.eln.entity;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.world.phys.Vec3;

public class ConfigurableAiWander extends Goal {

    private PathfinderMob entity;
    private double xPosition;
    private double yPosition;
    private double zPosition;
    private double speed;

    private int randLimit;

    public ConfigurableAiWander(PathfinderMob par1EntityCreature, double speed, int randLimit) {
        this.entity = par1EntityCreature;
        this.speed = speed;
        this.setMutexBits(1);
        this.randLimit = randLimit;
    }

    /**
     * Returns whether the EntityAIBase should begin execution.
     */
    public boolean shouldExecute() {
        /*if (this.entity.getIdleTime() >= 100) {
            return false;
        } else */if (this.entity.getRNG().nextInt(randLimit) != 0) {
            return false;
        } else {
            Vec3 vec3 = RandomPositionGenerator.findRandomTarget(this.entity, 10, 7);

            if (vec3 == null) {
                return false;
            } else {
                this.xPosition = vec3.x;
                this.yPosition = vec3.y;
                this.zPosition = vec3.z;
                return true;
            }
        }
    }

    /**
     * Returns whether an in-progress EntityAIBase should continue executing
     */
    public boolean continueExecuting() {
        return !this.entity.getNavigator().noPath();
    }

    /**
     * Execute a one shot task or start executing a continuous task
     */
    public void startExecuting() {
        this.entity.getNavigator().tryMoveToXYZ(this.xPosition, this.yPosition, this.zPosition, this.speed);
    }
}
