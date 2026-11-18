package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.combat.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.development.testing.PotionFixture;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class SharedPotionEffectsTests {
  @GameTest(template = "test_empty", timeoutTicks = 180, batch = "shared_potion_effects")
  public static void potions_share_one_owner_preserve_data_and_follow_native_health_rules(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(816, 80, 128);
    for (int x = 50; x <= 52; x++) for (int z = 7; z <= 9; z++) level.setChunkForced(x, z, true);
    for (int x = -5; x <= 5; x++)
      for (int z = -5; z <= 5; z++)
        level.setBlock(origin.offset(x, -2, z), Blocks.BEDROCK.defaultBlockState(), 3);
    helper.runAfterDelay(
        10,
        () -> {
          level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
          level.setBlock(origin.east(), ComponentRegistry.LAND_EYE.get().defaultBlockState(), 3);
          level.setBlock(origin.west(), ComponentRegistry.AQUATIC_EYE.get().defaultBlockState(), 3);
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  origin,
                  List.of(origin, origin.east(), origin.west()),
                  new BoundingBox3i(origin.west(), origin.east()));
          var core = body.getPlot().getCenterBlock();
          CoreAssemblyRules.mark(level, core);
          SubLevelData.hunger(body, 3);
          SubLevelData.trust(body, 42);
          SubLevelHealth.damage(body, 10, "test");
          helper.runAfterDelay(
              5,
              () -> {
                body.forceUpdateGlobalBounds();
                var target = CombatAdapter.target(body);
                var weak = CombatAdapter.coreTarget(body);
                CombatAdapter.eyeTarget(body, core.east());
                CombatAdapter.eyeTarget(body, core.west());
                var point = body.logicalPose().transformPosition(Vec3.atCenterOf(core));
                var hit = new BlockHitResult(Vec3.atCenterOf(core), Direction.NORTH, core, false);
                var harming = new ThrownPotion(level, point.x, point.y, point.z);
                harming.setItem(PotionFixture.potion(MobEffects.HARM));
                target.invulnerableTime = 0;
                CombatAdapter.projectile(
                    new net.neoforged.neoforge.event.entity.ProjectileImpactEvent(harming, hit));
                helper.assertTrue(
                    SubLevelHealth.status(body).current() == 14,
                    "Instant damage was multiplied by extra hurtboxes: "
                        + SubLevelHealth.status(body));
                var healing = new ThrownPotion(level, point.x, point.y, point.z);
                healing.setItem(PotionFixture.potion(MobEffects.HEAL));
                CombatAdapter.projectile(
                    new net.neoforged.neoforge.event.entity.ProjectileImpactEvent(healing, hit));
                helper.assertTrue(
                    SubLevelHealth.status(body).current() == 18,
                    "Instant healing was multiplied or missed grid");
                weak.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0));
                helper.assertTrue(
                    target.hasEffect(MobEffects.DAMAGE_RESISTANCE)
                        && weak.hasEffect(MobEffects.DAMAGE_RESISTANCE),
                    "Weak-point potion did not belong to grid");
                double resistanceBefore = SubLevelHealth.status(body).current();
                for (int i = 0; i < 5; i++) target.tick();
                helper.assertTrue(
                    SubLevelHealth.status(body).current() == resistanceBefore,
                    "Resistance alone caused damage");
                target.invulnerableTime = 0;
                target.hurt(level.damageSources().generic(), 5);
                helper.assertTrue(
                    Math.abs(SubLevelHealth.status(body).current() - (resistanceBefore - 4)) < .001,
                    "Resistance I did not reduce incoming damage by 20%");
                for (int i = 0; i < 100; i++) target.tick();
                helper.assertTrue(
                    !target.hasEffect(MobEffects.DAMAGE_RESISTANCE), "Resistance did not expire");
                double expiredBefore = SubLevelHealth.status(body).current();
                target.invulnerableTime = 0;
                target.hurt(level.damageSources().generic(), 5);
                helper.assertTrue(
                    Math.abs(SubLevelHealth.status(body).current() - (expiredBefore - 5)) < .001,
                    "Expired resistance still reduced incoming damage");
                target.removeAllEffects();
                SubLevelHealth.heal(body, 100);
                SubLevelHealth.damage(body, 28, "test");
                target.invulnerableTime = 0;
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 4));
                for (int i = 0; i < 80; i++) target.tick();
                helper.assertTrue(
                    SubLevelHealth.status(body).current() == 1
                        && !SubLevelHealth.status(body).dead(),
                    "Poison did not respect native nonlethal floor");
                target.removeAllEffects();
                SubLevelHealth.heal(body, 9);
                weak.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0));
                double before = SubLevelHealth.status(body).current();
                for (int i = 0; i < 40; i++) weak.tick();
                helper.assertTrue(
                    SubLevelHealth.status(body).current() == before,
                    "Weak hurtbox ticked shared effect a second time");
                for (int i = 0; i < 40; i++) target.tick();
                helper.assertTrue(
                    SubLevelHealth.status(body).current() < before,
                    "Canonical owner did not apply wither");
                target.removeAllEffects();
                target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
                CombatAdapter.saveEffects(body, target);
                target.discard();
                var restored = CombatAdapter.target(body);
                helper.assertTrue(
                    restored.hasEffect(MobEffects.DAMAGE_RESISTANCE),
                    "Recreated adapter lost persisted potion state");
                helper.assertTrue(
                    SubLevelData.hunger(body) == 3 && SubLevelData.trust(body) == 42,
                    "Health/effect updates overwrote shared placeholders");
                restored.removeAllEffects();
                SubLevelHealth.damage(body, SubLevelHealth.status(body).current() - 1, "test");
                restored.invulnerableTime = 0;
                restored.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 4));
                for (int i = 0; i < 20 && !SubLevelHealth.status(body).dead(); i++) restored.tick();
                helper.assertTrue(
                    SubLevelHealth.status(body).dead()
                        && SubLevelHealth.status(body).causeOfDeath().equals("wither"),
                    "Wither did not cause native lethal damage");
                restored.heal(100);
                helper.assertTrue(SubLevelHealth.status(body).dead(), "Healing revived corpse");
                helper.succeed();
              });
        });
  }
}
