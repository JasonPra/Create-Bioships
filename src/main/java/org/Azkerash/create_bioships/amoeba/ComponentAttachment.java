package org.Azkerash.create_bioships.amoeba;

import org.Azkerash.create_bioships.*;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;


/** Restores component data and mount orientation after transport, without moving cargo. */
final class ComponentAttachment {
  private ComponentAttachment() {}

  static void restore(
      AmoebaComponentHost core,
      ComponentTransport.Move move,
      BlockState host,
      CompoundTag capsule) {
    var level = core.level();
    var owner = core.body();
    core.restore(owner, move.target(), capsule);
    var moved = core.component(owner, move.target());
    if (moved != null) {
      core.rememberHost(moved, host);
      if (move.manual()) moved.getPersistentData().putBoolean("BioshipManualPlacement", true);
      moved.setChanged();
    }
    if (move.manual() && level.getBlockState(move.target()).is(Create_bioships.SURFACE_EYE.get()))
      level.setBlock(
          move.target(),
          level.getBlockState(move.target()).setValue(SurfaceEyeBlock.AUTOMATIC, false),
          3);
    if (core.leg(level.getBlockState(move.target())))
      level.setBlock(
          move.target(),
          level
              .getBlockState(move.target())
              .setValue(AmoebaLegBlock.FACING, core.legFacing(move.target())),
          3);
    if (moved instanceof FeedingMouthBlockEntity mouth) {
      mouth.embeddedInHull(BioshipTissue.isTissue(host));
      Direction facing = core.mouthFacing(move.target());
      level.setBlock(
          move.target(),
          level.getBlockState(move.target()).setValue(FeedingMouthBlock.FACING, facing),
          3);
    }
    if (moved instanceof StomachBlockEntity) {
      var d = move.target().subtract(core.controllerPosition());
      level.setBlock(
          move.target(),
          level
              .getBlockState(move.target())
              .setValue(StomachBlock.FACING, d.getX() < 0 ? Direction.EAST : Direction.WEST),
          3);
    }
  }
}
