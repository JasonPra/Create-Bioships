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

/** An organic wheel with seven rudder positions, retained until the captain changes it. */
public class LivingHelmBlock extends Block {
  public static final IntegerProperty STEERING = IntegerProperty.create("steering", 0, 6);

  public LivingHelmBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(STEERING, 3));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(STEERING);
  }

  @Override
  protected VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return Block.box(1, 0, 4, 15, 16, 12);
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (!AirshipPrototype.available()) return InteractionResult.PASS;
    if (!level.isClientSide) {
      double x = hit.getLocation().x - pos.getX();
      int steering =
          player.isShiftKeyDown() || (x >= 0.4 && x <= 0.6)
              ? 3
              : Math.clamp(state.getValue(STEERING) + (x < 0.5 ? -1 : 1), 0, 6);
      level.setBlock(pos, state.setValue(STEERING, steering), 3);
      player.displayClientMessage(
          Component.translatable(
              "message.create_bioships.helm",
              Component.translatable(
                  "helm.create_bioships."
                      + (steering < 3 ? "port" : steering > 3 ? "starboard" : "center")),
              Math.abs(steering - 3)),
          true);
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
