package org.azkerash.createbioships.components.logic.legs;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.*;

/** Internal collision tissue for generated native limb sublevels; never a player item. */
public final class CompactLimbBlock
    extends org.azkerash.createbioships.components.logic.WaterloggedComponentBlock {
  public static final IntegerProperty BONE = IntegerProperty.create("bone", 0, 2);
  public static final IntegerProperty HEIGHT = IntegerProperty.create("height", 1, 3);
  public static final IntegerProperty FILL = IntegerProperty.create("fill", 1, 64);
  public static final MapCodec<CompactLimbBlock> CODEC = simpleCodec(CompactLimbBlock::new);

  public CompactLimbBlock(Properties p) {
    super(p);
    registerDefaultState(
        stateDefinition
            .any()
            .setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,
                false)
            .setValue(BONE, 0)
            .setValue(HEIGHT, 1)
            .setValue(FILL, 64));
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
    super.createBlockStateDefinition(b);
    b.add(BONE, HEIGHT, FILL);
  }

  @Override
  protected net.minecraft.world.ItemInteractionResult useItemOn(
      net.minecraft.world.item.ItemStack stack,
      BlockState state,
      net.minecraft.world.level.Level level,
      BlockPos pos,
      net.minecraft.world.entity.player.Player player,
      net.minecraft.world.InteractionHand hand,
      net.minecraft.world.phys.BlockHitResult hit) {
    return stack.getItem() instanceof net.minecraft.world.item.BlockItem
        ? net.minecraft.world.ItemInteractionResult.FAIL
        : net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
  }

  @Override
  protected RenderShape getRenderShape(BlockState s) {
    return RenderShape.INVISIBLE;
  }

  @Override
  protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    double w =
        org.azkerash.createbioships.compat.letitwalk.CompactLegCollisions.width(
            s.getValue(BONE), s.getValue(HEIGHT));
    return Shapes.box(.5 - w / 2, 0, .5 - w / 2, .5 + w / 2, s.getValue(FILL) / 64d, .5 + w / 2);
  }
}
