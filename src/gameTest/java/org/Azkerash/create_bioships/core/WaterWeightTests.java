package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.legs.SubLevelLegs;
import org.Azkerash.create_bioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class WaterWeightTests {
  @GameTest(template = "test_empty", timeoutTicks = 160, batch = "water_weight")
  public static void tissue_sinks_slowly_and_armor_increases_submerged_weight(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var base = new BlockPos(2176, 80, 128);
    for (int x = 135; x <= 137; x++) {
      for (int z = 7; z <= 9; z++) {
        level.setChunkForced(x, z, true);
      }
    }
    for (int x = -8; x <= 8; x++) {
      for (int z = -8; z <= 8; z++) {
        for (int y = 0; y <= 16; y++) {
          level.setBlock(
              base.offset(x, y, z),
              (y == 0 || Math.abs(x) == 8 || Math.abs(z) == 8 ? Blocks.BEDROCK : Blocks.WATER)
                  .defaultBlockState(),
              Block.UPDATE_CLIENTS);
        }
      }
    }
    helper.runAfterDelay(
        10,
        () -> {
          var bodies = new ArrayList<dev.ryanhcode.sable.sublevel.ServerSubLevel>();
          var starts = new ArrayList<Vec3>();
          for (int i = 0; i < 2; i++) {
            var at = base.offset(i * 6 - 3, 12, 0);
            level.setBlock(at, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
            var material =
                i == 0
                    ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                        net.minecraft.resources.ResourceLocation.parse("biomancy:packed_flesh"))
                    : ComponentRegistry.CHITIN_ARMOR.get();
            level.setBlock(at.east(), material.defaultBlockState(), Block.UPDATE_ALL);
            var body =
                SubLevelAssemblyHelper.assembleBlocks(
                    level, at, List.of(at, at.east()), new BoundingBox3i(at, at.east()));
            CoreAssemblyRules.mark(level, body.getPlot().getCenterBlock());
            DevelopmentLifeSupport.pause(body);
            SubLevelLegs.request(body);
            level.setBlock(at, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(at.east(), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
            bodies.add(body);
            starts.add(
                body.logicalPose()
                    .transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock())));
          }
          helper.runAfterDelay(
              65,
              () -> {
                double tissue =
                    bodies
                            .get(0)
                            .logicalPose()
                            .transformPosition(
                                Vec3.atCenterOf(bodies.get(0).getPlot().getCenterBlock()))
                            .y
                        - starts.get(0).y;
                double armor =
                    bodies
                            .get(1)
                            .logicalPose()
                            .transformPosition(
                                Vec3.atCenterOf(bodies.get(1).getPlot().getCenterBlock()))
                            .y
                        - starts.get(1).y;
                System.out.println(
                    "WATER_WEIGHT tissue="
                        + tissue
                        + " armor="
                        + armor
                        + " gravity="
                        + dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData
                            .getGravity(level)
                        + " masses="
                        + bodies.get(0).getMassTracker().getMass()
                        + ","
                        + bodies.get(1).getMassTracker().getMass()
                        + " velocity="
                        + dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(bodies.get(0))
                            .getLinearVelocity());
                helper.assertTrue(
                    tissue < -.02 && tissue > -1.8,
                    "Tissue floated or sank too quickly: " + tissue);
                helper.assertTrue(
                    armor < tissue - .1 && armor > -5,
                    "Armor did not add moderate submerged weight: " + armor);
                helper.succeed();
              });
        });
  }
}
