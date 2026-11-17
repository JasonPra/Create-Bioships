package org.Azkerash.create_bioships.core;

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

/** A blinking core with player-only assembly; inspection only; no limbs or creature behavior. */
public final class CoreBlock extends Block
    implements dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener,
        com.simibubi.create.content.equipment.wrench.IWrenchable,
        net.minecraft.world.level.block.EntityBlock {
  public static final MapCodec<CoreBlock> CODEC = simpleCodec(CoreBlock::new);
  public static final BooleanProperty BLINK =
      org.Azkerash.create_bioships.components.eye.EyeBlink.BLINK;
  public static final IntegerProperty WAKE =
      org.Azkerash.create_bioships.components.eye.EyeBlink.WAKE;
  private static final IntegerProperty STAGE =
      org.Azkerash.create_bioships.components.eye.EyeBlink.STAGE;

  public CoreBlock(Properties properties) {
    super(properties);
    registerDefaultState(
        stateDefinition.any().setValue(BLINK, true).setValue(STAGE, 0).setValue(WAKE, 0));
  }

  @Override
  public net.minecraft.world.InteractionResult onWrenched(
      BlockState state, net.minecraft.world.item.context.UseOnContext context) {
    return net.minecraft.world.InteractionResult.FAIL;
  }

  @Override
  public net.minecraft.world.InteractionResult onSneakWrenched(
      BlockState state, net.minecraft.world.item.context.UseOnContext context) {
    return net.minecraft.world.InteractionResult.FAIL;
  }

  @Override
  public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
      BlockPos pos, BlockState state) {
    return new CoreGogglesDisplay(pos, state);
  }

  @Override
  public <T extends net.minecraft.world.level.block.entity.BlockEntity>
      net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
          Level level,
          BlockState state,
          net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
    return CoreGogglesDisplay.ticker(level, type);
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(BLINK, STAGE, WAKE);
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
      if (!state.getValue(BLINK) || state.getValue(STAGE) != 0 || state.getValue(WAKE) != 0)
        level.setBlock(pos, state.setValue(BLINK, true).setValue(STAGE, 0).setValue(WAKE, 0), 2);
      return;
    }
    CoreAssemblyRules.mark(level, pos);
    CoreAssemblyRules.reconcile(level, pos);
    if (!level.getBlockState(pos).is(this)) return;
    var body = CoreAssemblyRules.containing(level, pos);
    if (body != null && SubLevelHealth.status(body).dead()) {
      level.setBlock(pos, state.setValue(BLINK, true).setValue(WAKE, 0).setValue(STAGE, 0), 2);
      return;
    }
    org.Azkerash.create_bioships.components.eye.EyeBlink.advance(this, state, level, pos);
  }

  @Override
  protected float getDestroyProgress(
      BlockState state,
      net.minecraft.world.entity.player.Player player,
      net.minecraft.world.level.BlockGetter level,
      BlockPos pos) {
    boolean dead =
        level.getBlockEntity(pos) instanceof CoreGogglesDisplay display && display.dead();
    if (level instanceof ServerLevel server) {
      var body = CoreAssemblyRules.containing(server, pos);
      if (body != null) dead = SubLevelHealth.status(body).dead();
    }
    return dead ? 1 : super.getDestroyProgress(state, player, level, pos);
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
    destination.setBlock(
        to,
        state
            .setValue(BLINK, true)
            .setValue(STAGE, 0)
            .setValue(WAKE, assembled(destination, to) ? 1 : 0),
        2);
    CoreAssemblyRules.changed(destination, to);
    destination.scheduleTick(to, this, 1);
  }
}
