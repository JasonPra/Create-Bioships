package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;

/** Read-only capacity planning for separately connected mouth/stomach pairs. */
final class BioshipDigestiveGrowth {
  record Plan(
      BlockPos mouth,
      BlockPos stomach,
      BlockPos tube,
      Direction facing,
      boolean addMouth,
      boolean addStomach,
      MaterialCost cost,
      int tubeCredits,
      int legUpgrades) {}

  static int target(LivingCoreBlockEntity core) {
    return Math.max(
        DigestiveSystem.requiredPairs(core),
        Math.max(DigestiveSystem.mouths(core).size(), DigestiveSystem.stomachs(core).size()));
  }

  static Plan plan(LivingCoreBlockEntity core) {
    var stomachs = DigestiveSystem.stomachs(core);
    var mouths = DigestiveSystem.mouths(core);
    if (stomachs.isEmpty() || mouths.isEmpty() || BioshipNutrition.critical(core)) return null;
    int target = target(core);
    if (core.isMature() && core.engineRoom().capacity() < target) return null;
    if (stomachs.size() >= target && mouths.size() >= target) return null;
    var originals = BioshipRecovery.available(core);
    if (mouths.size() < target && originals.mouth()
        || stomachs.size() < target && originals.stomach()) return null;
    var unmatchedMouth =
        mouths.stream()
            .filter(p -> stomachs.stream().noneMatch(s -> p.equals(s.connectedMouth)))
            .findFirst()
            .orElse(null);
    if (unmatchedMouth != null) {
      var state = core.getLevel().getBlockState(unmatchedMouth);
      var facing =
          state.hasProperty(FeedingMouthBlock.FACING)
              ? state.getValue(FeedingMouthBlock.FACING)
              : FeedingMouthBlock.outward(core, unmatchedMouth);
      var result =
          check(
              core,
              unmatchedMouth,
              unmatchedMouth.relative(facing.getOpposite(), 2),
              facing,
              false,
              true);
      if (result != null) return result;
    }
    var unmatchedStomach =
        stomachs.stream().filter(s -> s.connectedMouth == null).findFirst().orElse(null);
    if (unmatchedStomach != null)
      for (var face : Direction.values()) {
        var result =
            check(
                core,
                unmatchedStomach.getBlockPos().relative(face, 2),
                unmatchedStomach.getBlockPos(),
                face,
                true,
                false);
        if (result != null) return result;
      }
    if (mouths.size() >= target || stomachs.size() >= target) return null;
    for (var pos : BioshipDecay.blocks(core.ship()))
      if (BioshipTissue.isTissue(core.getLevel().getBlockState(pos)))
        for (var face : mouthDirections(core)) {
          var result = check(core, pos, pos.relative(face.getOpposite(), 2), face, true, true);
          if (result != null) return result;
        }
    return null;
  }

  private static List<Direction> mouthDirections(LivingCoreBlockEntity core) {
    var mouths = DigestiveSystem.mouths(core);
    var faces = new ArrayList<Direction>();
    Direction.Plane.HORIZONTAL.forEach(faces::add);
    faces.sort(
        Comparator.comparingLong(
            face ->
                mouths.stream()
                    .filter(
                        p -> {
                          var state = core.getLevel().getBlockState(p);
                          return state.hasProperty(FeedingMouthBlock.FACING)
                              && state.getValue(FeedingMouthBlock.FACING) == face;
                        })
                    .count()));
    return faces;
  }

  private static Plan check(
      LivingCoreBlockEntity core,
      BlockPos mouth,
      BlockPos stomach,
      Direction face,
      boolean addMouth,
      boolean addStomach) {
    var tube = mouth.relative(face.getOpposite());
    var level = core.getLevel();
    var ship = core.ship();
    if (addMouth && !replaceable(core, mouth)
        || addStomach && !replaceable(core, stomach)
        || !level.getBlockState(tube).is(Create_bioships.INTESTINE.get())
            && !replaceable(core, tube)) return null;
    var outside = mouth.relative(face);
    if (!level.getBlockState(outside).isAir()
        || !BioshipIntegration.growthSpace(
            level,
            BlockPos.containing(ship.logicalPose().transformPosition(Vec3.atCenterOf(outside)))))
      return null;
    int tubeCredits =
        !addStomach && !level.getBlockState(tube).is(Create_bioships.INTESTINE.get()) ? 1 : 0;
    var cost =
        new MaterialCost(
            (addMouth ? 8 : 0) + (addStomach ? 16 : 0) + tubeCredits,
            (addMouth ? 4 : 0) + (addStomach ? 8 : 0) + tubeCredits,
            (addMouth ? 2 : 0) + (addStomach ? 4 : 0));
    double extra = 0;
    for (var pos : List.of(mouth, stomach, tube))
      if (level.getBlockState(pos).isAir())
        extra += core.crawl().blockLoad(core, pos, BioshipTissue.body().defaultBlockState());
    int tiers = 0;
    while (core.crawl().legTier() + tiers < 3
        && core.crawl().carriedLoad(core) + extra
            > BioshipCrawl.capacityFor(core.crawl().legTier() + tiers)) tiers++;
    if (!core.canFly()
        && core.crawl().carriedLoad(core) + extra
            > BioshipCrawl.capacityFor(core.crawl().legTier() + tiers)) return null;
    if (core.canFly()) tiers = 0;
    if (!EmergencyReserve.after(
        core,
        new MaterialCost(
            cost.flesh() + tiers * 8, cost.fiber() + tiers * 4, cost.bone() + tiers * 2)))
      return null;
    if (addMouth
            && !BioshipSupport.safeAddition(
                core, mouth, Create_bioships.FEEDING_MOUTH.get().defaultBlockState())
        || addStomach
            && !BioshipSupport.safeAddition(
                core, stomach, Create_bioships.STOMACH.get().defaultBlockState())) return null;
    return new Plan(mouth, stomach, tube, face, addMouth, addStomach, cost, tubeCredits, tiers);
  }

  private static boolean replaceable(LivingCoreBlockEntity core, BlockPos pos) {
    var level = core.getLevel();
    if (!core.ship().getPlot().contains(new net.minecraft.world.level.ChunkPos(pos))) return false;
    var state = level.getBlockState(pos);
    if (!state.isAir() && !BioshipTissue.isTissue(state)) return false;
    return BioshipIntegration.growthSpace(
        level,
        BlockPos.containing(core.ship().logicalPose().transformPosition(Vec3.atCenterOf(pos))));
  }
}
