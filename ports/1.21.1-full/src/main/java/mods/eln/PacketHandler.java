package mods.eln;

import mods.eln.misc.Coordinate;
import net.minecraft.core.BlockPos;
import io.netty.channel.ChannelHandler.Sharable;
import mods.eln.client.ClientKeyHandler;
import mods.eln.client.ClientProxy;
import mods.eln.misc.Utils;
import mods.eln.node.INodeEntity;
import mods.eln.node.NodeBase;
import mods.eln.node.NodeManager;
import mods.eln.server.PlayerManager;
import mods.eln.sound.SoundClient;
import mods.eln.sound.SoundCommand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.io.*;

@Sharable
public class PacketHandler {

    public PacketHandler() {
        // Eln.eventChannel.register(this);
    }


    public void packetRx(DataInputStream stream, Connection manager, Player player) {
        try {
            switch (stream.readByte()) {
                case Eln.packetPlayerKey:
                    packetPlayerKey(stream, manager, player);
                    break;
                case Eln.packetNodeSingleSerialized:
                    packetNodeSingleSerialized(stream, manager, player);
                    break;
                case Eln.packetPublishForNode:
                    packetForNode(stream, manager, player);
                    break;
                case Eln.packetForClientNode:
                    packetForClientNode(stream, manager, player);
                    break;
                case Eln.packetOpenLocalGui:
                    packetOpenLocalGui(stream, manager, player);
                    break;
                case Eln.packetPlaySound:
                    packetPlaySound(stream, manager, player);
                    break;
                case Eln.packetDestroyUuid:
                    packetDestroyUuid(stream, manager, player);
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void packetDestroyUuid(DataInputStream stream, Connection manager, Player player) {
        try {
            ClientProxy.uuidManager.kill(stream.readInt());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void packetPlaySound(DataInputStream stream, Connection manager, Player player) {
        try {
            if (stream.readByte() != player.dimension)
                return;
            SoundClient.play(SoundCommand.fromStream(stream, player.world));

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    void packetOpenLocalGui(DataInputStream stream, Connection manager, Player player) {
        Player clientPlayer = (Player) player;
        try {
            clientPlayer.openGui(Eln.instance, stream.readInt(),
                clientPlayer.world, stream.readInt(), stream.readInt(),
                stream.readInt());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void packetForNode(DataInputStream stream, Connection manager, Player player) {
        try {
            Coordinate coordinate = new Coordinate(stream.readInt(),
                stream.readInt(), stream.readInt(), stream.readByte());

            NodeBase node = NodeManager.instance.getNodeFromCoordinate(coordinate);
            if (node != null && node.getNodeUuid().equals(stream.readUTF())) {
                node.networkUnserialize(stream, (ServerPlayer) player);
            } else {
                Utils.println("packetForNode node found");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void packetForClientNode(DataInputStream stream, Connection manager, Player player) {
        Player clientPlayer = (Player) player;
        int x, y, z, dimension;
        try {

            x = stream.readInt();
            y = stream.readInt();
            z = stream.readInt();
            dimension = stream.readByte();


            if (clientPlayer.dimension == dimension) {
                BlockEntity entity = clientPlayer.world.getTileEntity(new BlockPos(x,y,z));
                if (entity != null && entity instanceof INodeEntity) {
                    INodeEntity node = (INodeEntity) entity;
                    if (node.getNodeUuid().equals(stream.readUTF())) {
                        node.serverPacketUnserialize(stream);
                        /*if (0 != stream.available()) {
                            Utils.println("0 != stream.available()");
                        }*/
                    } else {
                        // Utils.println("Wrong node UUID warning");
                        int dataSkipLength = stream.readByte();
                        for (int idx = 0; idx < dataSkipLength; idx++) {
                            stream.readByte();
                        }
                    }
                }
            } else {
                // Utils.println("No node found for " + x + " " + y + " " + z);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void packetNodeSingleSerialized(DataInputStream stream, Connection manager, Player player) {
        try {
            Player clientPlayer = player;
            int x, y, z, dimension;
            x = stream.readInt();
            y = stream.readInt();
            z = stream.readInt();
            dimension = stream.readByte();

            if (clientPlayer.dimension == dimension) {
                BlockEntity entity = clientPlayer.world.getTileEntity(new BlockPos(x,y,z));
                if (entity != null && entity instanceof INodeEntity) {
                    INodeEntity node = (INodeEntity) entity;
                    if (node.getNodeUuid().equals(stream.readUTF())) {
                        node.serverPublishUnserialize(stream);
                        if (0 != stream.available()) {
                            // Utils.println("0 != stream.available()");

                        }
                    } else {
                        // Utils.println("Wrong node UUID warning");
                        int dataSkipLength = stream.readByte();
                        for (int idx = 0; idx < dataSkipLength; idx++) {
                            stream.readByte();
                        }
                    }
                } else {
                    // Utils.println("No node found for " + x + " " + y + " " + z);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void packetPlayerKey(DataInputStream stream, Connection manager, Player player) {
        ServerPlayer playerMP = (ServerPlayer) player;
        byte id;
        try {
            id = stream.readByte();
            boolean state = stream.readBoolean();

            if (id == ClientKeyHandler.wrenchId) {
                PlayerManager.PlayerMetadata metadata = Eln.playerManager.get(playerMP);
                metadata.setInteractEnable(state);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
