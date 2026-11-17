package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.registry.Create_bioships;

/**
 * The same physical attachment points and support footprint drive gait, rendering and layout
 * checks.
 */
public final class BioshipSupport {
  /** Attachment and resting tip are relative to the structural origin, never world-grid axes. */
  public record Leg(Vec3 root, Vec3 foot, Direction face, boolean bootstrap, boolean swimming) {
    public Leg(Vec3 root, Vec3 foot, Direction face, boolean bootstrap) {
      this(root, foot, face, bootstrap, false);
    }

    public Leg(Vec3 root, Vec3 foot, Direction face) {
      this(root, foot, face, false);
    }

    public Vec3 outward() {
      return Vec3.atLowerCornerOf(face.getNormal());
    }

    public Vec3 swimmingFoot(double phase) {
      var normal = outward();
      var forward = new Vec3(0, 0, -1);
      var stroke = forward.subtract(normal.scale(forward.dot(normal)));
      if (stroke.lengthSqr() < .01) stroke = new Vec3(0, -1, 0);
      return foot.add(stroke.normalize().scale(Math.cos(phase) * (swimming ? .6 : .45)))
          .add(normal.scale(Math.sin(phase) * .2));
    }

    public Vec3 curve(Vec3 tip, Vec3 tipUp, double t) {
      double u = 1 - t;
      var first = root.add(outward().scale(.6));
      var second = tip.add(tipUp.scale(.45));
      return root.scale(u * u * u)
          .add(first.scale(3 * u * u * t))
          .add(second.scale(3 * u * t * t))
          .add(tip.scale(t * t * t));
    }
  }

  private static final List<java.util.function.Function<LivingCoreBlockEntity, List<Leg>>>
      LEG_PROVIDERS = new ArrayList<>();

  /** Body-mounted leg components can supply face-oriented attachments; bare cores stay separate. */
  public static void registerLegProvider(
      java.util.function.Function<LivingCoreBlockEntity, List<Leg>> provider) {
    LEG_PROVIDERS.add(Objects.requireNonNull(provider));
    CACHE.clear();
  }

