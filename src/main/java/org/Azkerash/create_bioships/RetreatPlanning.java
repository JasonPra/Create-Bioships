package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.creature.memory.PlaceMemory;
import org.Azkerash.create_bioships.creature.memory.PlaceMemory.Feature;
import org.Azkerash.create_bioships.creature.navigation.WaterRoutePlanner;
import org.Azkerash.create_bioships.creature.survival.EscapeScoring;
import org.Azkerash.create_bioships.creature.survival.EscapeScoring.Threat;
import org.Azkerash.create_bioships.creature.survival.ThreatProfile;
import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;

/** Ranks observed escape opportunities; existing navigators remain responsible for locomotion. */
final class RetreatPlanning {
  private Vec3 goal;
  private long retry;
  private boolean waterMode;
  private Vec3 lastDanger;

  private record Ranked(Vec3 point, double score) {}

  void clear() {
    goal = null;
    retry = 0;
  }

  Vec3 choose(LivingCoreBlockEntity core, Vec3 danger, List<Threat> observed, boolean water) {
    long now = core.getLevel().getGameTime();
    var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    if (danger == null) return null;
    boolean changed =
        waterMode != water || lastDanger == null || lastDanger.distanceToSqr(danger) > 9;
    if (changed) {
      goal = null;
      retry = 0;
    }
    waterMode = water;
    lastDanger = danger;
    if (now < retry)
      return goal != null && safe(core, goal, water) && here.distanceToSqr(goal) > 1 ? goal : null;
    retry = now + 20;
    // A remembered last attacker still has a direction even after its short tactical profile
    // expires.
    var threats =
        observed.isEmpty()
            ? List.of(new Threat(PlaceObservations.point(danger), ThreatProfile.GROUND))
            : observed;
    var places = core.placeMemory().inDimension(PlaceObservations.dimension(core));
    var candidates = new LinkedHashMap<BlockPos, Ranked>();
    var away = here.subtract(danger).multiply(1, 0, 1).normalize();
    if (away.lengthSqr() < .1) away = new Vec3(1, 0, 0);
    for (int i = 0; i < 12; i++) {
      var direction = away.yRot((float) (i * Math.PI / 6));
      add(core, here, here.add(direction.scale(10)), null, threats, places, water, candidates);
      if (water) {
        add(
            core,
            here,
            here.add(direction.scale(8)).add(0, 4, 0),
            null,
            threats,
            places,
            true,
            candidates);
        add(
            core,
            here,
            here.add(direction.scale(8)).add(0, -4, 0),
            null,
            threats,
            places,
            true,
            candidates);
      }
    }
    places.stream()
        .filter(e -> e.terrainConfidence(now) > 0)
        .filter(e -> vector(e.point()).distanceToSqr(here) < 64 * 64)
        .sorted(Comparator.comparingDouble(e -> vector(e.point()).distanceToSqr(here)))
        .limit(12)
        .forEach(
            e -> {
              var known = vector(e.point());
              var delta = known.subtract(here);
              var request = delta.length() > 16 ? here.add(delta.normalize().scale(16)) : known;
              add(core, here, request, e, threats, places, water, candidates);
            });
    var ranked =
        candidates.values().stream()
            .filter(r -> Double.isFinite(r.score))
            .sorted(Comparator.comparingDouble(Ranked::score).reversed())
            .limit(4)
            .toList();
    for (var candidate : ranked) {
      if (!water) {
        if (!core.crawl().reachableGoal(BlockPos.containing(candidate.point))) continue;
        var route = new GroundRoute();
        if (!route.plan(core, candidate.point) || !safePath(core, here, route.points(), false))
          continue;
      } else {
        var route =
            WaterRoutePlanner.toTarget(
                WaterNavigationSpace.point(here),
                WaterNavigationSpace.point(candidate.point),
                1.1,
                new WaterNavigationSpace(core, false),
                2048,
                32);
        if (route.points().size() < 2
            || !safePath(
                core,
                here,
                route.points().stream().map(WaterNavigationSpace::vector).toList(),
                true)) continue;
      }
      return goal = candidate.point;
    }
    return goal = null;
  }

