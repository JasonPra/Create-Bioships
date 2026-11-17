package org.Azkerash.create_bioships.disabled.wip;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/** A hand-opened, redstone-compatible two-part biological valve. */
public class LivingDoorBlock extends DoorBlock {
  public LivingDoorBlock(Properties properties) {
    super(BlockSetType.OAK, properties);
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (!level.isClientSide) setOpen(player, level, state, pos, !state.getValue(OPEN));
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  public void setOpen(Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
    if (!state.is(this) || state.getValue(OPEN) == open) return;
    level.setBlock(pos, state.setValue(OPEN, open), 3);
    level.playSound(
        null,
        pos,
        net.minecraft.sounds.SoundEvents.SLIME_SQUISH,
        net.minecraft.sounds.SoundSource.BLOCKS,
        0.6f,
        open ? 0.8f : 0.6f);
    level.gameEvent(entity, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
  }
}
