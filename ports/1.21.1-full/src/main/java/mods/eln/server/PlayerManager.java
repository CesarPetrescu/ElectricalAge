package mods.eln.server;

import mods.eln.misc.Utils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent;

import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;

public class PlayerManager {

    private Map<ServerPlayer, PlayerMetadata> metadataHash = new Hashtable<ServerPlayer, PlayerMetadata>();

    public PlayerManager() {
        NeoForge.EVENT_BUS.register(this);
    }

    public class PlayerMetadata {
        private int timeout;
        public boolean interactEnable = false;
        public boolean interactRise = false, interactRiseBuffer = false;
        Player player;

        public PlayerMetadata(Player p) {
            timeoutReset();
            this.player = p;
        }

        public boolean needDelete() {
            return timeout == 0;
        }

        public void timeoutReset() {
            timeout = 20 * 120;
        }

        public void timeoutDec() {
            timeout--;
            if (timeout < 0)
                timeout = 0;
        }

        public void setInteractEnable(boolean interactEnable) {
            if (!this.interactEnable && interactEnable) {
                interactRiseBuffer = true;
                Utils.println("interactRiseBuffer");
            }
            this.interactEnable = interactEnable;

            timeoutReset();
            Utils.println("interactEnable : " + interactEnable);
        }

        public boolean getInteractEnable() {
            timeoutReset();
            return interactEnable;
            //return player.isSneaking();
        }

		/*public boolean getInteractRise() {
            timeoutReset();
			return interactRise;
		}*/
    }

    public void clear() {
        metadataHash.clear();
    }

    @SubscribeEvent
    public void tick(ServerTickEvent event) {
        if (event.phase != Phase.START) return;
        for (Entry<ServerPlayer, PlayerMetadata> entry : metadataHash.entrySet()) {
            PlayerMetadata p = entry.getValue();

            p.interactRise = p.interactRiseBuffer;
            p.interactRiseBuffer = false;

            if (p.needDelete()) {
                metadataHash.remove(entry.getKey());
            }
        }
    }

    public PlayerMetadata get(ServerPlayer player) {
        PlayerMetadata metadata = metadataHash.get(player);
        if (metadata != null)
            return metadata;
        metadataHash.put(player, new PlayerMetadata(player));
        return metadataHash.get(player);
    }

    public PlayerMetadata get(Player player) {
        return get((ServerPlayer) player);
    }
}
