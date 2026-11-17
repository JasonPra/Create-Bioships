package org.Azkerash.create_bioships;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** A blinking core with player-only assembly; no block entity, limbs or creature behavior. */
public final class CoreBlock extends Block
    implements dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener {
  public static final MapCodec<CoreBlock> CODEC = simpleCodec(CoreBlock::new);
  public static final BooleanProperty BLINK = BooleanProperty.create("blink");
  private static final IntegerProperty STAGE = IntegerProperty.create("blink_stage", 0, 3);

  public CoreBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(BLINK, true).setValue(STAGE, 0));
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(BLINK, STAGE);
  }

  @Override
  protected void onPlace(
      BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
    super.onPlace(state, level, pos, previous, moved);
    if (level instanceof ServerLevel server && !previous.is(this)) {
      CoreAssemblyRules.changed(server, pos);
      level.scheduleTick(pos, this, 1);
    }
  }

  @Override
  public void setPlacedBy(
      Level level,
      BlockPos pos,
      BlockState state,
      net.minecraft.world.entity.LivingEntity placer,
      net.minecraft.world.item.ItemStack stack) {
    super.setPlacedBy(level, pos, state, placer, stack);
    if (level instanceof ServerLevel server
        && placer instanceof net.minecraft.world.entity.player.Player
        && !(placer instanceof net.neoforged.neoforge.common.util.FakePlayer))
      CoreAssemblyRules.playerPlaced(server, pos);
  }

  static boolean assembled(ServerLevel level, BlockPos pos) {
    var container = SubLevelContainer.getContainer(level);
    if (container == null || !container.inBounds(pos)) return false;
    var plot = container.getPlot(new ChunkPos(pos));
    return plot != null && plot.getSubLevel() instanceof ServerSubLevel body && !body.isRemoved();
  }

  @Override
  protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    if (!assembled(level, pos)) {
      if (!state.getValue(BLINK) || state.getValue(STAGE) != 0)
        level.setBlock(pos, state.setValue(BLINK, true).setValue(STAGE, 0), 2);
      return;
    }
    CoreAssemblyRules.mark(level, pos);
    CoreAssemblyRules.reconcile(level, pos);
    if (!level.getBlockState(pos).is(this)) return;
    int stage = state.getValue(STAGE);
    // Newly assembled cores open first. The old four/four/three-tick double blink follows.
    boolean opening = stage == 3 || stage == 0 && state.getValue(BLINK);
    int next = opening ? 0 : stage + 1;
    boolean closed = next == 1 || next == 3;
    level.setBlock(pos, state.setValue(BLINK, closed).setValue(STAGE, next), 2);
    level.scheduleTick(pos, this, next == 0 ? 109 : next == 3 ? 3 : 4);
  }

  @Override
  protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(
      BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
    return java.util.List.of();
  }

  @Override
  public net.minecraft.world.item.ItemStack getCloneItemStack(
      net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
    return net.minecraft.world.item.ItemStack.EMPTY;
  }

  @Override
  public void afterMove(
      ServerLevel source, ServerLevel destination, BlockState state, BlockPos from, BlockPos to) {
    // Use Sable's move callback so both assembly and disassembly refresh the eye state.
    destination.setBlock(to, state.setValue(BLINK, true).setValue(STAGE, 0), 2);
    CoreAssemblyRules.changed(destination, to);
    destination.scheduleTick(to, this, 1);
  }
}
