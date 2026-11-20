package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.combat.*;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class ImpactAdapterTests {
  @GameTest(template = "test_empty", timeoutTicks = 150, batch = "persistent_grid_impacts")
  public static void living_flag_owns_impacts_through_death_without_any_core(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(1088, 80, 128);
    for (int x = 67; x <= 68; x++) {
      for (int z = 7; z <= 8; z++) {
        level.setChunkForced(x, z, true);
      }
    }
    helper.runAfterDelay(
        10,
        () -> {
          level.setBlock(origin, Blocks.STONE.defaultBlockState(), 3);
          level.setBlock(origin.east(), Blocks.STONE.defaultBlockState(), 3);
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  origin,
                  List.of(origin, origin.east()),
                  new BoundingBox3i(origin, origin.east()));
          var cell = body.getPlot().getCenterBlock();
          helper.assertTrue(
              SubLevelImpactAdapter.get(body) == null, "Untagged grid got impact adapter");
          SubLevelData.living(body, true);
          var impacts = SubLevelImpactAdapter.get(body);
          helper.assertTrue(
              impacts != null && !CoreAssemblyRules.isCreature(body),
              "Flag-only grid needs an amoeba core");
          impacts.wound(cell, Direction.EAST);
          helper.assertTrue(
              SubLevelWounds.state(body).active(cell, Direction.EAST, level.getGameTime()),
              "Flagged tissue did not wound");
          SubLevelHealth.damage(body, SubLevelHealth.status(body).current() - 1, "test_setup");
          var incoming =
              new Arrow(
                  level,
                  origin.getX(),
                  origin.getY(),
                  origin.getZ(),
                  net.minecraft.world.item.Items.ARROW.getDefaultInstance(),
                  null);
          incoming.setDeltaMovement(0, 0, 1);
          level.addFreshEntity(incoming);
          SubLevelImpactAdapter.projectile(
              new net.neoforged.neoforge.event.entity.ProjectileImpactEvent(
                  incoming,
                  new BlockHitResult(
                      Vec3.atCenterOf(cell).relative(Direction.NORTH, .5),
                      Direction.NORTH,
                      cell,
                      false)));
          helper.assertTrue(
              SubLevelHealth.status(body).dead()
                  && SubLevelWounds.state(body).active(cell, Direction.NORTH, level.getGameTime()),
              "Lethal arrow lost its wound");
          helper.assertTrue(
              SubLevelImpactAdapter.get(body) == impacts
                  && SubLevelData.isLiving(body)
                  && CombatAdapter.target(body) == null,
              "Death removed persistent impacts or retained living targeting");
          var corpseArrow =
              new Arrow(
                  level,
                  origin.getX(),
                  origin.getY(),
                  origin.getZ(),
                  net.minecraft.world.item.Items.ARROW.getDefaultInstance(),
                  null);
          corpseArrow.setDeltaMovement(0, 0, -1);
          level.addFreshEntity(corpseArrow);
          SubLevelImpactAdapter.projectile(
              new net.neoforged.neoforge.event.entity.ProjectileImpactEvent(
                  corpseArrow,
                  new BlockHitResult(
                      Vec3.atCenterOf(cell).relative(Direction.SOUTH, .5),
                      Direction.SOUTH,
                      cell,
                      false)));
          helper.assertTrue(
              ProjectileAttachment.attached(corpseArrow)
                  && SubLevelWounds.state(body).active(cell, Direction.SOUTH, level.getGameTime())
                  && SubLevelHealth.status(body).current() == 0,
              "Corpse impact failed or revived health");
          var player =
              new net.neoforged.neoforge.common.util.FakePlayer(
                  level,
                  new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "CorpseImpact"));
          var click =
              new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock(
                  player,
                  cell,
                  Direction.WEST,
                  net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock
                      .Action.START);
          CombatAdapter.punch(click);
          helper.assertTrue(
              !click.isCanceled()
                  && SubLevelWounds.state(body).active(cell, Direction.WEST, level.getGameTime()),
              "Corpse melee wound prevented ordinary mining");
          SubLevelData.living(body, false);
          impacts.wound(cell, Direction.UP);
          helper.assertTrue(
              SubLevelImpactAdapter.get(body) == null
                  && !SubLevelWounds.state(body).active(cell, Direction.UP, level.getGameTime()),
              "Explicitly disabled living flag retained impacts");
          helper.runAfterDelay(10, helper::succeed);
        });
  }
}
