package org.azkerash.createbioships.components.logic.mouth;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import org.azkerash.createbioships.components.ComponentRegistry;

public final class MouthBlock extends DirectionalBlock
    implements EntityBlock, com.simibubi.create.content.equipment.wrench.IWrenchable {
  public static final net.minecraft.world.level.block.state.properties.IntegerProperty CHEW =
      net.minecraft.world.level.block.state.properties.IntegerProperty.create("chew", 0, 3);
  public static final net.minecraft.world.level.block.state.properties.IntegerProperty BREATH =
      net.minecraft.world.level.block.state.properties.IntegerProperty.create("breath", 0, 2);
  public static final net.minecraft.world.level.block.state.properties.BooleanProperty SPITTING =
      net.minecraft.world.level.block.state.properties.BooleanProperty.create("spitting");
  public static final MapCodec<MouthBlock> CODEC = simpleCodec(MouthBlock::new);

  public MouthBlock(Properties properties) {
    super(properties);
    registerDefaultState(
        stateDefinition
            .any()
            .setValue(FACING, Direction.NORTH)
            .setValue(CHEW, 0)
            .setValue(SPITTING, false)
            .setValue(BREATH, 0));
  }

  protected MapCodec<? extends DirectionalBlock> codec() {
    return CODEC;
  }

  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING, CHEW, BREATH, SPITTING);
  }

  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
  }

  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new MouthBlockEntity(pos, state);
  }

  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (player instanceof net.minecraft.server.level.ServerPlayer server
        && level.getBlockEntity(pos) instanceof MouthBlockEntity mouth) {
      if (state.is(ComponentRegistry.ARMORED_MAW.get())
          && player.isShiftKeyDown()
          && player.getMainHandItem().isEmpty()) {
        var result = mouth.requestAcidAttack(player.getUUID());
        player.displayClientMessage(
            net.minecraft.network.chat.Component.literal(result.getMessage()), true);
      } else {
        server.openMenu(mouth);
      }
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  protected ItemInteractionResult useItemOn(
      ItemStack stack,
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    if (stack.getItem() instanceof com.simibubi.create.content.equipment.wrench.WrenchItem) {
      return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }
    if (level.getBlockEntity(pos) instanceof MouthBlockEntity mouth
        && mouth.canPlaceItem(0, stack)) {
      if (!level.isClientSide) {
        var offering = stack.copyWithCount(player.isShiftKeyDown() ? stack.getCount() : 1);
        var rest = mouth.feed(offering, false);
        int accepted = offering.getCount() - rest.getCount();
        if (!player.getAbilities().instabuild) {
          stack.shrink(accepted);
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
          org.azkerash.createbioships.core.FeedingTrust.hand(serverPlayer, pos, offering, accepted);
        }
      }
      return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
  }

  @Override
  protected void neighborChanged(
      BlockState state, Level level, BlockPos pos, Block other, BlockPos from, boolean moved) {
    super.neighborChanged(state, level, pos, other, from, moved);
    if (!level.isClientSide && level.getBlockEntity(pos) instanceof MouthBlockEntity mouth) {
      mouth.observeAcidSignal(level.hasNeighborSignal(pos));
    }
  }

  @Override
  public void appendHoverText(
      ItemStack stack,
      net.minecraft.world.item.Item.TooltipContext context,
      java.util.List<net.minecraft.network.chat.Component> lines,
      net.minecraft.world.item.TooltipFlag flag) {
    if (stack.is(ComponentRegistry.ARMORED_MAW.get().asItem())) {
      lines.add(
          net.minecraft.network.chat.Component.translatable(
                  "block.create_bioships.armored_maw.acid_tooltip")
              .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
  }

  static void chewingSound(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
    level.playSound(
        null,
        pos,
        net.minecraft.sounds.SoundEvents.GENERIC_EAT,
        net.minecraft.sounds.SoundSource.BLOCKS,
        .45f,
        .8f + level.random.nextFloat() * .15f);
  }

  @Override
  protected void tick(
      BlockState state,
      net.minecraft.server.level.ServerLevel level,
      BlockPos pos,
      net.minecraft.util.RandomSource random) {
    if (!(level.getBlockEntity(pos) instanceof MouthBlockEntity mouth)) {
      return;
    }
    mouth.observeAcidSignal(level.hasNeighborSignal(pos));
    if (mouth.animateAcidAttack()) {
      return;
    }
    state = level.getBlockState(pos);
    int remaining = mouth.chewRemaining();
    if (!mouth.active()) {
      if (state.getValue(CHEW) != 0 || state.getValue(BREATH) != 0) {
        level.setBlock(pos, state.setValue(CHEW, 0).setValue(BREATH, 0), Block.UPDATE_CLIENTS);
      }
      return;
    }
    if (state.is(ComponentRegistry.ARMORED_MAW.get()) && level.random.nextInt(4) == 0) {
      mouth.salivaParticles();
    }
    if (remaining == 0) {
      int step = (int) ((level.getGameTime() / 8 + Math.floorMod(pos.asLong(), 8)) % 8);
      int breath = new int[] {0, 0, 1, 2, 2, 1, 0, 0}[step];
      if (state.getValue(CHEW) != 0 || state.getValue(BREATH) != breath) {
        level.setBlock(pos, state.setValue(CHEW, 0).setValue(BREATH, breath), Block.UPDATE_CLIENTS);
      }
      level.scheduleTick(pos, this, 8);
      return;
    }
    int phase = state.getValue(CHEW) % 3 + 1;
    level.setBlock(pos, state.setValue(CHEW, phase).setValue(BREATH, 0), Block.UPDATE_CLIENTS);
    if (phase == 1) {
      mouth.chewingParticles();
    }
    if (phase == 1) {
      chewingSound(level, pos);
    }
    level.scheduleTick(pos, this, Math.min(3, remaining));
  }

  @Override
  protected void onPlace(
      BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
    super.onPlace(state, level, pos, old, moved);
    if (!old.is(this) && !level.isClientSide) {
      level.scheduleTick(pos, this, 8);
    }
  }

  protected BlockState rotate(BlockState state, Rotation rotation) {
    return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
  }

  protected BlockState mirror(BlockState state, Mirror mirror) {
    return state.rotate(mirror.getRotation(state.getValue(FACING)));
  }

  protected java.util.List<ItemStack> getDrops(
      BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
    var drops = super.getDrops(state, builder);
    var be =
        builder.getOptionalParameter(
            net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
    if (be instanceof MouthBlockEntity mouth) {
      for (var stack : drops) {
        if (stack.is(asItem())) {
          mouth.saveToItem(stack, builder.getLevel().registryAccess());
        }
      }
    }
    return drops;
  }
}
