package mods.eln.sixnode.lampsocket;

import mods.eln.Eln;
import mods.eln.generic.GenericItemUsingDamage;
import mods.eln.generic.GenericItemUsingDamageDescriptor;
import mods.eln.init.Config;
import mods.eln.item.LampDescriptor;
import mods.eln.item.LampDescriptor.Type;
import mods.eln.misc.Coordinate;
import mods.eln.misc.INBTTReady;
import mods.eln.misc.Utils;
import mods.eln.server.SaveConfig;
import mods.eln.sim.IProcess;
import mods.eln.sixnode.lampsupply.LampSupplyElement;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Random;

public class LampSocketProcess implements IProcess, INBTTReady {

    double time = 0;
    double deltaTBase = 0.2;
    double deltaT = deltaTBase;
    public double invulerabilityTimeLeft = 2;
    boolean overPoweredInvulerabilityArmed = true;
    LampSocketElement lamp;
    int light = 0; // 0..15
    double alphaZ = 0.0;

    double stableProb = 0;

    ItemStack lampStackLast = null;
    boolean boot = true;

    double vp[] = new double[3];

    private LampSupplyElement oldLampSupply;

    double updateLifeTimeout = 0, updateLifeTimeoutMax = 5;


    public LampSocketProcess(LampSocketElement l) {
        this.lamp = l;
    }

    private LampDescriptor getLampDescriptor(ItemStack lampStack) {
        if (lampStack == null || lampStack.isEmpty()) return null;
        if (!(lampStack.getItem() instanceof GenericItemUsingDamage)) return null;

        GenericItemUsingDamageDescriptor descriptor =
            ((GenericItemUsingDamage<GenericItemUsingDamageDescriptor>) lampStack.getItem()).getDescriptor(lampStack);
        return descriptor instanceof LampDescriptor ? (LampDescriptor) descriptor : null;
    }

    private double getEffectiveLampVoltage(LampDescriptor lampDescriptor) {
        double resistorVoltage = Math.abs(lamp.lampResistor.getU());
        double loadVoltage = Math.abs(lamp.positiveLoad.getU());

        // In the broken 1.12 path the node voltage can be correct while the one-ended resistor
        // still reports 0V. Prefer the physically larger reading instead of letting the lamp stay dark.
        if (loadVoltage > resistorVoltage) {
            return loadVoltage;
        }
        return resistorVoltage;
    }

    private double getEffectiveLampPower(LampDescriptor lampDescriptor, double effectiveVoltage) {
        double resistorPower = lamp.lampResistor.getP();
        double inferredPower = effectiveVoltage * effectiveVoltage / lampDescriptor.getR();

        if (!Double.isFinite(resistorPower) || resistorPower < 0) {
            return inferredPower;
        }
        return Math.max(resistorPower, inferredPower);
    }

