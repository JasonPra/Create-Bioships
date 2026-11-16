package org.Azkerash.create_bioships;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class AccessPanelBlock extends Block {
  public AccessPanelBlock(Properties properties) {
    super(properties);
  }

  @Override
  protected net.minecraft.world.phys.shapes.VoxelShape getShape(
      BlockState state,
      net.minecraft.world.level.BlockGetter level,
      BlockPos pos,
      net.minecraft.world.phys.shapes.CollisionContext context) {
    return Block.box(2, 0, 2, 14, 5, 14);
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (!AirshipPrototype.available()) return InteractionResult.PASS;
    if (player instanceof ServerPlayer server) {
      var core = BioshipAccess.core(level, pos);
      if (core == null || !player.getUUID().equals(core.creator())) {
        player.displayClientMessage(
            Component.literal("Only the construct's creator can manage access."), true);
        return InteractionResult.SUCCESS;
      }
      var choices = AccessMenu.choices(core);
      server.openMenu(
          new SimpleMenuProvider(
              (id, inventory, user) -> new AccessMenu(id, inventory, core, pos, choices),
              Component.translatable("block.create_bioships.access_panel")),
          buffer -> {
            buffer.writeBlockPos(pos);
            buffer.writeVarInt(choices.size());
            for (var entry : choices) {
              buffer.writeUUID(entry.id());
              buffer.writeUtf(entry.label(), 128);
              buffer.writeBoolean(entry.animal());
            }
          });
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