  private static void add(
      LivingCoreBlockEntity core,
      Vec3 here,
      Vec3 requested,
      PlaceMemory.Entry memory,
      List<Threat> threats,
      List<PlaceMemory.Entry> places,
      boolean water,
      Map<BlockPos, Ranked> results) {
    Vec3 point = water ? requested : GroundRoute.escapeDestination(core, requested);
    if (point == null || here.distanceToSqr(point) < 4 || !safe(core, point, water)) return;
    long now = core.getLevel().getGameTime();
    var features = EnumSet.noneOf(Feature.class);
    double confidence = 1;
    if (memory != null) {
      features.addAll(memory.features());
      confidence = memory.terrainConfidence(now);
    }
    var pos = BlockPos.containing(point);
    if (core.getLevel().getFluidState(pos).is(FluidTags.WATER)) features.add(Feature.WATER);
    boolean cover =
        threats.stream()
            .filter(t -> t.profile().ranged())
            .allMatch(t -> blocked(core, vector(t.position()), point));
    // No ranged pursuer must not count as verified ranged cover.
    cover &= threats.stream().anyMatch(t -> t.profile().ranged());
    double danger = 0, prey = 0;
    for (var e : places)
      if (vector(e.point()).distanceToSqr(point) < 16 * 16) {
        danger = Math.max(danger, e.dangerConfidence(now));
        prey = Math.max(prey, e.preyConfidence(now));
      }
    var candidate =
        new EscapeScoring.Candidate(
            PlaceObservations.point(point), features, confidence, cover, true, danger, prey);
    double score = EscapeScoring.score(PlaceObservations.point(here), candidate, threats);
    var key = BlockPos.containing(point);
    var previous = results.get(key);
    if (previous == null || score > previous.score) results.put(key, new Ranked(point, score));
  }

  static boolean blocked(LivingCoreBlockEntity core, Vec3 from, Vec3 to) {
    // Loaded-world samples ignore the creature's current body; the hypothetical destination is
    // elsewhere.
    int count = (int) Math.ceil(from.distanceTo(to) * 2);
    for (int i = 1; i < count; i++) {
      var p = BlockPos.containing(from.lerp(to, (double) i / count));
      if (!core.getLevel().hasChunkAt(p)) return false;
      if (!core.getLevel().getBlockState(p).getCollisionShape(core.getLevel(), p).isEmpty())
        return true;
    }
    return false;
  }

  static boolean safePath(LivingCoreBlockEntity core, Vec3 from, List<Vec3> path, boolean water) {
    var space = water ? new WaterNavigationSpace(core, false) : null;
    for (var to : path) {
      int samples = Math.max(1, (int) Math.ceil(from.distanceTo(to) * 2));
      for (int i = 1; i <= samples; i++) {
        var point = from.lerp(to, (double) i / samples);
        if (!hazardFree(core, point) || water && !space.fits(WaterNavigationSpace.point(point)))
          return false;
      }
      from = to;
    }
    return true;
  }

  static boolean safe(LivingCoreBlockEntity core, Vec3 point, boolean water) {
    if (!hazardFree(core, point)) return false;
    return !water
        || new WaterNavigationSpace(core, false).fits(WaterNavigationSpace.point(point))
            && (BioshipRespiration.activeGills(core) || core.aquatic().air() > 200);
  }

  private static boolean hazardFree(LivingCoreBlockEntity core, Vec3 point) {
    var level = core.getLevel();
    var center = BlockPos.containing(point);
    // Check the terrain under and around the root; body clearance belongs to the route adapter.
    for (var pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
      if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
      var state = level.getBlockState(pos);
      if (level.getFluidState(pos).is(FluidTags.LAVA)
          || state.is(BlockTags.FIRE)
          || state.is(Blocks.MAGMA_BLOCK)
          || state.is(Blocks.CACTUS)
          || state.is(Blocks.CAMPFIRE)
          || state.is(Blocks.SOUL_CAMPFIRE)
          || state.is(Blocks.POWDER_SNOW)) return false;
    }
    return true; // GroundRoute verifies support and the height changes along the route.
  }

  static Vec3 vector(PlaceMemory.Point p) {
    return new Vec3(p.x(), p.y(), p.z());
  }
}
