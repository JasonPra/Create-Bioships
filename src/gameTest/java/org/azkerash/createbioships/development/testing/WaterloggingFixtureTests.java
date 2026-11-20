package org.azkerash.createbioships.development.testing;

import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class WaterloggingFixtureTests {
  @GameTest(template = "test_empty", timeoutTicks = 180, batch = "static_waterlogging_tank")
  public static void shared_tank_covers_all_placeable_biomancy_and_bioship_blocks(
      GameTestHelper h) {
    var l = h.getLevel();
    WaterloggingFixture.install(l);
    var list = WaterloggingFixture.catalog();
    var base = WaterloggingFixture.base(l);
    h.assertTrue(
        list.stream()
                .anyMatch(
                    b ->
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK
                            .getKey(b)
                            .getNamespace()
                            .equals("biomancy"))
            && list.contains(org.azkerash.createbioships.components.ComponentRegistry.BRAIN.get()),
        "Catalog omitted mod families");
    h.assertTrue(
        l.getBlockState(base.offset(-21, 3, -2)).is(Blocks.WATER),
        "Tank is not filled for swimming");
    h.assertTrue(
        l.getBlockState(base.above(8)).is(DevelopmentTestBlocks.GLASS.get())
            && l.getBlockState(base.below()).is(Blocks.BEDROCK),
        "Protected roof/floor missing");
    h.runAfterDelay(
        40,
        () -> {
          for (int i = 0; i < list.size(); i++) {
            if (net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(list.get(i))
                    .getNamespace()
                    .equals("create_bioships")
                && list.get(i).defaultBlockState().hasProperty(BlockStateProperties.WATERLOGGED)) {
              h.assertTrue(
                  l.getBlockState(WaterloggingFixture.sample(l, i)).is(list.get(i)),
                  "Our waterloggable sample washed away: " + list.get(i));
            }
          }
          System.out.println(
              "WATERLOGGING_TANK_PASS samples=" + list.size() + " static=true swimming=true");
          h.succeed();
        });
  }
}
