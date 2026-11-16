package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The same physical attachment points and support footprint drive gait, rendering and layout
 * checks.
 */
public final class BioshipSupport {
  public record Leg(Vec3 root, Vec3 foot) {}

  public record Layout(Vec3 center, List<Leg> legs, double margin) {
    public boolean stable() {
      return legs.size() == 4 && margin >= .15;
    }
  }

  private record Cached(long time, Layout layout) {}

  private static final Map<LivingCoreBlockEntity, Cached> CACHE =
      Collections.synchronizedMap(new WeakHashMap<>());

  private static Map<BlockPos, BlockState> cells(LivingCoreBlockEntity core) {
    var result = new LinkedHashMap<BlockPos, BlockState>();
    var container =
        dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(core.getLevel());
    var plot =
        container == null
            ? null
            : container.getPlot(new net.minecraft.world.level.ChunkPos(core.getBlockPos()));
    if (plot == null) return result;
    return BioshipStructure.cells(plot.getSubLevel());
  }

  public static Layout layout(LivingCoreBlockEntity core) {
    long time = core.getLevel().getGameTime();
    var cached = CACHE.get(core);
    if (cached != null && cached.time == time) return cached.layout;
    var value = layout(core, cells(core));
    CACHE.put(core, new Cached(time, value));
    return value;
  }

  private static Layout layout(LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells) {
    var origin = Vec3.atLowerCornerOf(core.structuralOrigin());
    double total = 0;
    Vec3 center = Vec3.ZERO;
    var roots = new ArrayList<BlockPos>();
    for (var e : cells.entrySet()) {
      double mass = PhysicsBlockPropertyHelper.getMass(core.getLevel(), e.getKey(), e.getValue());
      if (Double.isFinite(mass) && mass > 0) {
        total += mass;
        center = center.add(Vec3.atCenterOf(e.getKey()).subtract(origin).scale(mass));
      }
      if (!cells.containsKey(e.getKey().below())
          && !e.getValue().is(Create_bioships.SURFACE_EYE.get())) roots.add(e.getKey());
    }
    double storedMass =
        core.assimilation().load(core)
            * PhysicsBlockPropertyHelper.getMass(
                core.getLevel(), core.getBlockPos(), BioshipTissue.body().defaultBlockState());
    if (storedMass > 0) {
      total += storedMass;
      var carrier =
          cells.entrySet().stream()
              .filter(e -> e.getValue().is(Create_bioships.CORE.get()))
              .map(Map.Entry::getKey)
              .findFirst()
              .orElse(core.getBlockPos());
      center = center.add(Vec3.atCenterOf(carrier).subtract(origin).scale(storedMass));
    }
    if (total <= 0 || roots.isEmpty()) return new Layout(Vec3.ZERO, List.of(), -1);
    center = center.scale(1 / total);
    double lowest = roots.stream().mapToInt(BlockPos::getY).min().orElseThrow() - origin.y;
    double halfX =
        (cells.keySet().stream().mapToInt(BlockPos::getX).max().orElseThrow()
                - cells.keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow()
                + 1)
            / 2.0;
    double halfZ =
        (cells.keySet().stream().mapToInt(BlockPos::getZ).max().orElseThrow()
                - cells.keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow()
                + 1)
            / 2.0;
    double width =
        core.hatchling()
            ? Math.min(2.25, Math.max(1.65, halfX + .5))
            : Math.min(4.2, Math.max(1.65, halfX + .65));
    double length =
        core.hatchling()
            ? Math.min(2.25, Math.max(1.2, halfZ + .5))
            : Math.min(6.5, Math.max(1.2, halfZ + .7));
    double attachmentX =
        core.hatchling()
            ? Math.min(1.2, Math.max(.4, halfX - .25))
            : Math.min(2.5, Math.max(.4, halfX - .5));
    double attachmentZ =
        core.hatchling()
            ? Math.min(1.2, Math.max(.4, halfZ - .25))
            : Math.min(4.5, Math.max(.4, halfZ - .5));
    var legs = new ArrayList<Leg>();
    double margin = Math.min(width, length) - Math.max(0, center.y - lowest) * .3;
    for (int i = 0; i < 4; i++) {
      int side = i % 2 == 0 ? -1 : 1, front = i < 2 ? -1 : 1;
      var desired = center.add(side * attachmentX, 0, front * attachmentZ);
      BlockPos best = null;
      double score = Double.MAX_VALUE;
      for (var cell : roots) {
        var p = Vec3.atCenterOf(cell).subtract(origin);
        double value =
            Math.pow(p.x - desired.x, 2)
                + Math.pow(p.z - desired.z, 2)
                + Math.max(0, p.y - lowest - .5) * 4;
        if (value < score) {
          best = cell;
          score = value;
        }
      }
      var root =
          Vec3.atLowerCornerOf(best).subtract(origin).add(.5 + side * .35, -.01, .5 + front * .35);
      var foot =
          new Vec3(
              center.x + side * width,
              lowest - (core.hatchling() ? .35 : .75),
              center.z + front * length);
      var delta = foot.subtract(root).multiply(1, 0, 1);
      double horizontal = delta.length();
      double vertical = Math.abs(root.y - foot.y);
      double remaining =
          core.crawl().footingReach() * core.crawl().footingReach() - vertical * vertical;
      if (remaining <= 0) margin = -1;
      double reachable = Math.max(.25, Math.sqrt(Math.max(0, remaining)) - .25);
      if (horizontal > reachable) {
        var clamped = root.add(delta.scale(reachable / horizontal));
        foot = new Vec3(clamped.x, foot.y, clamped.z);
      }
      legs.add(new Leg(root, foot));
    }
    var feet = legs.stream().map(Leg::foot).toArray(Vec3[]::new);
    if (!containsProjection(center, feet)) margin = -1;
    return new Layout(center, List.copyOf(legs), margin);
  }

