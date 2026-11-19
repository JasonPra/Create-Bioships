package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.combat.SubLevelWounds;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.eye.*;
import org.Azkerash.create_bioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class EyeSupportTests {
  @GameTest(template = "test_empty", timeoutTicks = 280, batch = "solid_eye_support")
  public static void eyes_need_solid_non_eye_support_and_remain_closed_when_injured(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(1152, 80, 128);
    level.setChunkForced(72, 8, true);
    helper.runAfterDelay(
        10,
        () -> {
          var facing = BlockStateProperties.FACING;
          var land =
              ComponentRegistry.LAND_EYE.get().defaultBlockState().setValue(facing, Direction.EAST);
          var aqua =
              ComponentRegistry.AQUATIC_EYE
                  .get()
                  .defaultBlockState()
                  .setValue(facing, Direction.WEST);
          level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
          level.setBlock(origin.east(), land, 3);
          level.setBlock(origin.west(), aqua, 3);
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  origin,
                  java.util.List.of(origin, origin.east(), origin.west()),
                  new BoundingBox3i(origin.west(), origin.east()));
          var core = body.getPlot().getCenterBlock();
          CoreAssemblyRules.mark(level, core);
          DevelopmentLifeSupport.pause(body);
          SubLevelHealth.damage(body, 10, "test");
          for (var cell : java.util.List.of(core.east(), core.west())) {
            SubLevelWounds.hit(body, cell, level.getBlockState(cell).getValue(facing));
            helper.assertTrue(
                level.getBlockState(cell).getValue(EyeBlink.BLINK), "Injured eye stayed open");
          }
          var unsupported = origin.south(5);
          level.setBlock(unsupported, land, 3);
          level.setBlock(unsupported.west(), aqua, 3);
          helper.assertTrue(
              !EyeWoundBehavior.hasSupport(land, level, unsupported),
              "Eye counted another eye as support");
          helper.runAfterDelay(
              140,
              () -> {
                helper.assertTrue(
                    level.getBlockState(unsupported).isAir()
                        && level.getBlockState(unsupported.west()).isAir(),
                    "Unsupported eye pair did not fall apart");
                for (var cell : java.util.List.of(core.east(), core.west()))
                  helper.assertTrue(
                      level.getBlockState(cell).getValue(EyeBlink.BLINK),
                      "Normal blink reopened an injured eye");
                SubLevelWounds.healEyes(body);
                helper.runAfterDelay(
                    25,
                    () -> {
                      for (var cell : java.util.List.of(core.east(), core.west()))
                        helper.assertTrue(
                            !level.getBlockState(cell).getValue(EyeBlink.BLINK),
                            "Healed eye did not reopen");
                      level.removeBlock(core, false);
                      helper.runAfterDelay(
                          5,
                          () -> {
                            helper.assertTrue(
                                level.getBlockState(core.east()).isAir()
                                    && level.getBlockState(core.west()).isAir(),
                                "Assembled eyes survived missing support");
                            helper.runAfterDelay(10, helper::succeed);
                          });
                    });
              });
        });
  }
}
