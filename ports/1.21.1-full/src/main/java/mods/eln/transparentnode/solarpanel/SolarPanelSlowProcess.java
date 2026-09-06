package mods.eln.transparentnode.solarpanel;

import mods.eln.misc.Coordinate;
import mods.eln.misc.Utils;
import mods.eln.sim.IProcess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

@SuppressWarnings("SuspiciousNameCombination")
public class SolarPanelSlowProcess implements IProcess {
    SolarPanelSlowProcess(SolarPanelElement solarPanel) {
        this.solarPanel = solarPanel;
    }

    private SolarPanelElement solarPanel;
    private double timeCounter = 0;

    @Override
    public void process(double time) {
        double timeCounterRefreshMax = 0.2;
        timeCounter -= time;
        if (timeCounter < 0) {
            //Utils.println("Solar Light : " + getSolarLight());
            /*if(solarPanel.descriptor.basicModel == false)
			{
				solarPanel.currentSource.setI(solarPanel.descriptor.solarIfS.getValue(getSolarLight()));
			}
			else*/
            {
                solarPanel.powerSource.setP(solarPanel.descriptor.electricalPmax * getSolarLight());
            }
            timeCounter += Math.random() * timeCounterRefreshMax / 2 + timeCounterRefreshMax / 2;
        }

    }

    double getSolarLight() {
        double solarAlpha = getSolarAlpha();
        //	Utils.print("solarAlpha : " + solarAlpha + "  ");
        if (solarAlpha >= Math.PI) return 0.0;

        ItemStack trackerStack = solarPanel.getInventory().getItem(SolarPanelContainer.trackerSlotId);
        if (trackerStack != null && !trackerStack.isEmpty()) {
            solarPanel.panelAlpha = solarPanel.descriptor.alphaTrunk(solarAlpha);
        }


        Coordinate coordinate = solarPanel.node.coordinate;
        Vec3 v = Utils.getVec05(coordinate);
        double x = v.x + solarPanel.descriptor.solarOffsetX, y = v.y + solarPanel.descriptor.solarOffsetY, z = v.z + solarPanel.descriptor.solarOffsetZ;


        double lightAlpha = solarPanel.panelAlpha - solarAlpha;
        double light = Math.cos(lightAlpha);


        if (light < 0.0) light = 0.0;

        if (!coordinate.doesWorldExist()) return light;

        Level world = coordinate.world();
        if (world.getWorldInfo().isRaining()) light *= 0.5;
        if (world.getWorldInfo().isThundering()) light *= 0.5;


        double xD = Math.cos(solarAlpha), yD = Math.sin(solarAlpha);

        if (Math.abs(xD) > yD) {
            xD = Math.signum(xD);
            yD /= Math.abs(xD);
        } else {
            yD = 1.0;
            xD /= yD;
        }
        int iterationLimit = 256;
        while (y <= 256.0 && iterationLimit-- > 0) {
            BlockPos pos = new BlockPos((int) x, (int) y, (int) z);
            net.minecraft.world.level.chunk.LevelChunk chunk = world.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null || chunk.isEmpty()) break;
            double opacity = chunk.getBlockLightOpacity(pos);
            light *= (255 - opacity) / 255;
            if (light == 0.0) {
                break;
            }

            x += xD;
            y += yD;
        }
        return light;
    }

    static double getSolarAlpha(Level world) {
        double alpha = world.getCelestialAngleRadians(0f);
        if (alpha < Math.PI / 2 * 3) {
            alpha += Math.PI / 2;
        } else {
            alpha -= Math.PI / 2 * 3;
        }
        //return ((((world.getWorldTime()%24000)*12.0/13.0 +500 ) / 24000.0) )% 1.0 * Math.PI*2;
        return alpha;
    }

    double getSolarAlpha() {
        return getSolarAlpha(solarPanel.node.coordinate.world());
    }
}
