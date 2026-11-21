package org.azkerash.createbioships.development.testing;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.legs.CompactLegSocketBlock;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class HuntsmanFixtureTests {
  @GameTest(template = "test_empty")
  public static void giant_subject_has_eight_supported_mirrored_legs_and_eyes(GameTestHelper h) {
    var cells = HuntsmanFixture.anatomy(BlockPos.ZERO);
    long legs =
        cells.values().stream()
            .filter(state -> state.is(ComponentRegistry.ARACHNID_LEG.get()))
            .count();
    long eyes =
        cells.values().stream().filter(state -> state.is(ComponentRegistry.LAND_EYE.get())).count();
    h.assertTrue(legs == 8 && eyes == 8, "Huntsman anatomy lost legs or eyes");
    for (var entry : cells.entrySet()) {
      var state = entry.getValue();
      if (state.is(ComponentRegistry.ARACHNID_LEG.get())) {
        var support =
            entry
                .getKey()
                .relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
        h.assertTrue(
            cells.containsKey(support)
                && state.getValue(CompactLegSocketBlock.HEIGHT) == HuntsmanFixture.LEG_HEIGHT,
            "Extended socket has no body support");
      }
      if (state.is(ComponentRegistry.LAND_EYE.get())) {
        h.assertTrue(
            cells.containsKey(entry.getKey().south()), "Eye has no supporting front plate");
      }
    }
    var occupied = h.absolutePos(new BlockPos(1, 2, 1));
    h.getLevel().setBlock(occupied, ComponentRegistry.CORE.get().defaultBlockState(), 3);
    boolean refused = false;
    try {
      HuntsmanFixture.spawn(h.getLevel(), occupied);
    } catch (IllegalArgumentException expected) {
      refused = true;
    }
    h.assertTrue(
        refused && h.getLevel().getBlockState(occupied).is(ComponentRegistry.CORE.get()),
        "Fixture overwrote an occupied spawn");
    h.succeed();
  }
}
