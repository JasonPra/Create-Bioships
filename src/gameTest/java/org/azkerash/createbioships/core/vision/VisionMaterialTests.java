package org.azkerash.createbioships.core.vision;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class VisionMaterialTests {
  private static VisionRaycaster.Ray cast(
      GameTestHelper h, Vec3 from, double budget, boolean aquatic) {
    return VisionBudgetRay.cast(
        h.getLevel(), from, new Vec3(1, 0, 0), budget, budget, aquatic, null);
  }

  @GameTest(template = "test_empty", batch = "sable_optical_materials", timeoutTicks = 80)
  public static void stained_glass_on_a_rotated_sable_object_spends_world_metres(GameTestHelper h) {
    var level = h.getLevel();
    var base = new BlockPos(9344, 100, 128);
    level.setChunkForced(base.getX() >> 4, 8, true);
    h.runAfterDelay(
        20,
        () -> {
          var glass = base.east(2);
          level.setBlock(glass, Blocks.BLUE_STAINED_GLASS.defaultBlockState(), Block.UPDATE_ALL);
          var obstacle =
              dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  glass,
                  java.util.List.of(glass),
                  new dev.ryanhcode.sable.companion.math.BoundingBox3i(glass, glass));
          obstacle.updateBoundingBox();
          var from = Vec3.atCenterOf(base);
          var straight = cast(h, from, 8, false);
          h.assertTrue(
              !straight.blocked() && Math.abs(straight.to().distanceTo(from) - 7) < 1e-4,
              "Foreign Sable glass blocked sight or ignored the range cost");
          obstacle.logicalPose().orientation().rotateY(Math.PI / 4);
          obstacle.updateBoundingBox();
          var turned = cast(h, from, 8, true);
          h.assertTrue(
              !turned.blocked()
                  && Math.abs(turned.to().distanceTo(from) - (8 - Math.sqrt(2))) < 1e-4,
              "Rotated glass charged plot distance instead of physical world distance: " + turned);
          level.setBlock(
              obstacle.getPlot().getCenterBlock(),
              Blocks.STONE.defaultBlockState(),
              Block.UPDATE_ALL);
          var opaque = cast(h, from, 8, true);
          h.assertTrue(
              opaque.blocked() && obstacle.getUniqueId().equals(opaque.hitBody()),
              "Foreign opaque Sable geometry failed to block the optical ray");
          h.succeed();
        });
  }

  @GameTest(template = "test_empty", batch = "optical_material_budget", timeoutTicks = 80)
  public static void water_glass_and_solids_spend_physical_path_budgets(GameTestHelper h) {
    var level = h.getLevel();
    var base = new BlockPos(9216, 100, 128);
    for (int x = 575; x <= 577; x++) {
      level.setChunkForced(x, 8, true);
    }
    h.runAfterDelay(
        20,
        () -> {
          for (int x = 0; x <= 20; x++) {
            level.setBlock(base.east(x), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
          }
          var from = Vec3.atCenterOf(base);
          level.setBlock(base.east(2), Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
          h.assertTrue(
              Math.abs(cast(h, from, 8, false).to().distanceTo(from) - 8) < 1e-5,
              "Clear glass consumed extra range or blocked sight");
          for (var color : DyeColor.values()) {
            for (String suffix : new String[] {"_stained_glass", "_stained_glass_pane"}) {
              var block =
                  BuiltInRegistries.BLOCK.get(
                      ResourceLocation.withDefaultNamespace(color.getName() + suffix));
              level.setBlock(base.east(2), block.defaultBlockState(), Block.UPDATE_CLIENTS);
              h.assertTrue(
                  Math.abs(cast(h, from, 8, false).to().distanceTo(from) - 7) < 1e-5
                      && Math.abs(cast(h, from, 8, true).to().distanceTo(from) - 7) < 1e-5,
                  "Colored glass did not spend 2 optical metres per metre: " + color + suffix);
            }
          }
          level.setBlock(base.east(2), Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
          for (int x = 4; x <= 7; x++) {
            level.setBlock(base.east(x), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
          }
          var land = cast(h, from, 12, false);
          var aquatic = cast(h, from, 12, true);
          h.assertTrue(
              Math.abs(land.to().distanceTo(from) - (3.5 + 8.5 / 3)) < 1e-5,
              "Land optics did not pay 3x only inside the water voxels");
          h.assertTrue(
              Math.abs(aquatic.to().distanceTo(from) - 12) < 1e-5,
              "Aquatic optics lost underwater range");
          level.setBlock(base.east(10), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
          var solid = cast(h, from, 30, true);
          h.assertTrue(
              solid.blocked() && Math.abs(solid.to().x - base.getX() - 10) < 1e-5,
              "Opaque geometry did not stop the ray immediately");
          h.succeed();
        });
  }
}
