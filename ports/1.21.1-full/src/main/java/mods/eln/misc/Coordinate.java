package mods.eln.misc;

import mods.eln.node.NodeBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;

public class Coordinate implements INBTTReady {

    @Nonnull
    public BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(0, 0, 0);
    private int dimension = 0;
    private Level w = null;

    public Coordinate() {
    }

    public Coordinate(Coordinate coord) {
        BlockPos o = coord.pos;
        pos.setPos(o.getX(), o.getY(), o.getZ());
        dimension = coord.dimension;
    }

    public Coordinate(CompoundTag nbt, String str) {
        readFromNBT(nbt, str);
    }

    public Coordinate(NodeBlockEntity entity) {
        BlockPos o = entity.getPos();
        pos.setPos(o.getX(), o.getY(), o.getZ());
        dimension = entity.getWorld().provider.getDimension();
    }

    public Coordinate(@Nonnull BlockPos o, int dimension) {
        pos.setPos(o.getX(), o.getY(), o.getZ());
        this.dimension = dimension;
    }

    public Coordinate(@Nonnull BlockPos o, @Nonnull Level w) {
        pos.setPos(o.getX(), o.getY(), o.getZ());
        this.dimension = w.provider.getDimension();
    }

    public Coordinate(int x, int y, int z, int dimension) {
        pos.setPos(x, y, z);
        this.dimension = dimension;
    }

    public Coordinate(int x, int y, int z, Level world) {
        pos.setPos(x, y, z);
        dimension = world.provider.getDimension();
        if (world.isClientSide)
            this.w = world;
    }

    public Coordinate(BlockEntity entity) {
        BlockPos o = entity.getPos();
        pos.setPos(o.getX(), o.getY(), o.getZ());
        dimension = entity.getWorld().provider.getDimension();
        if (entity.getWorld().isRemote)
            this.w = entity.getWorld();
    }

    @Override
    public int hashCode() {
        return pos.hashCode() * 31 + dimension;
    }


    public int getDimension() {
        return dimension;
    }

    public Level world() {
        if (w == null) {
            w = DimensionManager.getWorld(getDimension());
        }
        return w;
    }

    public Coordinate newWithOffset(int x, int y, int z) {
        return new Coordinate(pos.add(x, y, z), dimension);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Coordinate)) return false;
        Coordinate id = (Coordinate) obj;
        return id.pos.equals(pos) && id.dimension == dimension;
    }

    @Override
    public void readFromNBT(CompoundTag nbt, String str) {
        int x = nbt.getInt(str + "x");
        int y = nbt.getInt(str + "y");
        int z = nbt.getInt(str + "z");
        pos.setPos(x, y, z);
        dimension = nbt.getInt(str + "d");
    }

    @Override
    public CompoundTag writeToNBT(CompoundTag nbt, String str) {
        nbt.putInt(str + "x", pos.getX());
        nbt.putInt(str + "y", pos.getY());
        nbt.putInt(str + "z", pos.getZ());
        nbt.putInt(str + "d", dimension);
        return nbt;
    }

    @Override
    public String toString() {
        return "X : " + pos.getX() + " Y : " + pos.getY() + " Z : " + pos.getZ() + " D : " + dimension;
    }

    public void move(Direction facing) {
        pos.move(facing.toForge());
    }

    public Coordinate moved(final Direction direction) {
        Coordinate moved = new Coordinate(this);
        moved.move(direction);
        return moved;
    }

    public static AABB getAxisAlignedBB(Coordinate a, Coordinate b) {
        return new AABB(a.pos, b.pos);
    }

    public AABB getAxisAlignedBB(int ray) {
        return new AABB(
            new BlockPos(pos.getX() - ray, pos.getY() - ray, pos.getZ() - ray),
            new BlockPos(pos.getX() + ray + 1, pos.getY() + ray + 1, pos.getZ() + ray + 1));
    }

    public double distanceTo(Entity e) {
        return Math.abs(e.posX - (pos.getX() + 0.5)) + Math.abs(e.posY - (pos.getY() + 0.5)) + Math.abs(e.posZ - (pos.getZ() + 0.5));
    }

    public boolean doesBlockExist() {
        return world().isBlockLoaded(pos);
    }

    public boolean doesWorldExist() {
        return DimensionManager.getWorld(dimension) != null;
    }

    public void setPosition(double[] vp) {
        pos.setPos(vp[0], vp[1], vp[2]);
    }

    public void setPosition(Vec3 vp) {
        pos.setPos(vp.x, vp.y, vp.z);
    }

    public BlockEntity getTileEntity() {
        return world().getTileEntity(pos);
    }

    public double trueDistanceTo(Coordinate c) {
        return c.pos.getDistance(pos.getX(), pos.getY(), pos.getZ());
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
        w = null;
    }

    public void copyFrom(Coordinate c) {
        pos.setPos(c.pos.getX(), c.pos.getY(), c.pos.getZ());
        this.dimension = c.dimension;
    }

    public void applyTransformation(Direction front, Coordinate coordinate) {
        front.rotateFromXN(this);
        BlockPos o = coordinate.pos;
        pos.setPos(pos.getX() + o.getX(),
        pos.getY() + o.getY(),
        pos.getZ() + o.getZ());
    }

    public void setWorld(Level world) {
        if (world.isClientSide)
            w = world;
        dimension = world.provider.getDimension();
    }

    public void setBlock(Block b) {
        world().setBlockState(pos, b.getDefaultState());
    }

    public boolean isAir() {
        return world().isAirBlock(pos);
    }

    public BlockState getBlockState() {
        return world().getBlockState(pos);
    }

    public Block getBlock() { return getBlockState().getBlock(); }

    public int getMeta(){
        return Utils.getMetaFromPos(this);
    }

    public Coordinate subtract(Coordinate b) {
        return newWithOffset(-b.pos.getX(), -b.pos.getY(), -b.pos.getZ());
    }
    public Coordinate negate() {
        return new Coordinate(-pos.getX(), -pos.getY(), -pos.getZ(), dimension);
    }

}
