package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.creature.navigation.WaterRoutePlanner.*;

/** Loaded-world collision/medium adapter. Routes reserve clearance for every current body block. */
final class WaterNavigationSpace implements Space {
  private final ServerLevel level;
  private final List<Vec3> offsets;
  private final Vec3 half;
  private final boolean surface;
  private final Map<BlockPos, Integer> samples = new HashMap<>();

  private int sample(BlockPos cell) {
    return samples.computeIfAbsent(
        cell.immutable(),
        p -> {
          if (!level.getWorldBorder().isWithinBounds(p)
              || !level.getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4)) return 0;
          var fluid = level.getFluidState(p);
          if (!fluid.isEmpty() && !fluid.is(FluidTags.WATER)) return 0;
          if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return 0;
          return fluid.is(FluidTags.WATER) ? 2 : 1;
        });
  }

  WaterNavigationSpace(LivingCoreBlockEntity core, boolean surface) {
    level = (ServerLevel) core.getLevel();
    this.surface = surface;
    var pose = core.ship().logicalPose();
    offsets =
        BioshipDecay.blocks(core.ship()).stream()
            .map(p -> pose.transformNormal(Vec3.atLowerCornerOf(p.subtract(core.getBlockPos()))))
            .toList();
    var x = pose.transformNormal(new Vec3(1, 0, 0));
    var y = pose.transformNormal(new Vec3(0, 1, 0));
    var z = pose.transformNormal(new Vec3(0, 0, 1));
    half =
        new Vec3(
                Math.abs(x.x) + Math.abs(y.x) + Math.abs(z.x),
                Math.abs(x.y) + Math.abs(y.y) + Math.abs(z.y),
                Math.abs(x.z) + Math.abs(y.z) + Math.abs(z.z))
            .scale(.49);
  }

  static Point point(Vec3 p) {
    return new Point(p.x, p.y, p.z);
  }

  static Vec3 vector(Point p) {
    return new Vec3(p.x(), p.y(), p.z());
  }

  public boolean fits(Point point) {
    boolean water = false;
    var root = vector(point);
    for (var offset : offsets) {
      var center = root.add(offset);
      for (var cell :
          BlockPos.betweenClosed(
              BlockPos.containing(center.subtract(half)),
              BlockPos.containing(center.add(half).add(0, .1, 0)))) {
        int state = sample(cell);
        if (state == 0) return false;
        if (state == 2) water = true;
      }
    }
    if (water) return true;
    var below = BlockPos.containing(root.add(0, -1, 0));
    return surface
        && level.getChunkSource().hasChunk(below.getX() >> 4, below.getZ() >> 4)
        && level.getFluidState(below).is(FluidTags.WATER);
  }

  public boolean clear(Point from, Point to) {
    int samples = Math.max(1, (int) Math.ceil(from.distance(to) / .2));
    for (int i = 1; i <= samples; i++) if (!fits(from.lerp(to, i / (double) samples))) return false;
    return true;
  }
}
