package org.Azkerash.create_bioships;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/** Restores component data and mount orientation after transport, without moving cargo. */
final class ComponentAttachment {
  private ComponentAttachment() {}

  static void restore(
      LivingCoreBlockEntity core,
      ComponentTransport.Move move,
      BlockState host,
      CompoundTag capsule) {
    var level = core.getLevel();
    var owner = core.ship();
    BioshipContainerState.restore(level, owner, move.target(), capsule);
    var moved = BioshipContainerState.entity(level, owner, move.target());
    if (moved != null) {
      BioshipTissue.remember(moved, host);
      if (move.manual()) moved.getPersistentData().putBoolean("BioshipManualPlacement", true);
      moved.setChanged();
    }
    if (move.manual() && level.getBlockState(move.target()).is(Create_bioships.SURFACE_EYE.get()))
      level.setBlock(
          move.target(),
          level.getBlockState(move.target()).setValue(SurfaceEyeBlock.AUTOMATIC, false),
          3);
    if (BioshipLegEvolution.leg(level.getBlockState(move.target())))
      level.setBlock(
          move.target(),
          level
              .getBlockState(move.target())
              .setValue(AmoebaLegBlock.FACING, AmoebaLegBlock.outward(core, move.target())),
          3);
    if (moved instanceof FeedingMouthBlockEntity mouth) {
      mouth.embeddedInHull(BioshipTissue.isTissue(host));
      Direction facing = FeedingMouthBlock.outward(core, move.target());
      level.setBlock(
          move.target(),
          level.getBlockState(move.target()).setValue(FeedingMouthBlock.FACING, facing),
          3);
    }
    if (moved instanceof StomachBlockEntity) {
      var d = move.target().subtract(core.getBlockPos());
      level.setBlock(
          move.target(),
          level
              .getBlockState(move.target())
              .setValue(StomachBlock.FACING, d.getX() < 0 ? Direction.EAST : Direction.WEST),
          3);
    }
  }
}
