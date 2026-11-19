package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.combat.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.trust.RegenerationTrust;
import org.Azkerash.create_bioships.development.testing.PotionFixture;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class RegenerationTrustTests {
  @GameTest(template = "test_empty", batch = "regeneration_trust", timeoutTicks = 100)
  public static void native_splash_rewards_only_injury_and_persists_shared_cooldown(
      GameTestHelper h) {
    var level = h.getLevel();
    var origin = h.absolutePos(new BlockPos(1, 3, 1));
    level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, origin, List.of(origin), new BoundingBox3i(origin, origin));
    CoreAssemblyRules.mark(level, body.getPlot().getCenterBlock());
    SubLevelData.hungerPaused(body, true);
    SubLevelData.trust(body, 10);
    h.onEachTick(
        () -> {
          if (h.getTick() != 10) return;
          body.forceUpdateGlobalBounds();
          var recipient = CombatAdapter.target(body);
          CombatAdapter.coreTarget(body);
          var player =
              new net.neoforged.neoforge.common.util.FakePlayer(
                  level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CareTest"));
          var point =
              body.logicalPose()
                  .transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock()));
          var potion = new ThrownPotion(level, point.x, point.y, point.z);
          potion.setOwner(player);
          potion.setItem(PotionFixture.potion(MobEffects.REGENERATION));
          splash(potion, recipient, new MobEffectInstance(MobEffects.REGENERATION, 400));
          h.assertTrue(
              SubLevelData.trust(body) == 10 && SubLevelData.regenerationTrustUntil(body) == 0,
              "Full health earned regeneration trust");
          recipient.removeAllEffects();
          SubLevelHealth.damage(body, 2, "test");
          splash(potion, recipient, new MobEffectInstance(MobEffects.REGENERATION, 400));
          h.assertTrue(
              SubLevelData.trust(body) == 10 + RegenerationTrust.REWARD,
              "Player splash did not earn one care reward: effect="
                  + recipient.getEffect(MobEffects.REGENERATION)
                  + " owner="
                  + potion.getOwner()
                  + " health="
                  + SubLevelHealth.status(body));
          long now = level.getGameTime(), until = SubLevelData.regenerationTrustUntil(body);
          h.assertTrue(until == now + 1200, "Short splash did not apply one-minute cooldown");
          var saved = body.getUserDataTag().copy();
          body.setUserDataTag(saved.copy());
          potion.setOwner(
              new net.neoforged.neoforge.common.util.FakePlayer(
                  level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "OtherCareTest")));
          splash(potion, recipient, new MobEffectInstance(MobEffects.REGENERATION, 800, 1));
          potion.setOwner(player);
          h.assertTrue(
              SubLevelData.trust(body) == 10.5
                  && SubLevelData.regenerationTrustUntil(body) == until,
              "Repeated splash bypassed saved cooldown");
          SubLevelData.regenerationTrustUntil(body, now);
          recipient.removeAllEffects();
          splash(potion, recipient, new MobEffectInstance(MobEffects.REGENERATION, 2400, 2));
          h.assertTrue(
              SubLevelData.trust(body) == 11
                  && SubLevelData.regenerationTrustUntil(body) == now + 2400,
              "Long potion duration was not protected");
          SubLevelData.regenerationTrustUntil(body, now);
          splash(potion, recipient, new MobEffectInstance(MobEffects.REGENERATION, 3600, 3));
          h.assertTrue(
              SubLevelData.trust(body) == 11,
              "Refreshing ongoing regeneration farmed trust after cooldown");
          splash(potion, recipient, new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
          h.assertTrue(SubLevelData.trust(body) == 11, "Rejected weaker regeneration earned trust");
          recipient.removeAllEffects();
          var dispenserPotion = new ThrownPotion(level, point.x, point.y, point.z);
          dispenserPotion.setItem(PotionFixture.potion(MobEffects.REGENERATION));
          splash(dispenserPotion, recipient, new MobEffectInstance(MobEffects.REGENERATION, 400));
          h.assertTrue(SubLevelData.trust(body) == 11, "Non-player potion earned trust");
          recipient.removeAllEffects();
          potion.setOwner(player);
          splash(potion, recipient, new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400));
          h.assertTrue(SubLevelData.trust(body) == 11, "Non-regeneration potion earned trust");
          SubLevelHealth.damage(body, 999, "test");
          splash(potion, recipient, new MobEffectInstance(MobEffects.REGENERATION, 400));
          h.assertTrue(SubLevelData.trust(body) == 11, "Dead creature earned regeneration trust");
          h.succeed();
        });
  }

  private static void splash(ThrownPotion potion, LivingEntity hit, MobEffectInstance effect) {
    try {
      var method =
          ThrownPotion.class.getDeclaredMethod("applySplash", Iterable.class, Entity.class);
      method.setAccessible(true);
      method.invoke(potion, List.of(effect), hit);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