    @Override
    public void process(double time) {
        ItemStack lampStack = lamp.getInventory().getStackInSlot(0);
        LampDescriptor lampDescriptor = getLampDescriptor(lampStack);

        if (boot) {
            if (light == 0) {
                // If light is zero on boot, ensure the world light is also zero
                // to fix the reload issue where it might be stuck at a higher value
                lamp.sixNode.setLightValue(0);
            }
        }

        if (!lamp.poweredByLampSupply || !lamp.hasCable()) {
            lamp.setIsConnectedToLampSupply(false);
            oldLampSupply = null;
        } else {
            Coordinate myCoord = lamp.sixNode.coordinate;
            LampSupplyElement.PowerSupplyChannelHandle best = null;
            float bestDistance = 10000;
            List<LampSupplyElement.PowerSupplyChannelHandle> list = LampSupplyElement.channelMap.get(lamp.channel);
            if (list != null) {
                for (LampSupplyElement.PowerSupplyChannelHandle s : list) {
                    float distance = (float) s.element.sixNode.coordinate.trueDistanceTo(myCoord);
                    if (distance < bestDistance && distance <= s.element.getRange()) {
                        bestDistance = distance;
                        best = s;
                    }
                }
            }
            if (best != null && best.element.getChannelState(best.id)) {
                if (lampDescriptor != null) {
                    best.element.addToRp(lampDescriptor.getR());
                }
                lamp.positiveLoad.state = best.element.powerLoad.state;
                oldLampSupply = best.element;
            } else {
                lamp.positiveLoad.state = 0;
                oldLampSupply = null;
            }

            lamp.setIsConnectedToLampSupply(best != null);
        }

        if (lampDescriptor != null) {
            if (lamp.getCoordinate().doesBlockExist() && lampDescriptor.vegetableGrowRate != 0.0) {
                double randTarget = 1.0 / lampDescriptor.vegetableGrowRate * time * (1.0 * light / lampDescriptor.nominalLight / 15.0);
                if (randTarget > Math.random()) {
                    boolean exit = false;
                    Vec3d vv = new Vec3d(1, 0, 0);
                    Vec3d vp = new Vec3d(myCoord().pos.getX() + 0.5, myCoord().pos.getY() + 0.5, myCoord().pos.getZ() + 0.5);

                    // TODO(1.10): I may have swapped these two.
                    vv = vv.rotatePitch((float) (alphaZ * Math.PI / 180.0));
                    vv = vv.rotateYaw((float) ((Math.random() - 0.5) * 2 * Math.PI / 4));
                    vv = vv.rotatePitch((float) ((Math.random() - 0.5) * 2 * Math.PI / 4));

                    vv = lamp.front.rotateOnXnLeft(vv);
                    vv = lamp.side.rotateFromXN(vv);

                    Coordinate c = new Coordinate(myCoord());

                    for (int idx = 0; idx < lamp.socketDescriptor.range + light; idx++) {
                        // newCoord.move(lamp.side.getInverse());
                        vp = vp.add(vv.x, vv.y, vv.z);
                        c.setPosition(vp);

                        if (!c.doesBlockExist()) {
                            exit = true;
                            break;
                        }
                        if (isOpaque(c)) {
                            vp = vp.add(-vv.x, -vv.y, -vv.z);
                            c.setPosition(vp);
                            break;
                        }
                    }

                    if (!exit) {
                        Block b = c.getBlockState().getBlock();

                        if (c.isAir()) {
                            b.updateTick(c.world(), new BlockPos(c.pos.getX(), c.pos.getY(), c.pos.getZ()), c.getBlockState(), new Random());
                        }
                    }
                }
            }
        }

        this.time += time;
        if (this.time < deltaT)
            return;

        this.time -= deltaT;

        lamp.computeElectricalLoad();
        int oldLight = light;
        int newLight = 0;

        ItemStack currentStack = lampStack != null ? lampStack : ItemStack.EMPTY;
        ItemStack lastStack = lampStackLast != null ? lampStackLast : ItemStack.EMPTY;
        if (!boot && (!ItemStack.areItemStacksEqual(currentStack, lastStack) || lampDescriptor == null)) {
            stableProb = 0;
        }

        if (lampDescriptor != null) {
            if (stableProb < 0)
                stableProb = 0;

            double effectiveVoltage = getEffectiveLampVoltage(lampDescriptor);
            double lightDouble = 0;
            switch (lampDescriptor.type) {
                case Incandescent:
                case LED:
                    lightDouble = lampDescriptor.nominalLight * (effectiveVoltage - lampDescriptor.minimalU) / (lampDescriptor.nominalU - lampDescriptor.minimalU);
                    lightDouble = (lightDouble * 16);
                    break;

                case eco:
                    double U = effectiveVoltage;
                    if (U < lampDescriptor.minimalU) {
                        stableProb = 0;
                        lightDouble = 0;
                    } else {
                        double powerFactor = U / lampDescriptor.nominalU;
                        stableProb += U / lampDescriptor.stableU * deltaT / lampDescriptor.stableTime * lampDescriptor.stableUNormalised;

                        if (stableProb > U / lampDescriptor.stableU)
                            stableProb = U / lampDescriptor.stableU;
                        if (Math.random() > stableProb) {
                            lightDouble = 0;
                        } else {
                            lightDouble = lampDescriptor.nominalLight * powerFactor;
                            lightDouble = (lightDouble * 16);
                        }
                    }
                    break;

                default:
                    break;
            }

            if (lightDouble - oldLight > 1.0) {
                newLight = (int) lightDouble;
            } else if (lightDouble - oldLight < -0.3) {
                newLight = (int) lightDouble;
            } else {
                newLight = oldLight;
            }

            if (newLight < 0)
                newLight = 0;
            if (newLight > 14)
                newLight = 14;

			/*
             * double overFactor = (lamp.electricalLoad.Uc-lampDescriptor.minimalU) /(lampDescriptor.nominalU-lampDescriptor.minimalU);
			 */
            double overFactor = getEffectiveLampPower(lampDescriptor, effectiveVoltage) / lampDescriptor.nominalP;
            if (overFactor < 0)
                overFactor = 0;

            if (overFactor < 1.3)
                overPoweredInvulerabilityArmed = true;

            if (overFactor > 1.5 && overPoweredInvulerabilityArmed) {
                invulerabilityTimeLeft = 2;
                overPoweredInvulerabilityArmed = false;
            }

            if (invulerabilityTimeLeft != 0 && overFactor > 1.5)
                overFactor = 1.5;

            updateLifeTimeout += deltaT;
            if (updateLifeTimeout > updateLifeTimeoutMax &&
                !(lampDescriptor.type == Type.LED && Config.INSTANCE.getLedLampInfiniteLife())) {
                // Utils.println("aging");
                updateLifeTimeout -= updateLifeTimeoutMax;
                double lifeLost = overFactor * updateLifeTimeoutMax / lampDescriptor.nominalLife;
                lifeLost = Utils.voltageMargeFactorSub(lifeLost);
                if (overFactor >= 1.21) {
                    lifeLost *= overFactor;
                }
                // lifeLost *= overFactor;
                // lifeLost *= overFactor;

                double life = lampDescriptor.getLifeInTag(lampStack) - lifeLost;
                if (SaveConfig.instance == null || SaveConfig.instance.electricalLampAging) {
                    lampDescriptor.setLifeInTag(lampStack, life);
                }
                if (life < 0 || overFactor > 3) {
                    lamp.getInventory().setInventorySlotContents(0, ItemStack.EMPTY);
                    light = 0;
                }

            }

            boot = false;
        }

        if (invulerabilityTimeLeft != 0) {
            invulerabilityTimeLeft -= deltaT;
            if (invulerabilityTimeLeft < 0)
                invulerabilityTimeLeft = 0;
        }
        deltaT = deltaTBase + deltaTBase * (-0.1 + 0.2 * Math.random());

        lampStackLast = lampStack;

        // Set light directly on the node (like emergency lamp)
        setLight(newLight);
    }

