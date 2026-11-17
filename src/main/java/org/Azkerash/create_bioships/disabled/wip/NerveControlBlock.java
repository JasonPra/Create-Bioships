package org.Azkerash.create_bioships.disabled.wip;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Physical, latching nerve pads. Adjacent redstone can activate the same organs. */
public class NerveControlBlock extends Block {
  public static final BooleanProperty LATCHED = BooleanProperty.create("latched");
  public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

  public enum Command {
    HUNT("hunt", 1, -2, 0, 0, 0),
    BACK("back", 0, 2, -1, 0, 0),
    LEFT("left", -1, 0, 0, -1, 0),
    RIGHT("right", 1, 0, 0, 1, 0),
    RISE("rise", -1, -1, 0, 0, 1),
    DIVE("dive", 1, -1, 0, 0, -1);
    private final String id;
    public final BlockPos offset;
    public final int forward, side, vertical;

    Command(String id, int x, int z, int forward, int side, int vertical) {
      this.id = id;
      this.offset = new BlockPos(x, 1, z);
      this.forward = forward;
      this.side = side;
      this.vertical = vertical;
    }

    public String id() {
      return id;
    }
  }

  private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 6, 14);
  public final Command command;

  public NerveControlBlock(Command command, Properties properties) {
    super(properties);
    this.command = command;
    registerDefaultState(stateDefinition.any().setValue(ACTIVE, false).setValue(LATCHED, false));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(ACTIVE, LATCHED);
  }

  @Override
  protected VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return SHAPE;
  }

  @Override
  protected void neighborChanged(
      BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbor, boolean moved) {
    updateSignal(state, level, pos);
  }

  @Override
  protected void onPlace(
      BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
    super.onPlace(state, level, pos, oldState, moved);
    updateSignal(state, level, pos);
  }

  private void updateSignal(BlockState state, Level level, BlockPos pos) {
    if (!AirshipPrototype.available() || level.isClientSide) return;
    boolean active = state.getValue(LATCHED) || level.hasNeighborSignal(pos);
    if (active != state.getValue(ACTIVE)) level.setBlock(pos, state.setValue(ACTIVE, active), 3);
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (!AirshipPrototype.available()) return InteractionResult.PASS;
    if (!level.isClientSide) {
      boolean latched = !state.getValue(LATCHED);
      boolean active = latched || level.hasNeighborSignal(pos);
      level.setBlock(pos, state.setValue(LATCHED, latched).setValue(ACTIVE, active), 3);
      level.playSound(
          null,
          pos,
          net.minecraft.sounds.SoundEvents.LEVER_CLICK,
          net.minecraft.sounds.SoundSource.BLOCKS,
          0.5f,
          active ? 0.7f : 0.5f);
      player.displayClientMessage(
          Component.translatable(
              "message.create_bioships.nerve",
              Component.translatable(getDescriptionId()),
              Component.translatable(
                  active ? "state.create_bioships.on" : "state.create_bioships.off")),
          true);
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
