package mods.eln.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public interface IInteract {
    abstract public void interact(ServerPlayer playerMP, ItemStack itemStack, byte param);
}
