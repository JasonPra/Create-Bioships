package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Connected surface eyes extend sight; they do not reveal buried world machines. */
public final class BioshipVision {
  private List<BlockPos> eyes = List.of();
  private long scanned = Long.MIN_VALUE;

  private void scan(LivingCoreBlockEntity core) {
    if (scanned == core.getLevel().getGameTime()) return;
    scanned = core.getLevel().getGameTime();
    eyes =
        BioshipDecay.blocks(core.ship()).stream()
            .filter(p -> core.getLevel().getBlockState(p).is(Create_bioships.SURFACE_EYE.get()))
            .filter(
                p -> {
                  var state = core.getLevel().getBlockState(p);
                  return !core.getLevel()
                      .getBlockState(
                          p.relative(state.getValue(SurfaceEyeBlock.FACING).getOpposite()))
                      .isAir();
                })
            .toList();
  }

  public List<Vec3> origins(LivingCoreBlockEntity core) {
    scan(core);
    List<Vec3> result = new ArrayList<>();
    result.add(BioshipBlueprint.physical(core, BlockPos.ZERO));
    for (var eye : eyes) {
      var facing = core.getLevel().getBlockState(eye).getValue(SurfaceEyeBlock.FACING);
      // The eye sits against the supporting face rather than floating at the block center.
      Vec3 point = Vec3.atCenterOf(eye).relative(facing, -.22);
      result.add(core.ship().logicalPose().transformPosition(point));
    }
    return result;
  }

  static boolean ray(LivingCoreBlockEntity core, Vec3 from, Vec3 to) {
    int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) * 5));
    var targetCell = BlockPos.containing(to);
    for (int i = 1; i < steps; i++) {
      var worldCell = BlockPos.containing(from.lerp(to, i / (double) steps));
      if (!worldCell.equals(targetCell)
          && (!core.getLevel()
                  .getBlockState(worldCell)
                  .getCollisionShape(core.getLevel(), worldCell)
                  .isEmpty()
              || !core.getLevel().getFluidState(worldCell).isEmpty())) return false;
      var sample =
          BlockPos.containing(
              core.ship()
                  .logicalPose()
                  .transformPositionInverse(from.lerp(to, i / (double) steps)));
      var state = core.getLevel().getBlockState(sample);
      if (sample.equals(core.getBlockPos()) || state.is(Create_bioships.SURFACE_EYE.get()))
        continue;
      if (!state.getCollisionShape(core.getLevel(), sample).isEmpty()) return false;
    }
    return true;
  }

  public boolean visible(LivingCoreBlockEntity core, Vec3 point) {
    if (core.ship() == null) return false;
    for (var from : origins(core)) if (ray(core, from, point)) return true;
    return false;
  }

  boolean exposed(LivingCoreBlockEntity core, BlockPos source) {
    if (!core.getLevel().canSeeSky(source.above())
        || !core.getLevel()
            .getBlockState(source.above())
            .getCollisionShape(core.getLevel(), source.above())
            .isEmpty()) return false;
    return visible(core, Vec3.atCenterOf(source));
  }

  void tick(LivingCoreBlockEntity core) {
    if (core.getLevel().getGameTime() % 80 == 0) growEyes(core);
  }

  void growEyes(LivingCoreBlockEntity core) {
    if (core.body().dead()
        || core.blueprint().busy()
        || OrganMotion.busy(core)
        || core.hungry()
        || !core.hasMouth()) return;
    var stomach = DigestiveSystem.stomach(core);
    if (stomach == null || !stomach.connected(core)) return;
    var objective = BioshipPriorities.bestWorld(core);
    if (objective != null && objective.rank() <= 2) return;
    scan(core);
    var level = (ServerLevel) core.getLevel();
    var cells = BioshipDecay.blocks(core.ship());
    for (var direction : Direction.values()) {
      BlockPos existing =
          eyes.stream()
              .filter(p -> level.getBlockState(p).getValue(SurfaceEyeBlock.FACING) == direction)
              .findFirst()
              .orElse(null);
      Vec3 forward =
          core.ship().logicalPose().transformNormal(Vec3.atLowerCornerOf(direction.getNormal()));
      Vec3 root =
          existing == null
              ? BioshipBlueprint.physical(core, BlockPos.ZERO)
              : core.ship()
                  .logicalPose()
                  .transformPosition(Vec3.atCenterOf(existing).relative(direction, -.22));
      if (ray(core, root, root.add(forward.scale(8)))) continue;
      if (existing == null
          && (eyes.size() >= 6
              || stomach.material("biomancy:flesh_bits") < 2
              || stomach.material("biomancy:elastic_fibers") < (core.isMature() ? 1 : 13)))
        continue;
      var candidates = new ArrayList<>(cells);
      candidates.sort(
          Comparator.<BlockPos>comparingInt(
                  p ->
                      -(p.getX() * direction.getStepX()
                          + p.getY() * direction.getStepY()
                          + p.getZ() * direction.getStepZ()))
              .thenComparingDouble(p -> p.distSqr(core.getBlockPos())));
      for (var host : candidates) {
        var tissue = level.getBlockState(host);
        if (!tissue.is(Create_bioships.FLESH.get())
            && !tissue.is(Create_bioships.CARAPACE.get())
            && !tissue.is(Create_bioships.CORE.get())) continue;
        var target = host.relative(direction);
        if (!level.getBlockState(target).isAir()
            || !core.ship().getPlot().contains(new net.minecraft.world.level.ChunkPos(target)))
          continue;
        if (!core.isMature()
            && core.plannedBody().stream()
                .anyMatch(p -> p.offset().equals(target.subtract(core.getBlockPos())))) continue;
        Vec3 point =
            core.ship()
                .logicalPose()
                .transformPosition(Vec3.atCenterOf(target).relative(direction, -.22));
        if (!ray(core, point, point.add(forward.scale(8)))) continue;
        if (existing != null) {
          if (OrganMotion.begin(core, existing, target, false)) return;
        } else {
          level.setBlock(
              target,
              Create_bioships.SURFACE_EYE
                  .get()
                  .defaultBlockState()
                  .setValue(SurfaceEyeBlock.FACING, direction),
              3);
          stomach.spend("biomancy:flesh_bits", 2);
          stomach.spend("biomancy:elastic_fibers", 1);
          scanned = Long.MIN_VALUE;
          core.setChanged();
          return;
        }
      }
    }
  }
}
