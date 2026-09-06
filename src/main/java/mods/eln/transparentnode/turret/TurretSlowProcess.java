package mods.eln.transparentnode.turret;

import mods.eln.misc.McBridge;
import mods.eln.sim.fsm.CompositeState;
import mods.eln.sim.fsm.State;
import mods.eln.sim.fsm.StateMachine;
import mods.eln.generic.GenericItemUsingDamageDescriptor;
import mods.eln.item.EntitySensorFilterDescriptor;
import mods.eln.misc.Coordinate;
import mods.eln.misc.Utils;
import mods.eln.sim.process.destruct.WorldExplosion;
import mods.eln.sound.SoundCommand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.damagesource.DamageSource;

import java.util.List;
import java.util.Random;



public class TurretSlowProcess extends StateMachine {
    private static final Random rand = new Random();
    private double actualPower;

    public TurretSlowProcess(TurretElement element) {
        actualPower = 0;
        setInitialState(new IdleState());
        reset();
        this.element = element;
    }

    private final TurretElement element;

    private class IdleState implements State {
        @Override
        public void enter() {
        }

        @Override
        public State state(double time) {
            if (element.load.getVoltage() >= element.getDescriptor().getProperties().minimalVoltage *
                (1.0 + element.getDescriptor().getProperties().minimalVoltageHysteresisFactor)) {
                return new ActiveState();
            } else {
                return this;
            }
        }

        @Override
        public void leave() {
        }
    }

    private class ActiveState extends CompositeState {
        public ActiveState() {
            setInitialState(new SeekingState());
        }

        @Override
        public void enter() {
            element.setEnabled(true);
            actualPower = element.getDescriptor().getProperties().basePower;
            element.play(new SoundCommand("eln:turretactivated").mulVolume(0.5));
            super.enter();
        }

        @Override
        public State state(double time) {
            super.state(time);
            if (element.load.getVoltage() < element.getDescriptor().getProperties().minimalVoltage *
                (1.0 - element.getDescriptor().getProperties().minimalVoltageHysteresisFactor))
                return new WaitState();
            else if (element.load.getVoltage() > element.getDescriptor().getProperties().maximalVoltage)
                return new DamageState();
            else
                return this;
        }

        @Override
        public void leave() {
            element.setEnabled(false);
            actualPower = 0;
            element.play(new SoundCommand("eln:turretdeactivated").mulVolume(0.5));
            super.leave();
        }
    }

    private class DamageState implements State {
        @Override
        public void enter() {
            WorldExplosion explosion = new WorldExplosion(element).machineExplosion();
            explosion.destructImpl();
        }

        @Override
        public State state(double time) {
            return new IdleState();
        }

        @Override
        public void leave() {
        }

    }

    private class WaitState implements State {
        private double delay = 5.0;

        @Override
        public void enter() {
        }

        @Override
        public State state(double time) {
            delay -= time;
            if (delay <= 0.0) {
                return new IdleState();
            } else {
                return this;
            }
        }

        @Override
        public void leave() {
        }
    }

    private class SeekingState implements State {
        private double lastScanWasBefore = 0.;

        @Override
        public void enter() {
            actualPower = element.getDescriptor().getProperties().basePower;
            element.setGunPosition(0);
            element.setGunElevation(0);
            element.setSeekMode(true);
            element.setTurretAngle(element.getDescriptor().getProperties().actionAngle);
        }

        @Override
        public State state(double time) {
            if (element.getTurretAngle() >= element.getDescriptor().getProperties().actionAngle)
                element.setTurretAngle(-element.getDescriptor().getProperties().actionAngle);
            else if (element.getTurretAngle() <= -element.getDescriptor().getProperties().actionAngle)
                element.setTurretAngle(element.getDescriptor().getProperties().actionAngle);

            lastScanWasBefore += time;
            if (lastScanWasBefore < element.getDescriptor().getProperties().entityDetectionInterval) return null;
            lastScanWasBefore = 0;

            Class<?> filterClass = null;
            ItemStack filterStack = element.getInventory().getItem(TurretContainer.filterId);
            if (!McBridge.isNothing(filterStack)) {
                GenericItemUsingDamageDescriptor gen = EntitySensorFilterDescriptor.getDescriptor(filterStack);
                if (gen != null && gen instanceof EntitySensorFilterDescriptor) {
                    EntitySensorFilterDescriptor filter = (EntitySensorFilterDescriptor) gen;
                    filterClass = filter.entityClass;
                }
            }

            Coordinate coord = element.coordinate();
            AABB bb = coord.getAxisAlignedBB((int) element.getDescriptor().getProperties().detectionDistance);
            // World#getEntitiesWithinAABB is raw in 1.7.10, but this query asks specifically for LivingEntity.
            @SuppressWarnings("unchecked")
            List<LivingEntity> list = coord.world().getEntitiesOfClass(LivingEntity.class, bb);
            for (LivingEntity entity : list) {
                double dx = (entity.getX() - coord.x - 0.5);
                double dz = (entity.getZ() - coord.z - 0.5);
                double entityAngle = -Math.toDegrees(Math.atan2(dz, dx));
                switch (element.front) {
                    case XN:
                        if (entityAngle > 0)
                            entityAngle -= 180;
                        else
                            entityAngle += 180;
                        break;

                    case ZP:
                        entityAngle += 90;
                        break;

                    case ZN:
                        entityAngle -= 90;
                        break;

                    default:
                        break;

                }

                if (Math.abs(entityAngle - element.getTurretAngle()) < 15 &&
                    Math.abs(entityAngle) < element.getDescriptor().getProperties().actionAngle) {

                    if (element.filterIsSpare) {
                        if (filterClass != null && filterClass.isAssignableFrom(entity.getClass())) return null;
                    } else {
                        if (filterClass == null || !filterClass.isAssignableFrom(entity.getClass())) return null;
                    }

                    List<BlockState> blockList = Utils.traceRay(coord.world(), coord.x + 0.5, coord.y + 0.5, coord.z + 0.5,
                        entity.getX(), entity.getY() + entity.getEyeHeight(), entity.getZ());
                    boolean visible = true;
                    for (BlockState b : blockList)
                        if (b.isSolidRender(coord.world(), net.minecraft.core.BlockPos.ZERO)) {
                            visible = false;
                            break;
                        }

                    if (visible) {
                        if(entity.getHealth()>0) {
                            element.play(new SoundCommand("eln:turretfire").mulVolume(0.4));
                        } else {
                            element.play(new SoundCommand("eln:turretkill").mulVolume(0.4));
                        }
                        return new AimingState(entity);
                    }
                }
            }

            return null;
        }

