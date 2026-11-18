package org.Azkerash.create_bioships.development.testing;

import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class MembraneFixtureTests {
  @GameTest(template = "test_empty", batch = "visible_membrane_fixture", timeoutTicks = 160)
  public static void membrane_fixture_can_start_reset_and_preserve_owner_access(
      GameTestHelper helper) {
    var level = helper.getLevel();
    MembraneFixture.reset(level);
    helper.assertTrue(
        MembraneFixture.start(level) == 1 && MembraneFixture.start(level) == 0,
        "START failed or duplicated subject");
    var body = MembraneFixture.body(level);
    var core = body.getPlot().getCenterBlock();
    var actor =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "FixtureOwner"));
    SubLevelPermissions.assignIfUnowned(body, actor.getUUID());
    var membrane = core.below(4);
    helper.assertTrue(
        level.getBlockState(core.offset(-2, -3, -1)).is(ComponentRegistry.NERVE_PANEL.get()),
        "Fixture omitted its panel");
    helper.assertTrue(
        level
            .getBlockState(membrane)
            .getCollisionShape(level, membrane, CollisionContext.of(actor))
            .isEmpty(),
        "Fixture membrane blocked owner");
    MembraneFixture.reset(level);
    helper.assertTrue(
        body.isRemoved() && MembraneFixture.body(level) == null, "RESET retained old subject");
    helper.assertTrue(MembraneFixture.start(level) == 1, "RESET prevented another START");
    MembraneFixture.reset(level);
    helper.succeed();
  }
}
