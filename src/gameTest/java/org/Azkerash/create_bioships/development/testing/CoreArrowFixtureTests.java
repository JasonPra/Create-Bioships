package org.Azkerash.create_bioships.development.testing;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.combat.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.SubLevelHealth;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class CoreArrowFixtureTests {
  @GameTest(template = "test_empty", timeoutTicks = 180, batch = "visible_arrow_fixture")
  public static void dispenser_fixture_can_start_and_reset(GameTestHelper helper) {
    var level = helper.getLevel();
    var state = CoreTestState.get(level);
    state.arrowOrigin = new BlockPos(224, 80, 128);
    CoreArrowFixture.reset(level);
    helper.assertTrue(
        level.getBlockState(CoreArrowFixture.core(level)).is(ComponentRegistry.CORE.get()),
        "Reset did not restore core");
    helper.runAfterDelay(
        40,
        () -> {
          helper.assertTrue(CoreArrowFixture.start(level) == 1, "Fixture failed to start");
          helper.runAfterDelay(
              25,
              () -> {
                var body =
                    (dev.ryanhcode.sable.sublevel.ServerSubLevel)
                        SubLevelContainer.getContainer(level).getSubLevel(state.arrowBody);
                helper.assertTrue(body != null, "Fixture lost assembled grid");
                var arrows =
                    level.getEntitiesOfClass(
                        Arrow.class,
                        new AABB(state.arrowOrigin).inflate(10),
                        ProjectileAttachment::attached);
                helper.assertTrue(
                    arrows.size() == 1,
                    "Native dispenser did not leave one embedded arrow: "
                        + arrows.size()
                        + " all="
                        + level
                            .getEntitiesOfClass(
                                Arrow.class, new AABB(state.arrowOrigin).inflate(20))
                            .stream()
                            .map(a -> a.position() + ":" + ProjectileAttachment.attached(a))
                            .toList()
                        + " core="
                        + body.logicalPose()
                            .transformPosition(
                                net.minecraft.world.phys.Vec3.atCenterOf(
                                    body.getPlot().getCenterBlock()))
                        + " signal="
                        + level.hasNeighborSignal(CoreArrowFixture.dispenser(level))
                        + " state="
                        + level.getBlockState(CoreArrowFixture.dispenser(level))
                        + " inventory="
                        + ((net.minecraft.world.level.block.entity.DispenserBlockEntity)
                                level.getBlockEntity(CoreArrowFixture.dispenser(level)))
                            .getItem(0));
                var core = body.getPlot().getCenterBlock();
                helper.assertTrue(
                    SubLevelWounds.state(body).active(core, Direction.NORTH, level.getGameTime()),
                    "Dispenser failed to wound struck core face");
                helper.assertTrue(
                    SubLevelHealth.status(body).current() < SubLevelHealth.status(body).maximum(),
                    "Dispenser caused no health damage");
                CoreArrowFixture.reset(level);
                helper.assertTrue(
                    !state.arrowRunning && state.arrowBody == null, "Reset retained running state");
                helper.assertTrue(
                    level.getBlockState(CoreArrowFixture.dispenser(level).north()).is(Blocks.AIR),
                    "Reset retained dispenser power");
                helper.assertTrue(
                    level
                        .getBlockState(CoreArrowFixture.core(level))
                        .is(ComponentRegistry.CORE.get()),
                    "Reset failed to restore unassembled fixture");
                helper.assertTrue(CoreArrowFixture.start(level) == 1, "Fixture cannot run twice");
                CoreArrowFixture.reset(level);
                helper.succeed();
              });
        });
  }
}
