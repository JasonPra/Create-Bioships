package org.azkerash.createbioships.components.logic.brain;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.azkerash.createbioships.core.*;
import org.azkerash.createbioships.core.brain.SubLevelBrains;

/** Brain tissue and its movable access face. The block stores no inventory or behavior data. */
public final class BrainBlock
    extends org.azkerash.createbioships.components.logic.WaterloggedComponentBlock
    implements net.minecraft.world.level.block.EntityBlock,
        BlockSubLevelAssemblyListener,
        com.simibubi.create.content.equipment.wrench.IWrenchable {
  public enum Port implements StringRepresentable {
    NONE(null),
    DOWN(Direction.DOWN),
    UP(Direction.UP),
    NORTH(Direction.NORTH),
    SOUTH(Direction.SOUTH),
    WEST(Direction.WEST),
    EAST(Direction.EAST);
    private final Direction face;

    Port(Direction face) {
      this.face = face;
    }

    public String getSerializedName() {
      return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static Port of(Direction face) {
      for (var port : values()) {
        if (port.face == face) {
          return port;
        }
      }
      return NONE;
    }

    public Direction getFace() {
      return this.face;
    }
  }

  public static final net.minecraft.world.level.block.state.properties.BooleanProperty FORMED =
      net.minecraft.world.level.block.state.properties.BooleanProperty.create("formed");
  public static final EnumProperty<Port> PORT = EnumProperty.create("port", Port.class);
  public static final MapCodec<BrainBlock> CODEC = simpleCodec(BrainBlock::new);

  public BrainBlock(Properties properties) {
    super(properties);
    registerDefaultState(
        stateDefinition
            .any()
            .setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,
                false)
            .setValue(PORT, Port.NONE)
            .setValue(FORMED, false));
  }

  @Override
  public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
      BlockPos pos, BlockState state) {
    return new BrainLimbDependencies(pos, state);
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    super.createBlockStateDefinition(builder);
    builder.add(PORT, FORMED);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    var level = context.getLevel();
    var pos = context.getClickedPos();
    if (level instanceof ServerLevel server) {
      var body = CoreAssemblyRules.containing(server, pos);
      if (body != null && !SubLevelBrains.get(body).canAdd(pos)) {
        return null;
      }
    } else if (!org.azkerash.createbioships.client.components.BrainPlacementView.canPlace(
        level, pos)) {
      return null;
    }
    return placed(defaultBlockState(), context);
  }

  private static void changed(Level level, BlockPos pos) {
    if (level instanceof ServerLevel server) {
      SubLevelBrains.request(CoreAssemblyRules.containing(server, pos));
    } else {
      org.azkerash.createbioships.client.components.BrainPlacementView.invalidate(level, pos);
    }
  }

  @Override
  protected void onPlace(
      BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
    super.onPlace(state, level, pos, old, moved);
    if (old.getBlock() != this) {
      changed(level, pos);
    }
  }

  @Override
  protected void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
    super.onRemove(state, level, pos, next, moved);
    if (next.getBlock() != this) {
      changed(level, pos);
    }
  }

  @Override
  public void afterMove(
      ServerLevel source, ServerLevel destination, BlockState state, BlockPos from, BlockPos to) {
    changed(destination, to);
  }

  @Override
  public InteractionResult onWrenched(BlockState state, UseOnContext context) {
    if (context.getLevel() instanceof ServerLevel server) {
      var body = CoreAssemblyRules.containing(server, context.getClickedPos());
      if (body == null
          || !SubLevelData.isLiving(body)
          || context.getPlayer() == null
          || !org.azkerash.createbioships.core.access.SubLevelPermissions.canInspect(
              body, context.getPlayer().getUUID())
          || !SubLevelBrains.get(body)
              .movePort(context.getClickedPos(), context.getClickedFace())) {
        return InteractionResult.FAIL;
      }
      server.playSound(
          null,
          context.getClickedPos(),
          net.minecraft.sounds.SoundEvents.BONE_BLOCK_PLACE,
          net.minecraft.sounds.SoundSource.BLOCKS,
          .5f,
          1);
    }
    return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
  }

  @Override
  protected ItemInteractionResult useItemOn(
      net.minecraft.world.item.ItemStack stack,
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    return stack.getItem() instanceof net.minecraft.world.item.BlockItem
            || stack.getItem() instanceof com.simibubi.create.content.equipment.wrench.WrenchItem
        ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
        : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (state.getValue(PORT).face != hit.getDirection()) {
      return InteractionResult.PASS;
    }
    if (player instanceof ServerPlayer server && level instanceof ServerLevel world) {
      var body = CoreAssemblyRules.containing(world, pos);
      if (body == null
          || !SubLevelData.isLiving(body)
          || !org.azkerash.createbioships.core.access.SubLevelPermissions.canInspect(
              body, player.getUUID())) {
        return InteractionResult.FAIL;
      }
      var brain = SubLevelBrains.get(body);
      if (!pos.equals(brain.port()) || hit.getDirection() != brain.face()) {
        return InteractionResult.FAIL;
      }
      server.openMenu(
          new SimpleMenuProvider(
              (id, inv, p) -> new BrainMenu(id, inv, brain.inventory(), pos),
              net.minecraft.network.chat.Component.translatable("block.create_bioships.brain")));
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
