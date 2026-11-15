package org.Azkerash.create_bioships;

import dev.simulated_team.simulated.util.QuietUse;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** An organic muscle handle, using Simulated's physical hold-and-drag interaction. */
public class LivingThrottleBlock extends Block implements QuietUse {
    public static final IntegerProperty THROTTLE = IntegerProperty.create("throttle", 0, 20);
    public static final int NEUTRAL = 10;
    public enum Kind { THRUST, VERTICAL }
    private final Kind kind;
    public LivingThrottleBlock(Properties properties) { this(properties, Kind.THRUST); }
    public LivingThrottleBlock(Properties properties, Kind kind) {
        super(properties); this.kind = kind; registerDefaultState(stateDefinition.any().setValue(THROTTLE, NEUTRAL));
    }
    public Kind kind() { return kind; }
    public static double signed(BlockState state) { return (state.getValue(THROTTLE) - NEUTRAL) / 10.0; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(THROTTLE); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Block.box(3, 0, 2, 13, 12, 14); }
    @Override public InteractionResult quietUse(Player player, InteractionHand hand, BlockPos pos, BlockState state) {
        if (hand != InteractionHand.MAIN_HAND) return null;
        org.Azkerash.create_bioships.client.BioshipClient.startControl(player.level(), player, pos);
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player.isShiftKeyDown()) level.setBlock(pos, state.setValue(THROTTLE, NEUTRAL), 3);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
