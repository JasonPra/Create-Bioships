package org.Azkerash.create_bioships.disabled.wip;

import org.Azkerash.create_bioships.integration.biomancy.NativeStorage;

import org.Azkerash.create_bioships.FeedingMouthBlockEntity;
import org.Azkerash.create_bioships.StomachBlockEntity;
import org.Azkerash.create_bioships.LivingCoreBlockEntity;
import org.Azkerash.create_bioships.BioshipIntegration;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.Azkerash.create_bioships.amoeba.ComponentTransport;

public final class EngineRoom {
  public static boolean inside(LivingCoreBlockEntity core, BlockPos pos) {
    var d = core.blueprint().engineLocal(pos.subtract(core.structuralOrigin()));
    return Math.abs(d.getX()) <= (Math.abs(d.getZ()) >= 5 ? 2 : 3) + core.engineRoom().widening()
        && d.getY() >= 2
        && d.getY() <= 3
        && d.getZ() >= 1
        && d.getZ() <= 5;
  }


  public static List<BlockPos> stomachBays(LivingCoreBlockEntity core) {
    var bays = new ArrayList<BlockPos>();
    for (int column = 0; column < core.engineRoom().columns(); column++) {
      bays.add(new BlockPos(-2 - 2 * column, 2, 3));
      bays.add(new BlockPos(2 + 2 * column, 2, 3));
    }
    return List.copyOf(bays);
  }

  public static void arrange(LivingCoreBlockEntity core, ServerSubLevel ship) {
    if (!core.isMature()) return;
    var components = BioshipIntegration.blockEntities(ship);
    components.sort(
        java.util.Comparator.comparingInt(
            entity -> entity instanceof StomachBlockEntity ? 0 : NativeStorage.isStorage(entity) ? 1 : 2));
    for (var entity : components) {
      if (entity instanceof FeedingMouthBlockEntity) continue;
      String id = BuiltInRegistries.BLOCK.getKey(entity.getBlockState().getBlock()).toString();
      boolean machine =
          entity instanceof StomachBlockEntity
              || id.startsWith("biomancy:")
                  && !id.contains("membrane")
                  && !id.contains("lantern")
                  && !id.contains("larynx");
      boolean housed =
          entity instanceof StomachBlockEntity && core.engineRoom().columns() > 1
              ? stomachBays(core).stream()
                  .map(core::layoutPos)
                  .anyMatch(p -> p.equals(entity.getBlockPos()))
              : inside(core, entity.getBlockPos());
      if (!machine || housed || entity.getPersistentData().getBoolean("BioshipManualPlacement"))
        continue;
      List<BlockPos> bays =
          entity instanceof StomachBlockEntity && core.engineRoom().columns() > 1
              ? stomachBays(core)
              : NativeStorage.isStorage(entity)
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
    return ComponentTransport.begin(core.amoebaComponents(), source, target, false);
  }
}
