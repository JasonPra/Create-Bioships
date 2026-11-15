package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class EngineRoom {
  public static boolean inside(LivingCoreBlockEntity core, BlockPos pos) {
    var d = core.blueprint().engineLocal(pos.subtract(core.getBlockPos()));
    return Math.abs(d.getX()) <= (Math.abs(d.getZ()) >= 5 ? 2 : 3)
        && d.getY() >= 2
        && d.getY() <= 3
        && d.getZ() >= 1
        && d.getZ() <= 5;
  }

  public static boolean storage(BlockEntity entity) {
    return BuiltInRegistries.BLOCK
        .getKey(entity.getBlockState().getBlock())
        .toString()
        .equals("biomancy:storage_sac");
  }

  public static void arrange(LivingCoreBlockEntity core, ServerSubLevel ship) {
    if (!core.isMature()) return;
    var components = BioshipIntegration.blockEntities(ship);
    components.sort(
        java.util.Comparator.comparingInt(
            entity -> entity instanceof StomachBlockEntity ? 0 : storage(entity) ? 1 : 2));
    for (var entity : components) {
      if (entity instanceof FeedingMouthBlockEntity) continue;
      String id = BuiltInRegistries.BLOCK.getKey(entity.getBlockState().getBlock()).toString();
      boolean machine =
          entity instanceof StomachBlockEntity
              || id.startsWith("biomancy:")
                  && !id.contains("membrane")
                  && !id.contains("lantern")
                  && !id.contains("larynx");
      if (!machine
          || inside(core, entity.getBlockPos())
          || entity.getPersistentData().getBoolean("BioshipManualPlacement")) continue;
      List<BlockPos> bays =
          entity instanceof StomachBlockEntity
              ? List.of(new BlockPos(-2, 2, 3), new BlockPos(2, 2, 3))
              : storage(entity)
                  ? List.of(
                      new BlockPos(-2, 2, 2),
                      new BlockPos(-2, 2, 4),
                      new BlockPos(-1, 2, 3),
                      new BlockPos(2, 2, 2),
                      new BlockPos(2, 2, 4),
                      new BlockPos(1, 2, 3),
                      new BlockPos(2, 2, 5),
                      new BlockPos(-2, 2, 5))
                  : List.of(
                      new BlockPos(2, 2, 1),
                      new BlockPos(-2, 2, 1),
                      new BlockPos(2, 2, 3),
                      new BlockPos(-2, 2, 5),
                      new BlockPos(2, 2, 5));
      for (var offset : bays)
        if (move(core, ship, entity.getBlockPos(), core.layoutPos(offset))) break;
    }
  }

  public static boolean move(
      LivingCoreBlockEntity core, ServerSubLevel ship, BlockPos source, BlockPos target) {
    if (source.equals(target)) return true;
    return OrganMotion.begin(core, source, target, false);
  }
}
