package org.azkerash.createbioships.development.testing;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.CreateBioships;
import org.azkerash.createbioships.combat.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.core.*;

@GameTestHolder(CreateBioships.MODID)
@PrefixGameTestTemplate(false)
public final class CoreArrowFixtureTests {
  @GameTest(template = "test_empty", timeoutTicks = 1200, batch = "visible_arrow_fixture")
  public static void three_awake_eyes_and_corpse_keep_native_arrows_and_wounds(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var state = CoreTestState.get(level);
    state.setArrowOrigin(new BlockPos(224, 80, 128));
    CoreArrowFixture.reset(level);
    helper
        .startSequence()
        .thenWaitUntil(
            () ->
                helper.assertTrue(
                    CoreArrowFixture.dispensers(level).stream()
                        .allMatch(level::isPositionEntityTicking),
                    "Waiting for dispenser chunks"))
        .thenExecute(
            () -> {
              helper.assertTrue(CoreArrowFixture.start(level) == 1, "Fixture failed to start");
              helper.assertTrue(
                  CoreArrowFixture.dispensers(level).stream()
                      .noneMatch(
                          p ->
                              level.getBlockState(p).is(Blocks.DISPENSER)
                                  || level.getBlockState(p.north()).is(Blocks.REDSTONE_BLOCK)),
                  "Shooters exist before 30-second observation");
              helper.runAfterDelay(
                  599,
                  () ->
                      helper.assertTrue(
                          CoreArrowFixture.dispensers(level).stream()
                              .noneMatch(
                                  p ->
                                      level.getBlockState(p).is(Blocks.DISPENSER)
                                          || level
                                              .getBlockState(p.north())
                                              .is(Blocks.REDSTONE_BLOCK)),
                          "Shooters spawned before full 30 seconds"));
              helper.assertTrue(
                  CoreArrowFixture.dispensers(level).stream().noneMatch(level::hasNeighborSignal),
                  "Dispensers fired before wake-up");
            })
        .thenWaitUntil(
            () ->
                helper.assertTrue(
                    state.getArrowPhase() == 3,
                    "Waiting for living/corpse rounds: " + state.getArrowResult()))
        .thenExecute(
            () -> {
              var body =
                  (ServerSubLevel)
                      SubLevelContainer.getContainer(level).getSubLevel(state.getArrowBody());
              helper.assertTrue(
                  body != null && SubLevelHealth.status(body).dead(), "Corpse round did not run");
              helper.assertTrue(
                  SubLevelImpactAdapter.get(body) != null && CombatAdapter.target(body) == null,
                  "Persistent impacts depended on a living mob adapter");
              var arrows =
                  level.getEntitiesOfClass(
                      Arrow.class,
                      new AABB(state.getArrowOrigin()).inflate(12),
                      ProjectileAttachment::attached);
              helper.assertTrue(
                  arrows.size() >= 6,
                  "Expected at least two native arrows per eye, got " + arrows.size());
              for (var cell : CoreArrowFixture.targets(body)) {
                helper.assertTrue(
                    SubLevelWounds.state(body).active(cell, Direction.NORTH, level.getGameTime()),
                    "Eye has no corpse wound: " + cell);
                helper.assertTrue(
                    arrows.stream()
                            .filter(
                                arrow ->
                                    arrow
                                            .getPersistentData()
                                            .getCompound("create_bioships:embedded_projectile")
                                            .getLong("cell")
                                        == cell.asLong())
                            .count()
                        >= 2,
                    "Eye lacks a living and corpse embedded arrow: " + cell);
              }
              CoreArrowFixture.reset(level);
              helper.assertTrue(
                  !state.isArrowRunning() && state.getArrowBody() == null,
                  "Reset retained subject");
              for (var pos : CoreArrowFixture.dispensers(level)) {
                helper.assertTrue(
                    level.getBlockState(pos.north()).is(Blocks.AIR),
                    "Reset retained dispenser power");
              }
              helper.assertTrue(
                  level
                      .getBlockState(CoreArrowFixture.core(level))
                      .is(ComponentRegistry.CORE.get()),
                  "Reset failed to restore sleeping core");
              helper.assertTrue(CoreArrowFixture.start(level) == 1, "Fixture cannot run twice");
              CoreArrowFixture.reset(level);
            })
        .thenExecuteAfter(10, helper::succeed);
  }
}
