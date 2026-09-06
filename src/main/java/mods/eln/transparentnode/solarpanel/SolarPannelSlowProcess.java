package mods.eln.transparentnode.solarpanel;

import mods.eln.misc.McBridge;
import mods.eln.misc.Coordinate;
import mods.eln.misc.Utils;
import mods.eln.sim.IProcess;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

public class SolarPannelSlowProcess implements IProcess {
    SolarPanelElement solarPannel;

    public SolarPannelSlowProcess(SolarPanelElement solarPannel) {
        this.solarPannel = solarPannel;
    }

    double timeCounter = 0;
    final double timeCounterRefreshMax = 0.2;

    @Override
    public void process(double time) {
        timeCounter -= time;
        if (timeCounter < 0) {
            //Utils.println("Solar Light : " + getSolarLight());
            /*if(solarPannel.descriptor.basicModel == false)
			{
				solarPannel.currentSource.setI(solarPannel.descriptor.solarIfS.getValue(getSolarLight()));
			}
			else*/
            {
                solarPannel.powerSource.setLightFactor(getSolarLight());
            }
            timeCounter += Math.random() * timeCounterRefreshMax / 2 + timeCounterRefreshMax / 2;
        }

    }

    public double getSolarLight() {
        double solarAlpha = getSolarAlpha();
        //	Utils.print("solarAlpha : " + solarAlpha + "  ");
        if (solarAlpha >= Math.PI) return 0.0;

        if (solarPannel.getInventory().getItem(SolarPanelContainer.trackerSlotId) != null) {
            solarPannel.panelAlpha = solarPannel.descriptor.alphaTrunk(solarAlpha);
        }


        Coordinate coordinate = solarPannel.node.coordinate;
        Vec3 v = Utils.getVec05(coordinate);
        double x = v.x + solarPannel.descriptor.solarOffsetX, y = v.y + solarPannel.descriptor.solarOffsetY, z = v.z + solarPannel.descriptor.solarOffsetZ;


        double lightAlpha = solarPannel.panelAlpha - solarAlpha;
        double light = Math.cos(lightAlpha);


        if (light < 0.0) light = 0.0;

        if (coordinate.getWorldExist() == false) return light;

        Level world = coordinate.world();
        if (world.getLevelData().isRaining()) light *= 0.5;
        if (world.getLevelData().isThundering()) light *= 0.5;


        double xD = Math.cos(solarAlpha), yD = Math.sin(solarAlpha);

        if (Math.abs(xD) > yD) {
            xD = Math.signum(xD);
            yD /= Math.abs(xD);
        } else {
            yD = 1.0;
            xD /= yD;
        }
        int count = 0;
        ///world.getChunkProvider().chunkExists(var1, var2)
        while (world.isLoaded(new BlockPos((int) x, (int) y, (int) z))) {
            double opacity = McBridge.getBlockState(world, (int) x, (int) y, (int) z).getLightBlock(world, new BlockPos((int) x, (int) y, (int) z));
            light *= (255 - opacity) / 255;
            if (light == 0.0) {
                break;
            }

            x += xD;
            y += yD;
            count++;
            if (y > 256.0) break;
        }
//		Utils.print("count : " + count + "   ");
        return light;
    }

    public static double getSolarAlpha(Level world) {
        double alpha = world.getSunAngle(0f);
        if (alpha < Math.PI / 2 * 3) {
            alpha += Math.PI / 2;
        } else {
            alpha -= Math.PI / 2 * 3;
        }
        //return ((((world.getDayTime()%24000)*12.0/13.0 +500 ) / 24000.0) )% 1.0 * Math.PI*2;
        return alpha;
    }

    public double getSolarAlpha() {
        return getSolarAlpha(solarPannel.node.coordinate.world());
    }
}
