package org.Azkerash.create_bioships.components.logic.nametag;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import org.Azkerash.create_bioships.components.logic.WaterloggedComponentBlock;
import org.Azkerash.create_bioships.core.CoreAssemblyRules;
import org.Azkerash.create_bioships.core.naming.SubLevelNames;

/**
 * A six-face, waterlogged mounting panel. Hologram coordinates do not rotate with its mounting
 * face.
 */
public final class NameTagDisplayBlock extends WaterloggedComponentBlock
    implements EntityBlock,
        BlockSubLevelAssemblyListener,
        com.simibubi.create.content.equipment.wrench.IWrenchable {
  public static final MapCodec<NameTagDisplayBlock> CODEC = simpleCodec(NameTagDisplayBlock::new);

  public NameTagDisplayBlock(Properties p) {
    super(p);
    registerDefaultState(defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
    super.createBlockStateDefinition(b);
    b.add(BlockStateProperties.FACING);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext c) {
    var s =
        placed(defaultBlockState().setValue(BlockStateProperties.FACING, c.getClickedFace()), c);
    return s.canSurvive(c.getLevel(), c.getClickedPos()) ? s : null;
  }

  @Override
  protected boolean canSurvive(BlockState s, LevelReader l, BlockPos p) {
    var f = s.getValue(BlockStateProperties.FACING);
    var support = p.relative(f.getOpposite());
    return l.getBlockState(support).isFaceSturdy(l, support, f);
  }

  @Override
  protected BlockState updateShape(
      BlockState s, Direction d, BlockState other, LevelAccessor l, BlockPos p, BlockPos q) {
    if (!s.canSurvive(l, p)) {
      l.scheduleTick(p, this, 1);
    }
    return super.updateShape(s, d, other, l, p, q);
  }

  @Override
  protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource random) {
    if (!s.canSurvive(l, p)) {
      l.destroyBlock(p, true);
    }
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
    return new NameTagDisplayEntity(p, s);
  }

  @Override
  protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    return switch (s.getValue(BlockStateProperties.FACING)) {
      case SOUTH -> box(2, 2, 0, 14, 14, 3);
      case NORTH -> box(2, 2, 13, 14, 14, 16);
      case EAST -> box(0, 2, 2, 3, 14, 14);
      case WEST -> box(13, 2, 2, 16, 14, 14);
      case UP -> box(2, 0, 2, 14, 3, 14);
      case DOWN -> box(2, 13, 2, 14, 16, 14);
    };
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
    if (!(l.getBlockEntity(p) instanceof NameTagDisplayEntity display)) {
      return InteractionResult.FAIL;
    }
    if (!l.isClientSide && !display.editable(player)) {
      return InteractionResult.FAIL;
    }
    if (player instanceof ServerPlayer server) {
      server.openMenu(
          display,
          buffer -> {
            var offset = display.offset();
            buffer.writeVarInt(offset.getX());
            buffer.writeVarInt(offset.getY());
            buffer.writeVarInt(offset.getZ());
          });
    }
    return InteractionResult.sidedSuccess(l.isClientSide);
  }

  @Override
  protected ItemInteractionResult useItemOn(
      ItemStack stack,
      BlockState s,
      Level l,
      BlockPos p,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    if (stack.getItem() instanceof com.simibubi.create.content.equipment.wrench.WrenchItem) {
      return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }
    if (NameTagDisplayEntity.accepts(stack)
        && l.getBlockEntity(p) instanceof NameTagDisplayEntity display
        && display.isEmpty()) {
      if (!l.isClientSide) {
        if (!display.editable(player)) {
          return ItemInteractionResult.FAIL;
        }
        display.setItem(0, stack.copyWithCount(1));
        if (!player.getAbilities().instabuild) {
          stack.shrink(1);
        }
      }
      return ItemInteractionResult.sidedSuccess(l.isClientSide);
    }
    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
  }

  @Override
  protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
    if (!s.is(next.getBlock())
        && !l.isClientSide
        && l.getBlockEntity(p) instanceof NameTagDisplayEntity display) {
      var at = Vec3.atCenterOf(p);
      if (l instanceof ServerLevel server) {
        var b = CoreAssemblyRules.containing(server, p);
        if (b != null) {
          at = b.logicalPose().transformPosition(at);
        }
      }
      Containers.dropContents(l, BlockPos.containing(at), display);
      display.clearContent();
    }
    super.onRemove(s, l, p, next, moving);
  }

  @Override
  public BlockState getRotatedBlockState(BlockState s, Direction face) {
    return s;
  }

  @Override
  public void afterMove(
      ServerLevel from, ServerLevel to, BlockState state, BlockPos p, BlockPos q) {
    var b = CoreAssemblyRules.containing(to, q);
    if (b != null) {
      SubLevelNames.changed(b, q, state);
    }
  }
}
