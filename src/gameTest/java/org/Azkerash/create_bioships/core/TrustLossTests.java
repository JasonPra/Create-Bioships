package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.combat.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;
import org.Azkerash.create_bioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class TrustLossTests {
  private static ServerSubLevel body(GameTestHelper h, BlockPos origin) {
    var level = h.getLevel();
    for (int cx = (origin.getX() - 8) >> 4; cx <= (origin.getX() + 8) >> 4; cx++) {
      for (int cz = 7; cz <= 9; cz++) {
        level.setChunkForced(cx, cz, true);
      }
    }
    for (int x = -6; x <= 6; x++) {
      for (int z = -6; z <= 6; z++) {
        level.setBlock(
            origin.offset(x, -2, z), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
      }
    }
    level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        origin.east(), ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(), Block.UPDATE_ALL);
    var result =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            origin,
            List.of(origin, origin.east()),
            new BoundingBox3i(origin, origin.east()));
    CoreAssemblyRules.mark(level, result.getPlot().getCenterBlock());
    DevelopmentLifeSupport.pause(result);
    return result;
  }

  private static void near(GameTestHelper h, double actual, double expected, String message) {
    h.assertTrue(
        Math.abs(actual - expected) < .00001, message + ": " + actual + " instead of " + expected);
  }

  private static void hit(
      SubLevelCombatTarget target,
      net.minecraft.world.damagesource.DamageSource source,
      float damage,
      BlockPos cell) {
    target.invulnerableTime = 0;
    target.impact(cell, Direction.UP);
    target.hurt(source, damage);
  }

  @GameTest(template = "test_empty", timeoutTicks = 760, batch = "trust_owner_grace")
  public static void owner_hits_escalate_persist_and_expire_with_no_wrench_penalty(
      GameTestHelper h) {
    var level = h.getLevel();
    var root = body(h, new BlockPos(3584, 82, 128));
    var cell = root.getPlot().getCenterBlock();
    var owner =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "TrustOwner"));
    var stranger =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "TrustOther"));
    SubLevelPermissions.assignIfUnowned(root, owner.getUUID());
    SubLevelData.trust(root, 90);
    var target = CombatAdapter.target(root);
    hit(target, level.damageSources().playerAttack(stranger), 2, cell);
    hit(
        target,
        level.damageSources().mobAttack(net.minecraft.world.entity.EntityType.ZOMBIE.create(level)),
        2,
        cell);
    near(h, SubLevelData.trust(root), 90, "Other attackers reduced owner trust");
    h.assertTrue(
        SubLevelData.ownerHits(root).isEmpty(), "Other attackers consumed accidental-hit grace");
    double[] expected = {89.75, 89, 87.25, 84, 79};
    for (int i = 0; i < expected.length; i++) {
      hit(target, level.damageSources().playerAttack(owner), 2, cell);
      near(h, SubLevelData.trust(root), expected[i], "Owner damage/grace scaling incorrect");
    }
    var saved = root.getUserDataTag().copy();
    root.setUserDataTag(saved.copy());
    h.assertTrue(
        SubLevelData.ownerHits(root).size() == 5
            && SubLevelData.ownerHits(root).getFirst().damage() == 2,
        "Saved attack history was lost");
    // Armor-only attacks record frequency but have no health-based trust cost.
    for (int i = 0; i < 5; i++) {
      hit(target, level.damageSources().playerAttack(owner), 1, cell.east());
    }
    near(h, SubLevelData.trust(root), 79, "Armor-only hits cost health-based trust");
    h.assertTrue(
        SubLevelData.ownerHits(root).size() == SubLevelData.TRUST_ATTACK_LOG_LIMIT,
        "Attack history was not bounded");
    SubLevelHealth.heal(root, 100);
    SubLevelHealth.withoutRemovalDamage(
        root,
        () -> {
          level.removeBlock(cell.east(), false);
          return true;
        });
    near(h, SubLevelData.trust(root), 79, "Component removal cost attack trust");
    // Keep the root alive after removing health capacity; grace must survive actual world time.
    SubLevelHealth.heal(root, 100);
    h.runAfterDelay(
        601,
        () -> {
          var current = CombatAdapter.target(root);
          hit(current, level.damageSources().playerAttack(owner), 1, cell);
          near(
              h,
              SubLevelData.trust(root),
              78.75,
              "Quiet period did not restore accidental-hit grace");
          h.assertTrue(
              SubLevelData.ownerHits(root).size() == SubLevelData.TRUST_ATTACK_LOG_LIMIT,
              "The bounded audit log lost older hits");
          SubLevelData.trust(root, .01);
          hit(current, level.damageSources().playerAttack(owner), 1000, cell);
          near(h, SubLevelData.trust(root), 0, "Lethal owner damage failed to clamp trust");
          h.succeed();
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 80, batch = "trust_projectile_limb")
  public static void projectiles_on_linked_limbs_use_owner_grace_and_actual_lethal_damage(
      GameTestHelper h) {
    var level = h.getLevel();
    var origin = new BlockPos(3712, 82, 128);
    var root = body(h, origin);
    var limbSource = origin.east(4);
    level.setBlock(limbSource, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    var limb =
        SubLevelAssemblyHelper.assembleBlocks(
            level, limbSource, List.of(limbSource), new BoundingBox3i(limbSource, limbSource));
    SubLevelCreatureParts.attach(root, limb);
    var owner =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "TrustArcher"));
    SubLevelPermissions.assignIfUnowned(root, owner.getUUID());
    SubLevelData.trust(root, 100);
    var arrow =
        new net.minecraft.world.entity.projectile.Arrow(
            level,
            owner,
            net.minecraft.world.item.Items.ARROW.getDefaultInstance(),
            net.minecraft.world.item.Items.BOW.getDefaultInstance());
    var source = level.damageSources().arrow(arrow, owner);
    var target = CombatAdapter.target(limb);
    hit(target, source, 6, limb.getPlot().getCenterBlock());
    near(
        h,
        SubLevelData.trust(limb),
        99.5,
        "Owner projectile on a limb did not use shared trust/grace");
    hit(target, source, 1000, limb.getPlot().getCenterBlock());
    h.assertTrue(SubLevelHealth.status(root).dead(), "Lethal projectile failed to kill root");
    near(
        h,
        SubLevelData.trust(root),
        93.5,
        "Overkill used requested damage instead of health actually lost");
    h.assertTrue(
        SubLevelData.ownerHits(limb).size() == 2
            && SubLevelData.ownerHits(root).getLast().damage() == 24,
        "Linked hit history or lethal health delta was incorrect");
    h.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 80, batch = "trust_starvation")
  public static void starvation_penalty_repeats_escalates_and_respects_pause_and_death(
      GameTestHelper h) {
    var level = h.getLevel();
    var root = body(h, new BlockPos(3648, 82, 128));
    SubLevelData.trust(root, 100);
    SubLevelData.hungerState(root, new SubLevelData.Hunger(0, false, 0, 0));
    SubLevelHunger.advance(root, 80);
    near(h, SubLevelHealth.status(root).current(), 19, "First starvation damage incorrect");
    near(h, SubLevelData.trust(root), 98.9, "First starvation penalty incorrect");
    CombatAdapter.target(root).invulnerableTime = 0;
    SubLevelHunger.advance(root, 80);
    near(h, SubLevelData.trust(root), 97.7, "Recurring starvation loss did not escalate");
    h.assertTrue(SubLevelData.ownerHits(root).isEmpty(), "Starvation consumed owner-attack grace");
    DevelopmentLifeSupport.pause(root);
    SubLevelHunger.advance(root, 10000);
    near(h, SubLevelData.trust(root), 97.7, "Paused hunger reduced trust");
    SubLevelHealth.damage(root, 17, "setup");
    near(h, SubLevelData.trust(root), 97.7, "Unattributed damage reduced trust");
    SubLevelData.hungerPaused(root, false);
    CombatAdapter.target(root).invulnerableTime = 0;
    SubLevelHunger.advance(root, 80);
    h.assertTrue(SubLevelHealth.status(root).dead(), "Starvation did not kill the fixture");
    near(h, SubLevelData.trust(root), 94.7, "Near-death starvation multiplier incorrect");
    SubLevelHunger.advance(root, 10000);
    near(h, SubLevelData.trust(root), 94.7, "A corpse kept losing trust");
    h.succeed();
  }
}
