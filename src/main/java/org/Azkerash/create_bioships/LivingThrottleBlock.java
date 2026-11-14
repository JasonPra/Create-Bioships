package org.Azkerash.create_bioships;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Thin luminous panel; a signed slider controls full reverse through neutral to full ahead. */
public class LivingThrottleBlock extends Block {
    public static final IntegerProperty THROTTLE = IntegerProperty.create("throttle", 0, 20);
    public static final int NEUTRAL = 10;
    public LivingThrottleBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(THROTTLE, NEUTRAL)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(THROTTLE); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Block.box(1, 0, 1, 15, 1, 15); }
    public static double signed(BlockState state) { return (state.getValue(THROTTLE) - NEUTRAL) / 10.0; }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server) {
            if (player.isShiftKeyDown()) level.setBlock(pos, state.setValue(THROTTLE, NEUTRAL), 3);
            else BioshipNetwork.openThrottle(server, pos, state.getValue(THROTTLE));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
