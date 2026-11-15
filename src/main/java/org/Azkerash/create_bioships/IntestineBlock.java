package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

public class IntestineBlock extends Block {
  public static final BooleanProperty MANUAL = BooleanProperty.create("manual");
  public static final BooleanProperty PAID = BooleanProperty.create("paid");
  public static final BooleanProperty ENCASED = BooleanProperty.create("encased");
  public static final Map<Direction, BooleanProperty> CONNECTIONS =
      Map.of(
          Direction.DOWN,
          BlockStateProperties.DOWN,
          Direction.UP,
          BlockStateProperties.UP,
          Direction.NORTH,
          BlockStateProperties.NORTH,
          Direction.SOUTH,
          BlockStateProperties.SOUTH,
          Direction.WEST,
          BlockStateProperties.WEST,
          Direction.EAST,
          BlockStateProperties.EAST);

  public IntestineBlock(Properties properties) {
    super(properties);
    var s =
        stateDefinition
            .any()
            .setValue(ENCASED, false)
            .setValue(MANUAL, false)
            .setValue(PAID, false);
    for (var p : CONNECTIONS.values()) s = s.setValue(p, false);
    registerDefaultState(s);
  }

  public static boolean compatible(BlockState s) {
    if (s.is(Create_bioships.INTESTINE.get())
        || s.is(Create_bioships.FEEDING_MOUTH.get())
        || s.is(Create_bioships.STOMACH.get())) return true;
    String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
    return id.equals("biomancy:storage_sac") || id.equals("biomancy:maw_hopper");
  }

  public BlockState connections(BlockState s, BlockGetter level, BlockPos pos) {
    for (var e : CONNECTIONS.entrySet())
      s = s.setValue(e.getValue(), compatible(level.getBlockState(pos.relative(e.getKey()))));
    return s;
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext c) {
    return connections(
        defaultBlockState().setValue(MANUAL, true).setValue(PAID, true),
        c.getLevel(),
        c.getClickedPos());
  }

  @Override
  protected BlockState updateShape(
      BlockState s,
      Direction d,
      BlockState other,
      LevelAccessor level,
      BlockPos pos,
      BlockPos otherPos) {
    return s.setValue(CONNECTIONS.get(d), compatible(other));
  }

  @Override
  protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moving) {
    super.onPlace(s, level, pos, old, moving);
    if (!level.isClientSide) {
      var next = connections(s, level, pos);
      if (!next.equals(s)) level.setBlock(pos, next, 3);
    }
  }

  @Override
  protected VoxelShape getShape(
      BlockState s, BlockGetter level, BlockPos pos, CollisionContext context) {
    if (s.getValue(ENCASED)) return Shapes.block();
    var shape = box(5, 5, 5, 11, 11, 11);
    for (var e : CONNECTIONS.entrySet())
      if (s.getValue(e.getValue())) {
        var d = e.getKey();
        shape =
            Shapes.or(
                shape,
                box(
                    d == Direction.WEST ? 0 : 6,
                    d == Direction.DOWN ? 0 : 6,
                    d == Direction.NORTH ? 0 : 6,
                    d == Direction.EAST ? 16 : 10,
                    d == Direction.UP ? 16 : 10,
                    d == Direction.SOUTH ? 16 : 10));
      }
    return shape;
  }

  @Override
  protected net.minecraft.world.ItemInteractionResult useItemOn(
      net.minecraft.world.item.ItemStack stack,
      BlockState state,
      Level level,
      BlockPos pos,
      net.minecraft.world.entity.player.Player player,
      net.minecraft.world.InteractionHand hand,
      net.minecraft.world.phys.BlockHitResult hit) {
    if (!state.getValue(ENCASED)
        && stack.getItem() instanceof net.minecraft.world.item.BlockItem item
        && (item.getBlock() == Create_bioships.FLESH.get()
            || item.getBlock()
                .defaultBlockState()
                .is(
                    net.minecraft.tags.TagKey.create(
                        net.minecraft.core.registries.Registries.BLOCK,
                        net.minecraft.resources.ResourceLocation.parse(
                            "create_bioships:construction_flesh"))))) {
      if (!level.isClientSide) {
        level.setBlock(pos, state.setValue(ENCASED, true), 3);
        stack.consume(1, player);
      }
      return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
    b.add(ENCASED, MANUAL, PAID);
    for (var p : CONNECTIONS.values()) b.add(p);
  }
}