  static boolean safeAddition(LivingCoreBlockEntity core, BlockPos pos, BlockState state) {
    var values = cells(core);
    values.put(pos, state);
    return layout(core, values).stable();
  }

  static boolean safeMove(
      LivingCoreBlockEntity core, BlockPos source, BlockPos target, boolean restoreFlesh) {
    return core.canFly() || movedLayout(core, source, target, restoreFlesh).stable();
  }

  static Layout movedLayout(
      LivingCoreBlockEntity core, BlockPos source, BlockPos target, boolean restoreFlesh) {
    var cells = cells(core);
    var moved = cells.remove(source);
    if (moved == null) return new Layout(Vec3.ZERO, List.of(), -1);
    var host = cells.remove(target);
    if (restoreFlesh) cells.put(source, BioshipTissue.body().defaultBlockState());
    else if (moved.is(Create_bioships.CORE.get()) && host != null && CoreMotion.tissue(host))
      cells.put(source, host);
    cells.put(target, moved);
    boolean attached = false;
    for (var face : net.minecraft.core.Direction.values())
      attached |= cells.containsKey(target.relative(face));
    if (!attached && cells.size() > 1) return new Layout(Vec3.ZERO, List.of(), -1);
    return layout(core, cells);
  }

  static boolean containsProjection(Vec3 point, Vec3[] contacts) {
    var polygon =
        Arrays.stream(contacts)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    if (polygon.size() < 3) return false;
    double cx = polygon.stream().mapToDouble(p -> p.x).average().orElseThrow();
    double cz = polygon.stream().mapToDouble(p -> p.z).average().orElseThrow();
    polygon.sort(Comparator.comparingDouble(p -> Math.atan2(p.z - cz, p.x - cx)));
    for (int i = 0; i < polygon.size(); i++) {
      var a = polygon.get(i);
      var b = polygon.get((i + 1) % polygon.size());
      double cross = (b.x - a.x) * (point.z - a.z) - (b.z - a.z) * (point.x - a.x);
      if (cross < -.02) return false;
    }
    return true;
  }
}
