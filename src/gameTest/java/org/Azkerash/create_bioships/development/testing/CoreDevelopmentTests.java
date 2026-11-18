package org.Azkerash.create_bioships.development.testing;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.core.CoreBlock;
import org.Azkerash.create_bioships.core.*;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class CoreDevelopmentTests {
  @GameTest(template = "test_empty", timeoutTicks = 400, batch = "visible_core_regression")
  public static void visible_glued_hollow_fixture_can_start_and_reset_repeatedly(
      GameTestHelper helper) {
    var layout = new CoreSplitFixture.Layout(helper.absolutePos(new BlockPos(0, 3, 0)));
    CoreTestCommands.reset(helper.getLevel(), layout);
    helper.runAfterDelay(5, () -> cycle(helper, layout, 0));
  }

  private static void cycle(GameTestHelper helper, CoreSplitFixture.Layout layout, int pass) {
    var level = helper.getLevel();
    helper.assertTrue(layout.cores().size() == 49, "Unexpected pyramid size");
    for (var pos : layout.cores())
      helper.assertTrue(
          level.getBlockState(pos).is(ComponentRegistry.CORE.get())
              && level.getBlockState(pos).getValue(CoreBlock.BLINK),
          "Fixture core is not closed and unassembled");
    helper.assertTrue(
        level.getBlockState(layout.tip().above(2)).isAir(), "Pyramid interior is not hollow");
    helper.assertTrue(
        level
            .getBlockState(layout.base().above(CoreSplitFixture.HEIGHT))
            .is(DevelopmentTestBlocks.GLASS.get()),
        "Glass roof missing");
    var glues = level.getEntitiesOfClass(HoneyGlueEntity.class, layout.glue());
    helper.assertTrue(
        glues.size() == 1
            && glues.getFirst().contains(layout.tip())
            && !glues.getFirst().contains(layout.tip().below()),
        "Honey glue includes the pole or misses the pyramid; entities="
            + glues.size()
            + " bounds="
            + glues.stream().map(g -> g.getBoundingBox().toString()).toList());
    var start = (CommandBlockEntity) level.getBlockEntity(layout.startButton().below());
    var reset = (CommandBlockEntity) level.getBlockEntity(layout.resetButton().below());
    helper.assertTrue(
        start.getCommandBlock().getCommand().equals("core_test start")
            && reset.getCommandBlock().getCommand().equals("core_test reset"),
        "Front command controls are missing");
    // Execute the same native command-block logic that the button invokes.
    start.getCommandBlock().performCommand(level);
    helper.assertTrue(
        CoreTestState.get(level).running(), "Start command did not assemble the fixture");
    try {
      helper.assertTrue(CoreTestCommands.start(level) == 0, "A second Start duplicated the test");
    } catch (com.simibubi.create.content.contraptions.AssemblyException error) {
      throw new AssertionError(error);
    }
    helper.runAfterDelay(
        20,
        () -> {
          var count =
              SubLevelContainer.getContainer(level).getAllSubLevels().stream()
                  .filter(
                      b ->
                          !b.isRemoved()
                              && CoreAssemblyRules.isCreature(b)
                              && CoreTestState.get(level).owned.contains(b.getUniqueId()))
                  .count();
          helper.assertTrue(
              count == 49, "Honey-glued fixture did not become 49 independent cores: " + count);
          for (int y = 0; y < 4; y++)
            helper.assertTrue(
                level.getBlockState(layout.base().above(y)).is(Blocks.OAK_LOG),
                "Assembly took the wooden pole");
          reset.getCommandBlock().performCommand(level);
          helper.assertTrue(!CoreTestState.get(level).running(), "Reset command left test running");
          if (pass == 0) helper.runAfterDelay(5, () -> cycle(helper, layout, 1));
          else
            helper.runAfterDelay(
                5,
                () -> {
                  helper.assertTrue(
                      level.getEntitiesOfClass(HoneyGlueEntity.class, layout.glue()).size() == 1,
                      "Reset accumulated glue entities");
                  helper.assertTrue(
                      SubLevelContainer.getContainer(level).getAllSubLevels().stream()
                          .noneMatch(
                              b ->
                                  !b.isRemoved()
                                      && CoreAssemblyRules.isCreature(b)
                                      && layout
                                          .arena()
                                          .inflate(1)
                                          .contains(
                                              b.logicalPose()
                                                  .transformPosition(
                                                      net.minecraft.world.phys.Vec3.atCenterOf(
                                                          b.getPlot().getCenterBlock())))),
                      "Reset left old Sable test objects");
                  helper.succeed();
                });
        });
  }
}
