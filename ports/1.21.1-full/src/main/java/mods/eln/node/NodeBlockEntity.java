package mods.eln.node;


import mods.eln.Eln;
import mods.eln.cable.CableRenderDescriptor;
import mods.eln.misc.*;
import mods.eln.server.DelayedBlockRemove;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.util.ITickable;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;

import javax.annotation.Nullable;
import java.io.*;
import java.util.LinkedList;


public abstract class NodeBlockEntity extends BlockEntity implements ITileEntitySpawnClient, INodeEntity, ITickable {

    public static final LinkedList<NodeBlockEntity> clientList = new LinkedList<NodeBlockEntity>();


    public NodeBlock getBlock() {
        return (NodeBlock) getBlockType();
    }

    boolean redstone = false;
    int lastLight = 0xFF;
    boolean firstUnserialize = true;
    boolean firstUpdate = true;

    @Override
    public void update() {
        if (firstUpdate) {
            firstUpdate = false;
            if (!world.isClientSide) {
                // Reset light map on first update to fix reload issues
                world.setLightFor(LightLayer.BLOCK, pos, 0);
                Node node = getNode();
                if (node != null) {
                    node.forceLightValueUpdate();
                }
            } else {
                clientList.add(this);
            }
        }
    }

    @Override
    public void serverPublishUnserialize(DataInputStream stream) {

        int light = 0;
        try {
            if (firstUnserialize) {
                firstUnserialize = false;
            }
            Byte b = stream.readByte();
            light = b & 0xF;
            boolean newRedstone = (b & 0x10) != 0;
            if (redstone != newRedstone) {
                redstone = newRedstone;
                world.notifyNeighborsRespectDebug(getPos(), getBlockType(), true);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        if (lastLight != light) {
            lastLight = light;
            world.checkLightFor(LightLayer.BLOCK, getPos());
        }

        // Always notify neighbors so adjacent cables redraw their connections
        Utils.notifyNeighbor(this);
    }

    @Override
    public void serverPacketUnserialize(DataInputStream stream) {

    }


    //abstract public Node newNode();
    //abstract public Node newNode(Direction front,EntityLiving entityLiving,int metadata);

    public abstract int isProvidingWeakPower(Direction side);
    //{
    //if(world.isRemote) return 0;
    //return getNode().isProvidingWeakPower(side);
    //}

    Node node = null;

    @Override
    public AbstractContainerMenu newContainer(Direction side, Player player) {
        return null;
    }

    @Override
    public Screen newGuiDraw(Direction side, Player player) {
        return null;
    }


    public NodeBlockEntity() {
    }


    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    public AABB getRenderBoundingBox() {
        if (cameraDrawOptimisation()) {
            // TODO(1.10): This may not be correct.
            return new AABB(pos);
        } else {
            return INFINITE_EXTENT_AABB;
        }
    }

    public boolean cameraDrawOptimisation() {
        return true;
    }

    public int getLightValue() {
        if (world.isClientSide) {
            if (lastLight == 0xFF) {
                return 0;
            }
            return lastLight;
        } else {
            Node node = getNode();
            if (node == null) return 0;
            return getNode().getLightValue();
        }
    }

    /**
     * Reads a tile entity fromFacing NBT.
     */
    public void readFromNBT(CompoundTag nbt) {
        super.readFromNBT(nbt);
    }

    /**
     * Writes a tile entity to NBT.
     */
    public CompoundTag writeToNBT(CompoundTag nbt) {
        return super.writeToNBT(nbt);
    }


    //max draw distance
    @Override
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    public double getMaxRenderDistanceSquared() {
        return 4096.0 * (4) * (4);
    }


    void onBlockPlacedBy(Direction front, LivingEntity entityLiving, BlockState state) {

    }


    public void onBlockAdded() {
        if (!world.isClientSide && getNode() == null) {
            world.setBlockToAir(pos);
        }
    }

    public void onBreakBlock() {
        if (!world.isClientSide) {
            if (getNode() == null) return;
            getNode().onBreakBlock();
        }
    }

    public void onChunkUnload() {
        if (world.isClientSide) {
            destructor();
        }
    }

    //client only
    public void destructor() {
        clientList.remove(this);
    }

    @Override
    public void invalidate() {

        if (world.isClientSide) {
            destructor();
        }
        super.invalidate();
    }

    public boolean onBlockActivated(Player entityPlayer, Direction side, float vx, float vy, float vz) {
        if (!world.isClientSide) {
            if (getNode() == null) return false;
            return getNode().onBlockActivated(entityPlayer, side, vx, vy, vz);
        }
        // On client side, always return true to prevent block use (like placing blocks)
        return true;
    }

    public void onNeighborBlockChange() {
        if (!world.isClientSide) {
            if (getNode() == null) return;
            getNode().onNeighborBlockChange();
        }
    }


    public Node getNode() {
        if (world.isClientSide) {
            Utils.fatal();
            return null;
        }
        if (this.world == null) return null;
        if (node == null) {
            NodeBase nodeFromCoordinate = NodeManager.instance.getNodeFromCoordinate(new Coordinate(pos, world));
            if (nodeFromCoordinate instanceof Node) {
                node = (Node) nodeFromCoordinate;
            } else {
                Utils.println("ASSERT WRONG TYPE public Node getNode " + new Coordinate(pos, world));
            }
            // Don't add to DelayedBlockRemove if NodeManager just hasn't loaded yet
            // Only add if NodeManager exists but doesn't have this node
            if (node == null && NodeManager.instance != null && NodeManager.instance.getNodes().size() > 0) {
                DelayedBlockRemove.add(new Coordinate(pos, this.world));
            }
        }
        return node;
    }


    public static NodeBlockEntity getEntity(BlockPos pos) {
        BlockEntity entity;
        if ((entity = Minecraft.getInstance().world.getTileEntity(pos)) != null) {
            if (entity instanceof NodeBlockEntity) {
                return (NodeBlockEntity) entity;
            }
        }
        return null;
    }

    // TODO(1.10): Packets are probably still broken somehow!
    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        Node node = getNode();
        if (node == null) {
            Utils.println("ASSERT NULL NODE public Packet getDescriptionPacket() nodeblock entity at " + pos);
            // Return a packet anyway to sync the TileEntity existence
            return new ClientboundBlockEntityDataPacket(getPos(), getBlockMetadata(), new CompoundTag());
        }

        CompoundTag tagCompound = new CompoundTag();
        tagCompound.putByteArray("eln", node.getPublishPacket().toByteArray());
        return new ClientboundBlockEntityDataPacket(
            getPos(),
            getBlockMetadata(),
            tagCompound
        );
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        Node node = getNode();
        if (node != null) {
            tag.putByteArray("eln", node.getPublishPacket().toByteArray());
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        if (tag.contains("eln")) {
            byte[] bytes = tag.getByteArray("eln");
            if (bytes.length > 0 && world.isClientSide) {
                Minecraft.getInstance().addScheduledTask(() -> {
                    DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(bytes));
                    Eln.packetHandler.packetRx(dataInputStream, null, Minecraft.getInstance().player);
                });
            } else if (bytes.length > 0) {
                DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(bytes));
                Eln.packetHandler.packetRx(dataInputStream, null, null);
            }
        }
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (world.isClientSide) {
            CompoundTag tag = pkt.getNbtCompound();
            if (tag.contains("eln")) {
                byte[] bytes = tag.getByteArray("eln");
                if (bytes.length > 0) {
                    Minecraft.getInstance().addScheduledTask(() -> {
                        DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(bytes));
                        Eln.packetHandler.packetRx(dataInputStream, net, Minecraft.getInstance().player);
                    });
                }
            }
        }
    }

    public void preparePacketForServer(DataOutputStream stream) {
        try {
            stream.writeByte(Eln.packetPublishForNode);

            stream.writeInt(pos.getX());
            stream.writeInt(pos.getY());
            stream.writeInt(pos.getZ());

            stream.writeByte(world.provider.getDimension());

            stream.writeUTF(getNodeUuid());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendPacketToServer(ByteArrayOutputStream bos) {
        UtilsClient.sendPacketToServer(bos);
    }


    public CableRenderDescriptor getCableRender(Direction side, LRDU lrdu) {
        return null;
    }

    public int getCableDry(Direction side, LRDU lrdu) {
        return 0;
    }

    public boolean canConnectRedstone(Direction xn) {
        if (world.isClientSide)
            return redstone;
        else {
            if (getNode() == null) return false;
            return getNode().canConnectRedstone();
        }
    }

    public void clientRefresh(float deltaT) {

    }
}
