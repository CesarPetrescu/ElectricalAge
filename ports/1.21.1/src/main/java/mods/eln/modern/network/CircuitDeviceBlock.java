package mods.eln.modern.network;

import com.mojang.serialization.MapCodec;
import mods.eln.modern.ElectricalAgeModern;
import mods.eln.sim.network.GridTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;

/** Full-block prototype: front is positive, back negative; wire joins all six faces resistively. */
public final class CircuitDeviceBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private final GridTopology.Kind kind;
    private final MapCodec<CircuitDeviceBlock> codec;
    public CircuitDeviceBlock(Properties properties, GridTopology.Kind kind) {
        super(properties);
        this.kind = kind;
        this.codec = simpleCodec(p -> new CircuitDeviceBlock(p, kind));
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(LIT,false));
    }
    public GridTopology.Kind kind() { return kind; }
    @Override public MapCodec<CircuitDeviceBlock> codec() { return codec; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING,LIT); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING,context.getNearestLookingDirection().getOpposite()); }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) { return state.setValue(FACING,rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state,Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new CircuitDeviceBlockEntity(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        if(level.isClientSide || type!=ElectricalAgeModern.CIRCUIT_DEVICE_ENTITY.get())return null;
        // One manager step at LevelTick.Post. A heartbeat means the vanilla ticker actually ran;
        // loaded but non-ticking chunks are deliberately not simulated and never force-loaded.
        return (world,pos,currentState,entity)->LevelCircuitManager.touch((CircuitDeviceBlockEntity)entity);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(level.isClientSide)return InteractionResult.SUCCESS;
        if(!player.mayBuild() || !level.mayInteract(player,pos))return InteractionResult.PASS;
        if(level.getBlockEntity(pos) instanceof CircuitDeviceBlockEntity device) {
            if(player.isShiftKeyDown())device.reset();
            else if(kind==GridTopology.Kind.SOURCE)device.togglePower();
            player.displayClientMessage(Component.literal(device.measurementText()),true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