  public record Layout(Vec3 center, List<Leg> legs, double margin) {
    public boolean stable() {
      return legs.size() >= 3 && margin >= .15;
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

  static Layout layout(LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells) {
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
          && !e.getValue().is(Create_bioships.SURFACE_EYE.get())
          && !BioshipLegEvolution.leg(e.getValue())) roots.add(e.getKey());
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
    boolean bootstrap =
        cells.size() == 1 && cells.values().iterator().next().is(Create_bioships.CORE.get());
    if (!bootstrap) {
      var attached = new ArrayList<Leg>(BioshipLegGrowth.attachments(core, cells));
      for (var provider : LEG_PROVIDERS)
        for (var leg : provider.apply(core)) if (attached.size() < 32) attached.add(leg);
      if (attached.size() >= 4
          || core.aquatic().swimming() && attached.stream().anyMatch(Leg::swimming)) {
        boolean balanced =
            containsProjection(center, attached.stream().map(Leg::foot).toArray(Vec3[]::new));
        return new Layout(center, List.copyOf(attached), balanced ? .15 : -1);
      }
    }
    var legs = new ArrayList<Leg>();
    double margin = Math.min(width, length) - Math.max(0, center.y - lowest) * .3;
    for (int i = 0; i < 4; i++) {
      int side = i % 2 == 0 ? -1 : 1, front = i < 2 ? -1 : 1;
      var desired = center.add(side * attachmentX, 0, front * attachmentZ);
      BlockPos best = null;
      Direction mountFace = null;
      double score = Double.MAX_VALUE;
      var faces =
          bootstrap
              ? List.of(Direction.DOWN)
              : List.of(
                  side < 0 ? Direction.WEST : Direction.EAST,
                  front < 0 ? Direction.NORTH : Direction.SOUTH,
                  Direction.DOWN);
      for (var cell : roots) {
        for (var face : faces) {
          if (cells.containsKey(cell.relative(face))
              || !(bootstrap || cells.get(cell).is(Create_bioships.CORE.get())
                  ? AmoebaLegBlock.supportsFace(cells.get(cell), cell, face)
                  : AmoebaLegBlock.canHostLeg(cells.get(cell), cell, face))) continue;
          var p = Vec3.atCenterOf(cell).subtract(origin);
          double value =
              Math.pow(p.x - desired.x, 2)
                  + Math.pow(p.z - desired.z, 2)
                  + Math.max(0, p.y - lowest - .5) * 4
                  + faces.indexOf(face) * .1;
          if (value < score) {
            best = cell;
            mountFace = face;
            score = value;
          }
        }
      }
      if (best == null) return new Layout(center, List.copyOf(legs), -1);
      var root =
          Vec3.atCenterOf(best)
              .subtract(origin)
              .add(Vec3.atLowerCornerOf(mountFace.getNormal()).scale(.49));
      root =
          switch (mountFace.getAxis()) {
            case X -> root.add(0, -.15, front * .35);
            case Z -> root.add(side * .35, -.15, 0);
            case Y -> root.add(side * .35, 0, front * .35);
          };
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
      legs.add(new Leg(root, foot, mountFace, bootstrap));
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

  public static boolean safeMove(
      LivingCoreBlockEntity core, BlockPos source, BlockPos target, boolean restoreFlesh) {
    return core.canFly() || movedLayout(core, source, target, restoreFlesh).stable();
  }

  public static boolean safeGroupMove(
      LivingCoreBlockEntity core, List<BlockPos> sources, BlockPos delta) {
    var before = cells(core);
    var transit = new LinkedHashMap<>(before);
    for (var source : sources) {
      transit.remove(source);
      var host = BioshipTissue.host(core.getLevel().getBlockEntity(source));
      if (!host.isAir()) transit.put(source, host);
    }
    if (BodyTopology.components(BodyTopology.structuralCells(transit))
        > BodyTopology.components(BodyTopology.structuralCells(before))) return false;
    var after = new LinkedHashMap<>(transit);
    for (var source : sources) after.put(source.offset(delta), before.get(source));
    return BodyTopology.components(BodyTopology.structuralCells(after))
            <= BodyTopology.components(BodyTopology.structuralCells(before))
        && invalidMounts(after) <= invalidMounts(before)
        && (core.canFly() || layout(core, after).stable());
  }

  public static boolean safeMerge(
      LivingCoreBlockEntity core,
      BlockPos source,
      BlockPos target,
      boolean restoreFlesh,
      BlockState result) {
    var before = cells(core);
    var after = new LinkedHashMap<>(before);
    after.remove(source);
    if (restoreFlesh) after.put(source, BioshipTissue.body().defaultBlockState());
    after.put(target, result);
    if (!BodyTopology.canMove(BodyTopology.structuralCells(before), source, target, restoreFlesh)
        || invalidMounts(after) > invalidMounts(before)) return false;
    return core.canFly() || layout(core, after).stable();
  }

  static Layout movedLayout(
      LivingCoreBlockEntity core, BlockPos source, BlockPos target, boolean restoreFlesh) {
    return movedLayout(core, source, target, restoreFlesh, true);
  }

  static boolean safeCoreEntry(LivingCoreBlockEntity core, BlockPos target) {
    return movedLayout(core, core.getBlockPos(), target, false, false).stable();
  }

  private static Layout movedLayout(
      LivingCoreBlockEntity core,
      BlockPos source,
      BlockPos target,
      boolean restoreFlesh,
      boolean swapCoreHost) {
    var cells = cells(core);
    var beforeStructural = BodyTopology.structuralCells(cells);
    var moved = cells.remove(source);
    if (moved == null) return new Layout(Vec3.ZERO, List.of(), -1);
    var host = cells.remove(target);
    if (restoreFlesh) cells.put(source, BioshipTissue.body().defaultBlockState());
    else if (swapCoreHost
        && moved.is(Create_bioships.CORE.get())
        && host != null
        && CoreMotion.tissue(host)) cells.put(source, host);
    if (BioshipLegEvolution.leg(moved))
      moved = moved.setValue(AmoebaLegBlock.FACING, AmoebaLegBlock.outward(cells, target));
    cells.put(target, moved);
    boolean retainsSource =
        restoreFlesh
            || swapCoreHost
                && moved.is(Create_bioships.CORE.get())
                && host != null
                && CoreMotion.tissue(host);
    if (beforeStructural.contains(source)) {
      // A core stays in its source cell until its atomic commit; carried organs leave it
      // immediately and must also preserve support throughout transport.
      boolean connected =
          moved.is(Create_bioships.CORE.get())
              ? BodyTopology.canCommit(beforeStructural, source, target, retainsSource)
              : BodyTopology.canMove(beforeStructural, source, target, retainsSource);
      if (!connected) return new Layout(Vec3.ZERO, List.of(), -1);
    }
    if (BioshipLegEvolution.leg(moved) && !AmoebaLegBlock.mounted(cells, target, moved))
      return new Layout(Vec3.ZERO, List.of(), -1);
    long invalidBefore = invalidMounts(cells(core));
    if (invalidMounts(cells) > invalidBefore) return new Layout(Vec3.ZERO, List.of(), -1);
    boolean attached = false;
    for (var face : net.minecraft.core.Direction.values())
      attached |= cells.containsKey(target.relative(face));
    if (!attached && cells.size() > 1) return new Layout(Vec3.ZERO, List.of(), -1);
    return layout(core, cells);
  }

  private static long invalidMounts(Map<BlockPos, BlockState> cells) {
    return cells.entrySet().stream()
        .filter(
            e ->
                BioshipLegEvolution.leg(e.getValue())
                    && !AmoebaLegBlock.mounted(cells, e.getKey(), e.getValue()))
        .count();
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
