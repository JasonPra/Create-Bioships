package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.combat.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class SubLevelCombatTests {
  @GameTest(template = "test_empty", timeoutTicks = 600, batch = "native_combat_integration")
  public static void native_melee_projectile_and_hostile_targeting(GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(128, 80, 128);
    for (int x = -5; x <= 5; x++) {
      for (int z = -5; z <= 5; z++) {
        level.setBlock(origin.offset(x, -2, z), Blocks.BEDROCK.defaultBlockState(), 3);
      }
    }
    for (int x = (origin.getX() - 6) >> 4; x <= (origin.getX() + 6) >> 4; x++) {
      for (int z = (origin.getZ() - 6) >> 4; z <= (origin.getZ() + 6) >> 4; z++) {
        level.setChunkForced(x, z, true);
      }
    }
    helper
        .startSequence()
        .thenWaitUntil(
            () ->
                helper.assertTrue(
                    level.isPositionEntityTicking(origin.north(3))
                        && level.isPositionEntityTicking(origin),
                    "Waiting for forced combat chunks"))
        .thenExecute(() -> exercise(helper, origin));
  }

  private static void exercise(GameTestHelper helper, BlockPos origin) {
    var level = helper.getLevel();
    level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
    level.setBlock(origin.east(), Blocks.STONE.defaultBlockState(), 3);
    level.setBlock(origin.west(), Blocks.STONE.defaultBlockState(), 3);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            origin,
            java.util.List.of(origin, origin.east(), origin.west()),
            new BoundingBox3i(origin.west(), origin.east()));
    helper.assertTrue(body != null, "Assembly failed");
    CoreAssemblyRules.mark(level, body.getPlot().getCenterBlock());
    var target = CombatAdapter.target(body);
    helper.assertTrue(target != null, "No combat adapter");
    var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "combat-test");
    var player =
        new net.minecraft.server.level.ServerPlayer(
            level.getServer(),
            level,
            profile,
            net.minecraft.server.level.ClientInformation.createDefault());
    player.connection =
        new net.neoforged.neoforge.common.util.FakePlayer(level, profile).connection;
    player.setPos(target.position().add(1, 0, 0));
    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    player.attack(target);
    double fist = 30 - SubLevelHealth.status(body).current();
    helper.assertTrue(fist > 0, "Punch did not damage grid");
    target.invulnerableTime = 0;
    player.setItemInHand(
        net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
    // Attribute modifiers are updated by the vanilla living-entity equipment tick.
    player.doTick();
    player.resetAttackStrengthTicker();
    helper.runAfterDelay(
        25,
        () -> {
          target.invulnerableTime = 0;
          double before = SubLevelHealth.status(body).current();
          for (int i = 0; i < 25; i++) {
            player.doTick();
          }
          player.attack(target);
          double sword = before - SubLevelHealth.status(body).current();
          helper.assertTrue(
              sword > fist, "Sword did not use weapon damage: " + sword + " versus fist " + fist);
          target.invulnerableTime = 0;
          var zombie = EntityType.ZOMBIE.create(level);
          zombie.setPos(origin.getX() + .5, origin.getY() - 1, origin.getZ() - 3);
          zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
          level.addFreshEntity(zombie);
          helper.assertTrue(
              zombie.targetSelector.getAvailableGoals().stream()
                  .anyMatch(
                      g ->
                          g.getGoal()
                              instanceof
                              net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<
                                  ?>),
              "No hostile target goal");
          helper.runAfterDelay(
              5,
              () -> {
                target.tick();
                zombie.getSensing().tick();
                for (int i = 0; i < 100 && zombie.getTarget() == null; i++) {
                  zombie.targetSelector.tick();
                }
                helper.assertTrue(
                    zombie.getTarget() instanceof SubLevelCombatTarget acquired
                        && acquired.body() == body,
                    "Hostile AI failed to acquire the sublevel target; canAttack="
                        + zombie.canAttack(target)
                        + " visible="
                        + zombie.getSensing().hasLineOfSight(target)
                        + " nearby="
                        + level
                            .getEntitiesOfClass(
                                SubLevelCombatTarget.class, zombie.getBoundingBox().inflate(35))
                            .size()
                        + " zombie="
                        + zombie.position()
                        + " target="
                        + target.position()
                        + " goals="
                        + zombie.targetSelector.getAvailableGoals().size()
                        + " status="
                        + SubLevelHealth.status(body)
                        + " removed="
                        + target.isRemoved()
                        + " bbox="
                        + target.getBoundingBox()
                        + " bodyRemoved="
                        + body.isRemoved());
                double mobBefore = SubLevelHealth.status(body).current();
                zombie.doHurtTarget(zombie.getTarget());
                helper.assertTrue(
                    SubLevelHealth.status(body).current() < mobBefore,
                    "Zombie attack did not damage grid");
                zombie.discard();
                target.invulnerableTime = 0;
                var arrow =
                    new net.minecraft.world.entity.projectile.Arrow(
                        level, player, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
                var impactPoint =
                    body.logicalPose()
                        .transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock()));
                arrow.setPos(impactPoint.add(0, 0, -3));
                arrow.setDeltaMovement(0, 0, 2);
                double arrowBefore = SubLevelHealth.status(body).current();
                level.addFreshEntity(arrow);
                for (int i = 0; i < 3 && !arrow.isRemoved(); i++) {
                  arrow.tick();
                }
                helper.assertTrue(
                    SubLevelHealth.status(body).current() < arrowBefore,
                    "Arrow did not collide with the real Sable grid");
                helper.assertTrue(
                    !target.canBeHitByProjectile(),
                    "Empty space can hit adapter rather than Sable geometry");
                player.discard();
                helper.succeed();
              });
        });
  }
}
