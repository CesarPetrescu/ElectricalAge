package mods.eln.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;

public interface IConfigurable {
    void readConfigTool(CompoundTag compound, Player invoker);
    void writeConfigTool(CompoundTag compound, Player invoker);
}
