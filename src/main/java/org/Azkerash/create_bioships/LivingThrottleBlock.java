package org.Azkerash.create_bioships;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** A physical two-lobed throttle: left decreases, right increases, sneak stops. */
public class LivingThrottleBlock extends Block {
    public static final IntegerProperty THROTTLE = IntegerProperty.create("throttle", 0, 5);
    public LivingThrottleBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(THROTTLE, 0)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(THROTTLE); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Block.box(1, 0, 2, 15, 8, 14); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int change = hit.getLocation().x - pos.getX() < 0.5 ? -1 : 1;
            int throttle = player.isShiftKeyDown() ? 0 : Math.clamp(state.getValue(THROTTLE) + change, 0, 5);
            level.setBlock(pos, state.setValue(THROTTLE, throttle), 3);
            player.displayClientMessage(Component.translatable("message.create_bioships.throttle", throttle * 20), true);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.LEVER_CLICK, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 0.6f + throttle * 0.1f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