        @Override
        public void leave() {
            element.setSeekMode(false);
        }
    }

    private class AimingState implements State {
        public AimingState(LivingEntity target) {
            this.target = target;
        }

        private final LivingEntity target;

        @Override
        public void enter() {
            actualPower = element.getDescriptor().getProperties().basePower +
                element.chargePower;
            element.setGunPosition(1);
        }

        @Override
        public State state(double time) {
            if (target.getHealth()<=0) return new SeekingState();

            Class<?> filterClass = null;
            ItemStack filterStack = element.getInventory().getItem(TurretContainer.filterId);
            if (!McBridge.isNothing(filterStack)) {
                GenericItemUsingDamageDescriptor gen = EntitySensorFilterDescriptor.getDescriptor(filterStack);
                if (gen != null && gen instanceof EntitySensorFilterDescriptor) {
                    EntitySensorFilterDescriptor filter = (EntitySensorFilterDescriptor) gen;
                    filterClass = filter.entityClass;
                }
            }
            if (element.filterIsSpare) {
                if (filterClass != null && filterClass.isAssignableFrom(target.getClass())) return new SeekingState();
            } else {
                if (filterClass == null || !filterClass.isAssignableFrom(target.getClass())) return new SeekingState();
            }

            Coordinate coord = element.coordinate();

            double dx = (float) (target.getX() - coord.x - 0.5);
            double dy = (float) (target.getY() + target.getEyeHeight() - coord.y - 0.75);
            double dz = (float) (target.getZ() - coord.z - 0.5);
            double entityAngle = -Math.toDegrees(Math.atan2(dz, dx));
            switch (element.front) {
                case XN:
                    if (entityAngle > 0)
                        entityAngle -= 180;
                    else
                        entityAngle += 180;
                    break;

                case ZP:
                    entityAngle += 90;
                    break;

                case ZN:
                    entityAngle -= 90;
                    break;

                default:
                    break;

            }

            double entityAngle2 = -Math.toDegrees(Math.asin(dy / Math.sqrt(dx * dx + dz * dz)));

            if (Math.abs(entityAngle) > element.getDescriptor().getProperties().actionAngle) return new SeekingState();

            element.setTurretAngle((float) entityAngle);
            element.setGunElevation((float) -entityAngle2);

            if (Math.abs(target.getX() - coord.x) > element.getDescriptor().getProperties().aimDistance ||
                Math.abs(target.getZ() - coord.z) > element.getDescriptor().getProperties().aimDistance)
                return new SeekingState();

            List<BlockState> blockList = Utils.traceRay(coord.world(), coord.x + 0.5, coord.y + 0.5, coord.z + 0.5,
                target.getX(), target.getY() + target.getEyeHeight(), target.getZ());
            for (BlockState b : blockList)
                if (b.isSolidRender(coord.world(), net.minecraft.core.BlockPos.ZERO))
                    return new SeekingState();

            if (element.getGunPosition() == 1 && element.isTargetReached() &&
                element.energyBuffer >= element.getDescriptor().getProperties().impulseEnergy)
                return new ShootState(target);

            return this;
        }

        @Override
        public void leave() {
        }
    }

    class ShootState implements State {
        public ShootState(LivingEntity target) {
            this.target = target;
        }

        private final LivingEntity target;

        @Override
        public void enter() {
            if (target != null) {
                target.invulnerableTime = 0;
                target.hurt(mods.eln.misc.ElnDamage.turret(target), 5);
                element.shoot();
                element.play(new SoundCommand("eln:lasergun"));
            }
        }

        @Override
        public State state(double time) {
            if (target == null || target.getHealth()<=0)
                return new SeekingState();
            else
                return new AimingState(target);
        }

        @Override
        public void leave() {
            element.energyBuffer = 0;
        }
    }

    @Override
    public void process(double time) {
        double MaximalEnergy = element.getDescriptor().getProperties().impulseEnergy;
        element.energyBuffer += element.powerResistor.getPower() * time;
        boolean full = element.energyBuffer > MaximalEnergy;

        if (full) {
            element.energyBuffer = MaximalEnergy;
        }

        if (element.coordinate().getBlockExist())
            super.process(time);

        if (actualPower == 0 || full)
            element.powerResistor.highImpedance();
        else
            element.powerResistor.setResistance(element.load.getVoltage() * element.load.getVoltage() / actualPower);
    }
}