    // ElectricalConnectionOneWay connection = null;

    public void setLight(int newLight) {
        int oldLight = light;
        light = newLight;

        if (light != oldLight) {
            lamp.sixNode.recalculateLightValue();

            ByteArrayOutputStream bos = new ByteArrayOutputStream(64);
            DataOutputStream packet = new DataOutputStream(bos);

            lamp.preparePacketForClient(packet);
            try {
                packet.writeByte(light);
            } catch (IOException e) {
                e.printStackTrace();
            }
            lamp.sendPacketToAllClient(bos);
            lamp.needPublish();
        }
    }

    Coordinate myCoord() {
        return lamp.sixNode.coordinate;
    }

    public void destructor() {
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt, String str) {
        stableProb = nbt.getDouble(str + "LSP" + "stableProb");
        alphaZ = nbt.getFloat(str + "alphaZ");
        light = nbt.getInteger(str + "light");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt, String str) {
        nbt.setDouble(str + "LSP" + "stableProb", stableProb);
        nbt.setFloat(str + "alphaZ", (float) alphaZ);
        nbt.setInteger(str + "light", light);
        return nbt;
    }

    public int getBlockLight() {
        return light;
    }

    public boolean isOpaque(Coordinate coord) {
        Block block = coord.getBlockState().getBlock();
        boolean isNotOpaque = coord.isAir() || !block.isOpaqueCube(block.getBlockState().getBaseState());
        if (block == Blocks.FARMLAND)
            isNotOpaque = false;
        return !isNotOpaque;
    }
}
