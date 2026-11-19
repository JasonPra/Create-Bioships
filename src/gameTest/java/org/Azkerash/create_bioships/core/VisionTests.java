package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.vision.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class VisionTests {
  @GameTest(template = "test_empty", batch = "vision", timeoutTicks = 120)
  public static void profiles_and_cone_geometry(GameTestHelper helper) {
    helper.assertTrue(
        EyeVisionProfile.LAND.range(false, 15) == 128
            && EyeVisionProfile.LAND.range(true, 15) == 64
            && EyeVisionProfile.AQUATIC.range(true, 15) == 128
            && EyeVisionProfile.AQUATIC.range(false, 15) == 64,
        "Environment ranges reversed");
    helper.assertTrue(
        EyeVisionProfile.LAND.range(false, 0) < 128
            && EyeVisionProfile.AQUATIC.range(true, 0) > EyeVisionProfile.LAND.range(true, 0),
        "Darkness and underwater advantage missing");
    for (var face : Direction.values()) {
      var forward = Vec3.atLowerCornerOf(face.getNormal());
      var up = face.getAxis() == Direction.Axis.Y ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
      var rays = VisionRaycaster.directions(forward, up);
      helper.assertTrue(
          rays.size() == 13 && new HashSet<>(rays).size() == 13,
          "Unbounded/duplicate cone samples");
      for (var ray : rays)
        helper.assertTrue(
            Math.abs(ray.length() - 1) < 1e-8 && ray.dot(forward) >= Math.cos(Math.PI / 4) - 1e-8,
            "Ray outside circular 90 degree cone");
    }
    helper.succeed();
  }

  @GameTest(template = "test_empty", batch = "vision", timeoutTicks = 150)
  public static void assembled_eye_cache_occlusion_wounds_and_death(GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(1584, 85, 160);
    for (int x = 97; x <= 101; x++) for (int z = 8; z <= 12; z++) level.setChunkForced(x, z, true);
    helper.runAfterDelay(
        10,
        () -> {
          var cells = List.of(origin, origin.east(), origin.east(2), origin.west(), origin.west(2));
          level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
          level.setBlock(origin.east(), Blocks.STONE.defaultBlockState(), 3);
          level.setBlock(origin.west(), Blocks.STONE.defaultBlockState(), 3);
          level.setBlock(
              origin.east(2),
              ComponentRegistry.LAND_EYE
                  .get()
                  .defaultBlockState()
                  .setValue(BlockStateProperties.FACING, Direction.EAST),
              3);
          level.setBlock(
              origin.west(2),
              ComponentRegistry.AQUATIC_EYE
                  .get()
                  .defaultBlockState()
                  .setValue(BlockStateProperties.FACING, Direction.WEST),
              3);
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level, origin, cells, new BoundingBox3i(origin.west(2), origin.east(2)));
          var core = body.getPlot().getCenterBlock();
          CoreAssemblyRules.mark(level, core);
          SubLevelData.hungerPaused(body, true);
          helper.assertTrue(
              SubLevelVision.refresh(body, 2) == 2 && SubLevelVision.sights(body).size() == 2,
              "Per-refresh sensor budget ignored");
          SubLevelVision.refresh(body, 100);
          var sights = SubLevelVision.sights(body);
          helper.assertTrue(sights.size() == 8, "Missing six core faces or modular eyes");
          var east =
              sights.stream()
                  .filter(s -> s.eye().cell().equals(core) && s.eye().face() == Direction.EAST)
                  .findFirst()
                  .orElseThrow();
          helper.assertTrue(
              east.rays().getFirst().blocked()
                  && east.rays().getFirst().from().distanceTo(east.rays().getFirst().to()) < .1,
              "Own tissue failed to occlude core vision or hit remained in plot coordinates: "
                  + east.rays().getFirst());
          var outer =
              sights.stream()
                  .filter(s -> s.eye().cell().equals(core.east(2)))
                  .findFirst()
                  .orElseThrow();
          helper.assertTrue(
              !outer.rays().getFirst().blocked()
                  && outer.rays().getFirst().from().distanceTo(outer.rays().getFirst().to()) > 2,
              "Eye ray started inside supporting tissue");
          var start = outer.rays().getFirst().from();
          var wall = BlockPos.containing(start.add(2.5, 0, 0));
          level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
          var hit = VisionRaycaster.cast(level, start, new Vec3(1, 0, 0), 6);
          helper.assertTrue(
              hit.blocked() && hit.from().distanceTo(hit.to()) < 3, "World wall did not stop ray");
          level.removeBlock(wall, false);

          long oldDay = level.getDayTime();
          level.setDayTime(6000);
          level.updateSkyBrightness();
          SubLevelVision.refresh(body, 100);
          double dayRange =
              SubLevelVision.sights(body).stream()
                  .filter(
                      sight ->
                          sight.eye().cell().equals(core) && sight.eye().face() == Direction.UP)
                  .findFirst()
                  .orElseThrow()
                  .range();
          level.setDayTime(18000);
          level.updateSkyBrightness();
          SubLevelVision.refresh(body, 100);
          double nightRange =
              SubLevelVision.sights(body).stream()
                  .filter(
                      sight ->
                          sight.eye().cell().equals(core) && sight.eye().face() == Direction.UP)
                  .findFirst()
                  .orElseThrow()
                  .range();
          helper.assertTrue(nightRange < dayRange, "Physical world night did not reduce eye range");
          level.setDayTime(oldDay);
          level.updateSkyBrightness();
          int loadedX = origin.getX() >> 4;
          int chunkZ = origin.getZ() >> 4;
          for (int step = 0; step < 256 && level.hasChunk(loadedX + 1, chunkZ); step++) loadedX++;
          helper.assertTrue(
              !level.hasChunk(loadedX + 1, chunkZ), "Could not find unloaded chunk boundary");
          var edge = new Vec3((loadedX + 1) * 16.0 - .5, 85, origin.getZ() + .5);
          double limited = VisionRaycaster.loadedDistance(level, edge, new Vec3(1, 0, 0), 24);
          helper.assertTrue(
              limited > .49 && limited < .5 && !level.hasChunk(loadedX + 1, chunkZ),
              "Ray crossed or loaded an unloaded chunk boundary");
          // Cached sensor frame follows the rotated grid, including its own tissue clipping.
          body.logicalPose().orientation().rotateY(Math.PI / 2);
          body.forceUpdateGlobalBounds();
          SubLevelVision.refresh(body, 100);
          var rotated =
              SubLevelVision.sights(body).stream()
                  .filter(sight -> sight.eye().cell().equals(core.east(2)))
                  .findFirst()
                  .orElseThrow();
          var direction =
              rotated.rays().getFirst().to().subtract(rotated.rays().getFirst().from()).normalize();
          helper.assertTrue(
              Math.abs(direction.x) < 1e-5 && Math.abs(direction.z) > .99,
              "Eye cone ignored grid rotation");
          body.logicalPose().orientation().identity();
          body.forceUpdateGlobalBounds();
          // A topology change replaces the cached sensor face immediately.
          level.setBlock(
              core.east(2),
              level.getBlockState(core.east(2)).setValue(BlockStateProperties.FACING, Direction.UP),
              2);
          SubLevelVision.refresh(body, 100);
          helper.assertTrue(
              SubLevelVision.sights(body).stream()
                  .noneMatch(
                      sight ->
                          sight.eye().cell().equals(core.east(2))
                              && sight.eye().face() == Direction.EAST),
              "Reoriented eye retained stale direction");

          org.Azkerash.create_bioships
              .combat
              .SubLevelWounds
              .state(body)
              .hit(core.east(2), Direction.EAST, level.getGameTime(), 100);
          SubLevelVision.refresh(body, 100);
          helper.assertTrue(SubLevelVision.sights(body).size() == 7, "Injured eye still senses");
          SubLevelHealth.damage(body, 10000, "vision_test");
          helper.assertTrue(
              SubLevelVision.refresh(body, 100) == 0 && SubLevelVision.sights(body).isEmpty(),
              "Dead grid still casts");
          helper.succeed();
        });
  }
}
