package org.Azkerash.create_bioships.development.testing;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class LegChamberTests {
  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "large_leg_chambers")
  public static void spacious_shells_preserve_subjects_remove_old_roofs_and_keep_doors(
      GameTestHelper h) {
    var l = h.getLevel();
    LocomotionFixture.reset(l);
    ModularLimbFixture.reset(l);
    for (var base : java.util.List.of(LocomotionFixture.base(l), ModularLimbFixture.base(l))) {
      h.assertTrue(
          l.getBlockState(base.offset(18, 10, 10)).is(DevelopmentTestBlocks.GLASS.get())
              && l.getBlockState(base.above(24)).is(DevelopmentTestBlocks.GLASS.get()),
          "Chamber lacks wider walls or taller roof");
      h.assertTrue(
          l.getBlockState(base.offset(17, -1, 39)).is(Blocks.BEDROCK),
          "Expanded chamber floor is not indestructible");
    }
    LocomotionFixture.start(l);
    var body = LocomotionFixture.body(l);
    var id = body.getUniqueId();
    var tag = body.getUserDataTag().copy();
    var base = LocomotionFixture.base(l);
    l.setBlock(base.offset(10, 3, 6), DevelopmentTestBlocks.GLASS.get().defaultBlockState(), 2);
    l.setBlock(base.offset(1, 7, 6), DevelopmentTestBlocks.GLASS.get().defaultBlockState(), 2);
    DevelopmentLegChambers.build(l, base, false, 7);
    LocomotionFixture.installDoors(l);
    h.assertTrue(
        !body.isRemoved()
            && LocomotionFixture.body(l).getUniqueId().equals(id)
            && tag.equals(body.getUserDataTag()),
        "Chamber upgrade reset creature data");
    h.assertTrue(
        l.getBlockState(base.offset(10, 3, 6)).isAir()
            && l.getBlockState(base.offset(1, 7, 6)).isAir(),
        "Old walls or roof remained inside new chamber");
    h.assertTrue(
        l.getBlockState(base.offset(0, 0, -8)).is(DevelopmentTestBlocks.GLASS_DOOR.get()),
        "Upgrade lost access doors");
    LocomotionFixture.reset(l);
    ModularLimbFixture.reset(l);
    h.succeed();
  }
}
