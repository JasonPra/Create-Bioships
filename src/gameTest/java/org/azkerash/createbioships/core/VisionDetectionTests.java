package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.core.vision.*;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class VisionDetectionTests {
  @GameTest(template = "test_empty", timeoutTicks = 140, batch = "long_range_entity_vision")
  public static void sees_targets_between_debug_rays_at_player_range_but_not_through_tissue(
      GameTestHelper h) {
    var level = h.getLevel();
    var base = new BlockPos(6144, 80, 128);
    for (int x = 383; x <= 391; x++) {
      for (int z = 7; z <= 10; z++) {
        level.setChunkForced(x, z, true);
      }
    }
    level.setDayTime(6000);
    ServerSubLevel[] subject = {null};
    net.minecraft.world.entity.animal.Cow[] target = {null};
    double[] sightRange = {0};
    h.startSequence()
        .thenExecuteAfter(
            20,
            () -> {
              for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                  level.setBlock(
                      base.offset(x, -1, z),
                      ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(),
                      Block.UPDATE_ALL);
                }
              }
              level.setBlock(
                  base, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
              subject[0] =
                  SubLevelAssemblyHelper.assembleBlocks(
                      level, base, List.of(base), new BoundingBox3i(base, base));
              CoreAssemblyRules.mark(level, subject[0].getPlot().getCenterBlock());
              DevelopmentLifeSupport.pause(subject[0]);
              var cow = EntityType.COW.create(level);
              cow.setNoAi(true);
              cow.setNoGravity(true);
              cow.moveTo(base.getX() + 100.5, base.getY() + .5, base.getZ() + 20.5, 0, 0);
              level.addFreshEntity(cow);
              target[0] = cow;
            })
        .thenExecuteAfter(
            20,
            () -> {
              var body = subject[0];
              var cow = target[0];
              h.assertTrue(
                  EyeVisionProfile.LAND.range(false, 15, 256) == 256
                      && EyeVisionProfile.AQUATIC.range(true, 15, 256) == 256,
                  "Eye tuning did not follow player visibility distance");
              SubLevelVision.refresh(body, 8);
              var eye =
                  SubLevelVision.sights(body).stream()
                      .filter(s -> s.eye().face() == Direction.EAST)
                      .findFirst()
                      .orElseThrow();
              sightRange[0] = eye.range();
              h.assertTrue(
                  eye.range() >= 100,
                  "Physical daytime range was shorter than the distant target: range="
                      + eye.range()
                      + " light="
                      + eye.light());
              h.assertTrue(
                  eye.detected().stream().anyMatch(t -> t.entity().equals(cow.getUUID())),
                  "An entity between debug rays at 100 blocks was not detected");
              var direction =
                  cow.getBoundingBox()
                      .getCenter()
                      .subtract(eye.rays().getFirst().from())
                      .normalize();
              h.assertTrue(
                  VisionRaycaster.directions(
                          new net.minecraft.world.phys.Vec3(1, 0, 0),
                          new net.minecraft.world.phys.Vec3(0, 1, 0))
                      .stream()
                      .allMatch(r -> r.dot(direction) < Math.cos(Math.toRadians(3))),
                  "Test target accidentally lay on a debug sample ray");
              h.assertTrue(
                  SubLevelVision.detected(body).stream()
                      .anyMatch(t -> t.entity().equals(cow.getUUID())),
                  "Grid did not expose its actual visible targets");
              for (int y = -2; y <= 8; y++) {
                for (int z = 6; z <= 14; z++) {
                  level.setBlock(
                      base.offset(50, y, z),
                      ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(),
                      Block.UPDATE_ALL);
                }
              }
            })
        .thenExecuteAfter(
            1,
            () -> {
              var body = subject[0];
              var cow = target[0];
              SubLevelVision.refresh(body, 8);
              h.assertTrue(
                  SubLevelVision.detected(body).stream()
                      .noneMatch(t -> t.entity().equals(cow.getUUID())),
                  "Target-specific ray cast saw through an armor wall");
              SubLevelHealth.damage(body, SubLevelHealth.status(body).current(), "vision_death");
              h.assertTrue(
                  SubLevelVision.refresh(body, 8) == 0 && SubLevelVision.detected(body).isEmpty(),
                  "Dead creature retained detection");
              System.out.println(
                  "VISION_DETECTION_PASS range="
                      + sightRange[0]
                      + " distance=100 between_samples=true wall_occlusion=true dead_off=true");
            })
        .thenSucceed();
  }
}
