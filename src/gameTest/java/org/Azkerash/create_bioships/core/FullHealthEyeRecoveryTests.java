package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.combat.SubLevelWounds;
import org.Azkerash.create_bioships.components.ComponentRegistry;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class FullHealthEyeRecoveryTests {
  @GameTest(template = "test_empty", timeoutTicks = 120, batch = "full_health_eye_recovery")
  public static void full_health_clears_all_eye_types_and_saved_injuries(GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(656, 80, 128);
    level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
    level.setBlock(
        origin.east(),
        ComponentRegistry.LAND_EYE
            .get()
            .defaultBlockState()
            .setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
                Direction.EAST),
        3);
    level.setBlock(
        origin.west(),
        ComponentRegistry.AQUATIC_EYE
            .get()
            .defaultBlockState()
            .setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
                Direction.WEST),
        3);
    level.setBlock(origin.north(), Blocks.STONE.defaultBlockState(), 3);
    level.setBlock(origin.south(), Blocks.STONE.defaultBlockState(), 3);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            origin,
            List.of(origin, origin.east(), origin.west(), origin.north(), origin.south()),
            new BoundingBox3i(origin.west().north(), origin.east().south()));
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, core);
    SubLevelHealth.damage(body, 10, "test");
    long now = level.getGameTime();
    for (var cell : List.of(core, core.east(), core.west(), core.south())) {
      SubLevelWounds.hit(body, cell, Direction.NORTH);
    }
    for (var cell : List.of(core, core.east(), core.west())) {
      helper.assertTrue(
          SubLevelWounds.state(body).active(cell, Direction.NORTH, now),
          "Injured eye cleared before full health");
    }
    SubLevelHealth.withoutRemovalDamage(
        body,
        () -> {
          level.removeBlock(core.north(), false);
          level.setBlock(core.north(), Blocks.STONE.defaultBlockState(), 3);
          return true;
        });
    helper.assertTrue(
        SubLevelHealth.status(body).current() == 40 && SubLevelHealth.status(body).maximum() == 50,
        "Rolled-back wrench operation healed the grid");
    helper.assertTrue(
        SubLevelWounds.state(body).active(core, Direction.NORTH, now),
        "Temporary full capacity healed eyes before a wrench rollback finished");
    SubLevelHealth.heal(body, 3);
    helper.assertTrue(
        SubLevelWounds.state(body).active(core, Direction.NORTH, now),
        "Partial healing cleared the eye injury");
    SubLevelHealth.heal(body, 7);
    var health = SubLevelHealth.status(body);
    helper.assertTrue(
        health.current() == 50 && health.maximum() == 50,
        "Explicit healing did not restore full health");
    for (var cell : List.of(core, core.east(), core.west())) {
      helper.assertTrue(
          !SubLevelWounds.state(body).active(cell, Direction.NORTH, now),
          "Full health did not clear an eye type");
    }
    helper.assertTrue(
        SubLevelWounds.state(body).active(core.south(), Direction.NORTH, now),
        "Eye recovery cleared an unrelated tissue wound");
    var restored =
        SurfaceWoundState.load(body.getUserDataTag().getList("create_bioships:wounds", 10));
    helper.assertTrue(restored.active(now).size() == 1, "Eye recovery was not persisted");
    SubLevelWounds.hit(body, core, Direction.NORTH);
    helper.assertTrue(
        !SubLevelWounds.state(body).active(core, Direction.NORTH, now),
        "Full-health core became injured without damage");
    SubLevelHealth.damage(body, 50, "test");
    SubLevelWounds.hit(body, core, Direction.NORTH);
    helper.assertTrue(
        SubLevelHealth.status(body).dead()
            && SubLevelWounds.state(body).active(core, Direction.NORTH, now),
        "Dead grid was revived or treated as fully healed");
    helper.succeed();
  }
}
