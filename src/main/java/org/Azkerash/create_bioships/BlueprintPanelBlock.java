package org.Azkerash.create_bioships;

import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class BlueprintPanelBlock extends Block {
  public BlueprintPanelBlock(Properties properties) {
    super(properties);
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (player instanceof ServerPlayer server) {
      var core = BioshipAccess.core(level, pos);
      if (core == null
          || !player.getUUID().equals(core.creator())
          || core.body().dead()
          || !core.isMature()) return InteractionResult.FAIL;
      if (core.blueprint().busy()) {
        player.displayClientMessage(Component.literal(core.blueprint().status()), false);
        return InteractionResult.SUCCESS;
      }
      core.body().observe(core);
      server.openMenu(
          new SimpleMenuProvider(
              (id, inv, p) -> new BlueprintMenu(id, inv, core, pos),
              Component.literal("Living Blueprint")),
          b -> {
            b.writeBlockPos(pos);
            b.writeBlockPos(core.getBlockPos());
            b.writeNbt(core.body().save());
            b.writeNbt(core.blueprint().save());
          });
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
