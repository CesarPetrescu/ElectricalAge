package mods.eln.sim;

import mods.eln.Eln;
import mods.eln.entity.ReplicatorEntity;
import mods.eln.misc.Coordinate;
import mods.eln.misc.Utils;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;

import java.util.List;

public class MonsterPopFreeProcess implements IProcess {

    private final Coordinate coordinate;
    private final int range;

    double timerCounter = 0;
    final double timerPeriod = 0.212;

    List oldList = null;

    public MonsterPopFreeProcess(Coordinate coordinate, int range) {
        this.coordinate = coordinate;
        this.range = range;
    }

    @Override
    public void process(double time) {
        //Monster killing must be active before continuing :
        if (!Eln.config.getBooleanOrElse("entities.mobSpawning.preventNearLamps", true))
            return;

        timerCounter += time;
        if (timerCounter > timerPeriod) {
            timerCounter -= Utils.rand(1, 1.5) * timerPeriod;
            List list = coordinate.world().getEntitiesOfClass(Monster.class, coordinate.getAxisAlignedBB(range + 8));

            for (Object o : list) {
                Monster mob = (Monster) o;
                if (oldList == null || !oldList.contains(o)) {
                    if (coordinate.distanceTo(mob) < range) {
                        if (!(o instanceof ReplicatorEntity) && !(o instanceof WitherBoss) && !(o instanceof EnderMan)) {
                            mob.discard();
                            Utils.println("MonsterPopFreeProcess : Dead");
                        }
                    }
                }
            }
            oldList = list;
        }
    }

}
