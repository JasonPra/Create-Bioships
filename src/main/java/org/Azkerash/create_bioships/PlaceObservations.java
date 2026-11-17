package org.Azkerash.create_bioships;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.creature.memory.PlaceMemory;
import org.Azkerash.create_bioships.creature.memory.PlaceMemory.Feature;
import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;

/** Low-frequency, loaded-world observations. Knowledge never generates or loads terrain. */
final class PlaceObservations {
  private long next;
  private int spoke;

  void tick(LivingCoreBlockEntity core) {
    var level = core.getLevel();
    if (level == null || core.ship() == null || core.body().dead()) return;
    long now = level.getGameTime();
    if (now < next) return;
    next = now + 40;
    var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    terrain(core, here, true);
    // Two columns per observation; rotate through nearby directions and distances.
    for (int i = 0; i < 2; i++) {
      int index = spoke++ % 24;
      double angle = (index % 8) * Math.PI / 4;
      double range = 8 + 12 * (index / 8);
      var column =
          BlockPos.containing(here.add(Math.cos(angle) * range, 0, Math.sin(angle) * range));
      if (!level.hasChunkAt(column)) continue;
      int y =
          level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
      var surface = new Vec3(column.getX() + .5, y + .1, column.getZ() + .5);
      if (core.vision().visible(core, surface)) terrain(core, surface, false);
    }
    core.setChanged();
  }

  static void terrain(LivingCoreBlockEntity core, Vec3 point, boolean occupied) {
    var level = core.getLevel();
    var pos = BlockPos.containing(point);
    if (level == null || !level.hasChunkAt(pos)) return;
    if (!occupied && !core.vision().visible(core, point)) return;
    var biome = level.getBiome(pos);
    var features = EnumSet.noneOf(Feature.class);
    boolean water =
        level.getFluidState(pos).is(FluidTags.WATER)
            || level.getFluidState(pos.below()).is(FluidTags.WATER);
    if (water) {
      features.add(Feature.WATER);
      features.add(
          biome.is(BiomeTags.IS_OCEAN)
              ? Feature.OCEAN
              : biome.is(BiomeTags.IS_RIVER) ? Feature.RIVER : Feature.LAKE);
    }
    if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_JUNGLE))
      features.add(Feature.FOREST);
    // Only immediately visible nearby geometry contributes cover/elevation evidence.
    for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
      var adjacent = pos.relative(direction, 2);
      if (!level.hasChunkAt(adjacent)) continue;
      var sample = Vec3.atCenterOf(adjacent);
      if (!core.vision().visible(core, sample)) continue;
      if (!level.getBlockState(adjacent).getCollisionShape(level, adjacent).isEmpty())
        features.add(Feature.COVER);
      if (!level
          .getBlockState(adjacent.above())
          .getCollisionShape(level, adjacent.above())
          .isEmpty()) features.add(Feature.CLIFF);
      if (level.isEmptyBlock(adjacent.below()) && level.isEmptyBlock(adjacent.below(2)))
        features.add(Feature.LEDGE);
    }
    core.placeMemory()
        .terrain(dimension(core), point(point), biome(core, pos), features, level.getGameTime());
  }

  static String dimension(LivingCoreBlockEntity core) {
    return core.getLevel().dimension().location().toString();
  }

  static String biome(LivingCoreBlockEntity core, BlockPos pos) {
    return core.getLevel()
        .getBiome(pos)
        .unwrapKey()
        .map(k -> k.location().toString())
        .orElse("unknown");
  }

  static PlaceMemory.Point point(Vec3 p) {
    return new PlaceMemory.Point(p.x, p.y, p.z);
  }
}
