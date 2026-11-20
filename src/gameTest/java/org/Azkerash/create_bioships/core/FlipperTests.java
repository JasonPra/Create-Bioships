package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.flipper.FlipperBlockEntity;
import org.Azkerash.create_bioships.core.brain.BrainMotorControls;
import org.Azkerash.create_bioships.core.flippers.SubLevelFlippers;
import org.Azkerash.create_bioships.core.locomotion.SubLevelLocomotion;
import org.Azkerash.create_bioships.core.pathfinding.SubLevelPathfinder;
import org.Azkerash.create_bioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class FlipperTests {
  @GameTest(template = "test_empty", batch = "flipper_swimming", timeoutTicks = 300)
  public static void native_flippers_hold_depth_paddle_and_obey_water_motor_requests(
      GameTestHelper h) {
    var l = h.getLevel();
    var origin = new BlockPos(7200, 80, 128);
    for (int cx = 449; cx <= 451; cx++) {
      for (int cz = 7; cz <= 9; cz++) {
        l.setChunkForced(cx, cz, true);
      }
    }
    for (int x = -8; x <= 8; x++) {
      for (int z = -8; z <= 8; z++) {
        for (int y = -1; y <= 12; y++) {
          l.setBlock(
              origin.offset(x, y, z),
              (y == -1 || Math.abs(x) == 8 || Math.abs(z) == 8 ? Blocks.GLASS : Blocks.WATER)
                  .defaultBlockState(),
              2);
        }
      }
    }
    h.runAfterDelay(
        20,
        () -> {
          var core = origin.above(6);
          var cells = new ArrayList<BlockPos>();
          l.setBlock(core, ComponentRegistry.CORE.get().defaultBlockState(), 3);
          cells.add(core);
          l.setBlock(core.above(), ComponentRegistry.BRAIN.get().defaultBlockState(), 3);
          cells.add(core.above());
          for (var face : List.of(Direction.EAST, Direction.WEST)) {
            var support = core.relative(face);
            var fin = core.relative(face, 2);
            l.setBlock(support, ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(), 3);
            cells.add(support);
            l.setBlock(
                fin,
                ComponentRegistry.FLIPPER
                    .get()
                    .defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, face)
                    .setValue(BlockStateProperties.WATERLOGGED, true),
                3);
            cells.add(fin);
          }
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  l, core, cells, new BoundingBox3i(core.offset(-2, 0, 0), core.offset(2, 1, 0)));
          var p = body.getPlot().getCenterBlock();
          CoreAssemblyRules.mark(l, p);
          DevelopmentLifeSupport.pause(body);
          SubLevelFlippers.request(body);
          // Restore the volume removed by assembly, so the body is truly submerged in world space.
          for (var cell : cells) {
            l.setBlock(cell, Blocks.WATER.defaultBlockState(), 2);
          }
          var start = SubLevelPathfinder.position(body);
          h.runAfterDelay(
              60,
              () -> {
                var at = SubLevelPathfinder.position(body);
                System.out.println("FLIPPER_IDLE start=" + start + " now=" + at);
                h.assertTrue(
                    SubLevelFlippers.supporting(body),
                    "Wet brain-controlled flippers failed to support");
                h.assertTrue(Math.abs(at.y - start.y) < 1.0, "Idle flippers failed to hold depth");
                h.assertTrue(
                    SubLevelLocomotion.get(body).hasFins()
                        && !SubLevelLocomotion.get(body).hasLegs(),
                    "Flipper supplied wrong capabilities");
                h.assertTrue(
                    !BrainMotorControls.move(body, new Vec3(.5, 0, 0), 0, 40),
                    "Aquatic fin accepted land locomotion");
                h.assertTrue(
                    BrainMotorControls.move(
                        body, new Vec3(0, .3, .5), .1, 100, SubLevelPathfinder.Mode.WATER),
                    "Water movement was rejected");
                h.runAfterDelay(
                    65,
                    () -> {
                      var moved = SubLevelPathfinder.position(body);
                      System.out.println("FLIPPER_SWIM start=" + at + " now=" + moved);
                      h.assertTrue(
                          moved.z > at.z + .5 && moved.y > at.y + .15,
                          "Native flippers failed horizontal/vertical swimming");
                      var fin = p.east(2);
                      var be = (FlipperBlockEntity) l.getBlockEntity(fin);
                      h.assertTrue(
                          be != null
                              && !be.points(1).equals(FlipperBlockEntity.rest(be.getBlockState())),
                          "Native rowing failed to animate paddle");
                      for (var v : be.points(1)) {
                        h.assertTrue(
                            Double.isFinite(v.x + v.y + v.z), "IK produced nonfinite paddle");
                      }
                      l.removeBlock(p.east(2), false);
                      l.removeBlock(p.west(2), false);
                      h.runAfterDelay(
                          4,
                          () -> {
                            h.assertTrue(
                                SubLevelFlippers.count(body) == 0
                                    && !SubLevelFlippers.supporting(body)
                                    && !SubLevelLocomotion.get(body).hasFins(),
                                "Removed flippers left propulsion/capability data");
                            SubLevelContainer.getContainer(l)
                                .removeSubLevel(body, SubLevelRemovalReason.REMOVED);
                            h.succeed();
                          });
                    });
              });
        });
  }

  @GameTest(template = "test_empty", batch = "flipper_waterlogging", timeoutTicks = 30)
  public static void flipper_waterlogs_and_keeps_its_whole_collision_envelope(GameTestHelper h) {
    var p = h.absolutePos(new BlockPos(2, 3, 2));
    var l = h.getLevel();
    l.setBlock(p.south(), ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(), 3);
    var s =
        ComponentRegistry.FLIPPER
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.WATERLOGGED, true);
    l.setBlock(p, s, 3);
    h.assertTrue(
        s.canSurvive(l, p) && s.getFluidState().is(net.minecraft.tags.FluidTags.WATER),
        "Waterlogging removed flipper/support");
    h.assertTrue(
        !s.getCollisionShape(l, p).isEmpty() && s.getCollisionShape(l, p).bounds().getXsize() == 1,
        "Paddle collision only covered its tip");
    h.assertTrue(
        ComponentRegistry.FLIPPER.get().asItem() != net.minecraft.world.item.Items.AIR,
        "Flipper missing item");
    h.succeed();
  }
}
