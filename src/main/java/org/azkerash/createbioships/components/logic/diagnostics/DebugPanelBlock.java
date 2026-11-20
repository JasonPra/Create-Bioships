package org.azkerash.createbioships.components.logic.diagnostics;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import org.azkerash.createbioships.components.logic.nerve.NervePanelBlock;

/** Same physical terminal mount as the Nerve Panel, with an independent read-only screen. */
public final class DebugPanelBlock extends NervePanelBlock {
  public static final MapCodec<DebugPanelBlock> CODEC = simpleCodec(DebugPanelBlock::new);

  public DebugPanelBlock(Properties properties) {
    super(properties);
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected void open(ServerPlayer player, ServerSubLevel body, BlockPos panel) {
    DebugPanelControl.open(player, body, panel);
  }
}
