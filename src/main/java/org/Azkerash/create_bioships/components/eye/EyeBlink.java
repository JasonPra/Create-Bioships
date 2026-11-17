package org.Azkerash.create_bioships.components.eye;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/** Shared scheduled blink/wake sequence for core eyes and modular eye components. */
public final class EyeBlink {
  public static final BooleanProperty BLINK = BooleanProperty.create("blink");
  public static final IntegerProperty WAKE = IntegerProperty.create("wake", 0, 9),
      STAGE = IntegerProperty.create("blink_stage", 0, 3);

  private EyeBlink() {}

  public static BlockState sleeping(BlockState state) {
    return state.setValue(BLINK, true).setValue(WAKE, 0).setValue(STAGE, 0);
  }

  public static void advance(Block block, BlockState state, ServerLevel level, BlockPos pos) {
    int wake = state.getValue(WAKE);
    if (wake > 0) {
      int next = wake == 9 ? 0 : wake + 1;
      level.setBlock(pos, state.setValue(WAKE, next).setValue(BLINK, next != 0), 2);
      level.scheduleTick(pos, block, next == 0 ? 109 : 3);
      return;
    }
    int stage = state.getValue(STAGE);
    int next = stage == 3 || stage == 0 && state.getValue(BLINK) ? 0 : stage + 1;
    level.setBlock(pos, state.setValue(BLINK, next == 1 || next == 3).setValue(STAGE, next), 2);
    level.scheduleTick(pos, block, next == 0 ? 109 : next == 3 ? 3 : 4);
  }
}
