package mods.eln.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.level.saveddata.SavedData;

public class ElnWorldStorage extends SavedData {

    private int dim;

    final static String key = "eln.worldStorage";

    public ElnWorldStorage(String str) {
        super(str);
    }

    public static ElnWorldStorage forWorld(Level world) {
        // Retrieves the MyWorldData instance for the given world, creating it if necessary
        MapStorage storage = world.getPerWorldStorage();
        int dim = world.provider.getDimension();
        ElnWorldStorage result = (ElnWorldStorage) storage.getOrLoadData(ElnWorldStorage.class, key + dim);
        if (result == null) {
            result = (ElnWorldStorage) storage.getOrLoadData(ElnWorldStorage.class, key + dim + "back");
        }
        if (result == null) {
            result = new ElnWorldStorage(key + dim);
            result.dim = dim;
            storage.setData(key + dim, result);
        }
        return result;
    }

    @Override
    public void readFromNBT(CompoundTag nbt) {
        dim = nbt.getInteger("dim");
        ServerEventListener.readFromEaWorldNBT(nbt);
    }

    @Override
    public CompoundTag writeToNBT(CompoundTag nbt) {
        nbt.setInteger("dim", dim);
        ServerEventListener.writeToEaWorldNBT(nbt, dim);
        return nbt;
    }

    @Override
    public boolean isDirty() {
        return true;
    }
}
